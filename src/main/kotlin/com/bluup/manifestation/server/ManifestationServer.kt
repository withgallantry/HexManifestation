package com.bluup.manifestation.server

import at.petrak.hexcasting.api.casting.ActionRegistryEntry
import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.casting.iota.IotaType
import at.petrak.hexcasting.api.casting.math.HexDir
import at.petrak.hexcasting.api.casting.math.HexPattern
import at.petrak.hexcasting.common.lib.hex.HexActions
import com.bluup.manifestation.Manifestation
import com.bluup.manifestation.common.ManifestationNetworking
import com.bluup.manifestation.common.menu.MenuPayload
import com.bluup.manifestation.server.action.OpCreateGridMenu
import com.bluup.manifestation.server.action.OpCreateListMenu
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.core.Registry
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand

/**
 * Server-side entrypoint. Runs on both dedicated servers and the integrated
 * server inside a single-player client.
 *
 * Responsibilities:
 *   1. Register the two CREATE_*_MENU patterns in Hex's global action registry.
 *   2. Expose sendMenuTo() for operators to push a MenuPayload to a player's
 *      client via S2C packet.
 *   3. Register the button-click C2S receiver. When a player clicks a button
 *      client-side, they send us a DISPATCH_ACTION_C2S packet with the
 *      button's stored iotas; we hand them to MenuActionDispatcher to feed
 *      through the player's live casting session.
 *
 * Pattern-signature audit confirmed LIST=aqwqa and GRID=aqwqaqwqa are free.
 */
object ManifestationServer : ModInitializer {

    private const val LIST_MENU_SIG = "aqwqa"
    private val LIST_MENU_DIR = HexDir.EAST

    private const val GRID_MENU_SIG = "aqwqaqwqa"
    private val GRID_MENU_DIR = HexDir.EAST

    override fun onInitialize() {
        Manifestation.LOGGER.info("Manifestation server initializing.")

        registerActions()
        registerC2SReceivers()

        Manifestation.LOGGER.info(
            "Manifestation: registered 2 menu-creation patterns and dispatch receiver."
        )
    }

    private fun registerActions() {
        Registry.register(
            HexActions.REGISTRY,
            Manifestation.id("create_list_menu"),
            ActionRegistryEntry(
                HexPattern.fromAngles(LIST_MENU_SIG, LIST_MENU_DIR),
                OpCreateListMenu
            )
        )
        Registry.register(
            HexActions.REGISTRY,
            Manifestation.id("create_grid_menu"),
            ActionRegistryEntry(
                HexPattern.fromAngles(GRID_MENU_SIG, GRID_MENU_DIR),
                OpCreateGridMenu
            )
        )
    }

    /**
     * Wire up the button-click packet. Packet format:
     *   - InteractionHand (enum)
    *   - VarInt: number of input strings
    *   - for each: UTF string
     *   - VarInt: number of iotas
     *   - for each: CompoundTag produced by IotaType.serialize
     */
    private fun registerC2SReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(
            ManifestationNetworking.DISPATCH_ACTION_C2S
        ) { server, player, _, buf, _ ->
            val hand = buf.readEnum(InteractionHand::class.java)
            val inputCount = buf.readVarInt()
            val inputStrings = (0 until inputCount).map { buf.readUtf() }
            val count = buf.readVarInt()
            val tags = (0 until count).map { buf.readNbt() }
            // Execute on the server thread — the packet arrives on the network
            // thread, but CastingVM and setStaffcastImage must run on the main
            // server thread.
            server.execute {
                val world = player.serverLevel()
                val iotas: List<Iota> = tags.mapNotNull { tag ->
                    tag ?: return@mapNotNull null
                    try {
                        IotaType.deserialize(tag, world)
                    } catch (t: Throwable) {
                        Manifestation.LOGGER.warn(
                            "Manifestation dispatch: skipping iota that failed to deserialize",
                            t
                        )
                        null
                    }
                }
                MenuActionDispatcher.dispatch(player, hand, inputStrings, iotas)
            }
        }
    }

    @JvmStatic
    fun sendMenuTo(player: ServerPlayer, payload: MenuPayload) {
        val buf = PacketByteBufs.create()
        payload.write(buf)
        ServerPlayNetworking.send(player, ManifestationNetworking.SHOW_MENU_S2C, buf)
    }
}
