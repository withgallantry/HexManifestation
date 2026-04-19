package com.bluup.manifestation.server.action

import at.petrak.hexcasting.api.casting.castables.Action
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.eval.OperationResult
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage
import at.petrak.hexcasting.api.casting.eval.vm.SpellContinuation
import at.petrak.hexcasting.api.casting.iota.DoubleIota
import at.petrak.hexcasting.api.casting.iota.ListIota
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidIota
import at.petrak.hexcasting.api.casting.mishaps.MishapNotEnoughArgs
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds
import com.bluup.manifestation.server.iota.UiSliderIota
import net.minecraft.network.chat.Component

/**
 * Constructor operator for typed UI slider entries.
 *
 * Stack shape on entry (top -> bottom):
 *   label
 *   [min, max] or [min, max, current]
 */
object OpUiSlider : Action {
    override fun operate(
        env: CastingEnvironment,
        image: CastingImage,
        continuation: SpellContinuation
    ): OperationResult {
        val stack = image.stack.toMutableList()
        if (stack.size < 2) {
            throw MishapNotEnoughArgs(2, stack.size)
        }

        val label = stack.removeAt(stack.lastIndex)
        val rangeIota = stack.removeAt(stack.lastIndex)
        if (rangeIota !is ListIota) {
            stack.add(rangeIota)
            stack.add(label)
            throw MishapInvalidIota.ofType(rangeIota, 1, "list")
        }

        val parts = rangeIota.list.toList()
        if (parts.size != 2 && parts.size != 3) {
            stack.add(rangeIota)
            stack.add(label)
            throw MishapInvalidIota(rangeIota, 1, Component.literal("list [min, max, current?]"))
        }

        val min = (parts[0] as? DoubleIota)?.double ?: run {
            stack.add(rangeIota)
            stack.add(label)
            throw MishapInvalidIota.ofType(parts[0], 1, "double")
        }
        val max = (parts[1] as? DoubleIota)?.double ?: run {
            stack.add(rangeIota)
            stack.add(label)
            throw MishapInvalidIota.ofType(parts[1], 1, "double")
        }
        val current = if (parts.size == 3) {
            (parts[2] as? DoubleIota)?.double ?: run {
                stack.add(rangeIota)
                stack.add(label)
                throw MishapInvalidIota.ofType(parts[2], 1, "double")
            }
        } else {
            null
        }

        if (min > max) {
            stack.add(rangeIota)
            stack.add(label)
            throw MishapInvalidIota.of(DoubleIota(min), 1, "double.between", min, max)
        }
        if (current != null && (current < min || current > max)) {
            stack.add(rangeIota)
            stack.add(label)
            throw MishapInvalidIota.of(DoubleIota(current), 1, "double.between", min, max)
        }

        stack.add(UiSliderIota(label, min, max, current))

        val image2 = image.withUsedOp().copy(stack = stack)
        return OperationResult(image2, listOf(), continuation, HexEvalSounds.NORMAL_EXECUTE)
    }
}
