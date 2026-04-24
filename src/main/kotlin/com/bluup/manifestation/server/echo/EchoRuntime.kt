package com.bluup.manifestation.server.echo

import at.petrak.hexcasting.common.blocks.circles.impetuses.BlockEntityRedstoneImpetus
import com.bluup.manifestation.Manifestation
import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.phys.Vec3
import java.util.UUID

object EchoRuntime {
    private const val ECHO_PROFILE_MARKER_KEY = "manifestation_echo"
    private const val ECHO_PROFILE_MARKER_VALUE = "1"

    fun register() {
        // No runtime hooks required for bind-focused echo flow.
    }

    fun summonOrRefresh(
        caster: ServerPlayer,
        owner: UUID,
        slot: Int,
        summonPos: Vec3
    ): EchoStateStore.EchoRecord {
        val server = caster.server
        val store = EchoStateStore.get(server)
        val level = caster.serverLevel()

        val existing = store.get(owner, slot)
        val facing = caster.lookAngle.normalizeOrFallback()

        val record = if (existing == null) {
            EchoStateStore.EchoRecord(
                owner = owner,
                slot = slot,
                dimensionId = level.dimension().location().toString(),
                position = summonPos,
                facing = facing
            )
        } else {
            existing.dimensionId = level.dimension().location().toString()
            existing.position = summonPos
            existing.facing = facing
            existing
        }

        store.put(record)
        Manifestation.LOGGER.info(
            "Echo slot refresh: owner={} slot={} dim={} pos=({}, {}, {})",
            owner,
            slot,
            record.dimensionId,
            "%.2f".format(record.position.x),
            "%.2f".format(record.position.y),
            "%.2f".format(record.position.z)
        )
        return record
    }

    fun bindClericImpetusAt(caster: ServerPlayer, owner: UUID, slot: Int, targetPos: Vec3): Boolean {
        val level = caster.serverLevel()
        val pos = BlockPos.containing(targetPos)
        val be: BlockEntity = level.getBlockEntity(pos) ?: return false
        if (be !is BlockEntityRedstoneImpetus) {
            return false
        }

        val profile = buildEchoBindingProfile(caster, owner, slot)
        be.setPlayer(profile, owner)
        be.sync()
        Manifestation.LOGGER.debug("Echo direct-bound cleric impetus at {} for {}", pos, owner)
        return true
    }

    private fun buildEchoBindingProfile(fallbackActor: ServerPlayer, owner: UUID, slot: Int): GameProfile {
        val source = fallbackActor.server.playerList.getPlayer(owner)?.gameProfile
            ?: if (owner == fallbackActor.uuid) fallbackActor.gameProfile else null
            ?: fallbackActor.server.profileCache?.get(owner)?.orElse(null)

        val baseName = source?.name?.ifBlank { "Player" } ?: "Player"
        val echoName = "${ordinal(slot + 1)} Echo of $baseName"
        val profile = GameProfile(owner, echoName)
        profile.properties.put(ECHO_PROFILE_MARKER_KEY, Property(ECHO_PROFILE_MARKER_KEY, ECHO_PROFILE_MARKER_VALUE))
        return profile
    }

    private fun ordinal(value: Int): String {
        val mod100 = value % 100
        if (mod100 in 11..13) {
            return "${value}th"
        }
        return when (value % 10) {
            1 -> "${value}st"
            2 -> "${value}nd"
            3 -> "${value}rd"
            else -> "${value}th"
        }
    }

    private fun Vec3.normalizeOrFallback(): Vec3 {
        if (this.lengthSqr() <= 1.0e-8) {
            return Vec3(0.0, 0.0, 1.0)
        }
        return this.normalize()
    }
}
