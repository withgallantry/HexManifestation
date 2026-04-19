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
import com.bluup.manifestation.server.iota.UiDropdownIota

/**
 * Constructor operator for typed UI dropdown entries.
 *
 * Stack shape on entry (top -> bottom):
 *   label
 *   [string, string, ...]
 */
object OpUiDropdown : Action {
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
        val optionsIota = stack.removeAt(stack.lastIndex)

        if (optionsIota !is ListIota) {
            stack.add(optionsIota)
            stack.add(label)
            throw MishapInvalidIota.ofType(optionsIota, 1, "list")
        }

        if (!optionsIota.list.nonEmpty) {
            stack.add(optionsIota)
            stack.add(label)
            throw MishapInvalidIota.ofType(optionsIota, 1, "non_empty_list")
        }

        val options = mutableListOf<at.petrak.hexcasting.api.casting.iota.Iota>()
        for (option in optionsIota.list) {
            options.add(option)
        }

        stack.add(UiDropdownIota(label, options, 0))

        val image2 = image.withUsedOp().copy(stack = stack)
        return OperationResult(image2, listOf(), continuation, HexEvalSounds.NORMAL_EXECUTE)
    }
}