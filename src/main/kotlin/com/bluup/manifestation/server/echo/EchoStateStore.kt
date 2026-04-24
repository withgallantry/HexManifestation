package com.bluup.manifestation.server.echo

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.server.MinecraftServer
import net.minecraft.world.phys.Vec3
import net.minecraft.world.level.saveddata.SavedData
import java.util.UUID

class EchoStateStore : SavedData() {
    data class EchoRecord(
        val owner: UUID,
        val slot: Int,
        var dimensionId: String,
        var position: Vec3,
        var facing: Vec3
    )

    private val echoes: MutableMap<UUID, MutableMap<Int, EchoRecord>> = mutableMapOf()

    fun get(owner: UUID, slot: Int): EchoRecord? = echoes[owner]?.get(slot)

    fun put(record: EchoRecord) {
        val bySlot = echoes.getOrPut(record.owner) { mutableMapOf() }
        bySlot[record.slot] = record
        setDirty()
    }

    fun remove(owner: UUID) {
        echoes.remove(owner)
        setDirty()
    }

    override fun save(tag: CompoundTag): CompoundTag {
        val list = ListTag()
        for ((_, bySlot) in echoes) {
            for ((_, record) in bySlot) {
                val t = CompoundTag()
                t.putUUID("owner", record.owner)
                t.putInt("slot", record.slot)
                t.putString("dim", record.dimensionId)
                t.putDouble("x", record.position.x)
                t.putDouble("y", record.position.y)
                t.putDouble("z", record.position.z)
                t.putDouble("fx", record.facing.x)
                t.putDouble("fy", record.facing.y)
                t.putDouble("fz", record.facing.z)
                list.add(t)
            }
        }
        tag.put("echoes", list)
        return tag
    }

    companion object {
        private const val DATA_NAME = "manifestation_echoes"

        fun get(server: MinecraftServer): EchoStateStore {
            val storage = server.overworld().dataStorage
            return storage.computeIfAbsent(
                ::load,
                ::EchoStateStore,
                DATA_NAME
            )
        }

        private fun load(tag: CompoundTag): EchoStateStore {
            val out = EchoStateStore()
            val list = tag.getList("echoes", Tag.TAG_COMPOUND.toInt())
            for (entry in list) {
                val t = entry as? CompoundTag ?: continue
                if (!t.hasUUID("owner")) {
                    continue
                }
                val owner = t.getUUID("owner")
                val slot = if (t.contains("slot", Tag.TAG_INT.toInt())) t.getInt("slot") else 0
                if (slot !in 0..2) {
                    continue
                }
                val dim = t.getString("dim")
                val pos = Vec3(t.getDouble("x"), t.getDouble("y"), t.getDouble("z"))
                val facing = Vec3(t.getDouble("fx"), t.getDouble("fy"), t.getDouble("fz"))
                val bySlot = out.echoes.getOrPut(owner) { mutableMapOf() }
                bySlot[slot] = EchoRecord(owner, slot, dim, pos, facing)
            }
            return out
        }
    }
}
