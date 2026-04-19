package com.bluup.manifestation.server.action

import at.petrak.hexcasting.api.casting.castables.Action
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.eval.OperationResult
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage
import at.petrak.hexcasting.api.casting.eval.vm.SpellContinuation
import at.petrak.hexcasting.api.casting.iota.ListIota
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidIota
import at.petrak.hexcasting.api.casting.mishaps.MishapNotEnoughArgs
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds
import com.bluup.manifestation.server.iota.UiButtonIota
import net.minecraft.network.chat.Component

/**
 * Constructor operator for typed UI button entries.
 *
 * Stack shape on entry (top -> bottom):
 *   label
 *   [action, action, ...]
 */
object OpUiButton : Action {
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
        val actionsIota = stack.removeAt(stack.lastIndex)
        if (actionsIota !is ListIota) {
            stack.add(actionsIota)
            stack.add(label)
            throw MishapInvalidIota.ofType(actionsIota, 1, "list")
        }
        if (!actionsIota.list.nonEmpty) {
            stack.add(actionsIota)
            stack.add(label)
            throw MishapInvalidIota(actionsIota, 1, Component.literal("non-empty list"))
        }

        stack.add(UiButtonIota(label, actionsIota.list.toList()))
        val image2 = image.withUsedOp().copy(stack = stack)
        return OperationResult(image2, listOf(), continuation, HexEvalSounds.NORMAL_EXECUTE)
    }
}
