package com.bluup.manifestation.server.action

import at.petrak.hexcasting.api.casting.castables.Action
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.eval.OperationResult
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage
import at.petrak.hexcasting.api.casting.eval.vm.SpellContinuation
import at.petrak.hexcasting.api.casting.iota.DoubleIota
import at.petrak.hexcasting.api.casting.iota.Vec3Iota
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidIota
import at.petrak.hexcasting.api.casting.mishaps.MishapNotEnoughArgs
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds
import com.bluup.manifestation.server.echo.EchoRuntime
import com.bluup.manifestation.server.mishap.MishapRequiresCasterWill
import net.minecraft.server.level.ServerPlayer

/**
 * Stack shape on entry (top -> bottom):
 *   slot (0..2) (still not sure if I should zero index this or not)
 *   target position vector
 */
object OpManifestEcho : Action {
    override fun operate(
        env: CastingEnvironment,
        image: CastingImage,
        continuation: SpellContinuation
    ): OperationResult {
        val stack = image.stack.toMutableList()
        if (stack.size < 2) {
            throw MishapNotEnoughArgs(2, stack.size)
        }

        val caster = env.castingEntity as? ServerPlayer ?: throw MishapRequiresCasterWill()

        val slotIota = stack.removeAt(stack.lastIndex)
        val posIota = stack.removeAt(stack.lastIndex)

        val slot = if (slotIota is DoubleIota) {
            val rounded = Math.round(slotIota.double).toInt()
            if (rounded !in 0..2) {
                throw MishapInvalidIota.ofType(slotIota, 0, "slot 0..2")
            }
            rounded
        } else {
            stack.add(posIota)
            stack.add(slotIota)
            throw MishapInvalidIota.ofType(slotIota, 0, "slot number")
        }

        val summonPos = if (posIota is Vec3Iota) {
            env.assertVecInRange(posIota.vec3)
            posIota.vec3
        } else {
            stack.add(posIota)
            stack.add(slotIota)
            throw MishapInvalidIota.ofType(posIota, 1, "vector")
        }

        EchoRuntime.summonOrRefresh(caster, caster.uuid, slot, summonPos)
        if (!EchoRuntime.bindClericImpetusAt(caster, caster.uuid, slot, summonPos)) {
            throw MishapInvalidIota.ofType(posIota, 1, "position of a cleric impetus")
        }

        val image2 = image.withUsedOp().copy(stack = stack)
        return OperationResult(
            image2,
            listOf(),
            continuation,
            HexEvalSounds.NORMAL_EXECUTE
        )
    }
}
