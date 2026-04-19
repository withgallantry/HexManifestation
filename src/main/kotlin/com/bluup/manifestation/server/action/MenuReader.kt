package com.bluup.manifestation.server.action

import at.petrak.hexcasting.api.casting.iota.DoubleIota
import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.casting.iota.ListIota
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidIota
import at.petrak.hexcasting.api.casting.mishaps.MishapNotEnoughArgs
import com.bluup.manifestation.Manifestation
import com.bluup.manifestation.common.menu.MenuEntry
import com.bluup.manifestation.common.menu.StoredIota
import net.minecraft.network.chat.Component

/**
 * Shared iota-reading logic for the two menu operators.
 *
 * Error policy:
 *   * Title wrong type / missing              -> hard mishap (MishapInvalidIota)
 *   * Outer button-list wrong type / missing  -> hard mishap
 *   * Individual malformed button             -> skip, keep going
 *
 * Entry forms:
 *   * Input:  <label>
 *   * Input:  [<label>]
 *   * Button: [<action-list>, <label>]
 *
 * A "malformed button" is a button-like entry whose shape is wrong:
 *   * Not exactly 2 elements
 *   * First element (the action-list) isn't a list
 *   * Action-list is empty (no actions = useless button)
 *
 * Crucially, we no longer check the CONTENTS of the action-list for type.
 * Any iota Hex knows about — patterns, numbers, strings (MoreIotas), vectors,
 * entities, custom addon iotas like Ioticblocks View — is accepted and
 * forwarded untouched to the VM. Inputs are not dispatched as executable
 * iotas; their runtime text is converted to string iotas and pushed directly
 * onto the stack when a button is clicked.
 */
internal object MenuReader {

    data class ReadResult(val title: Component, val entries: List<MenuEntry>)

    fun readMenu(stack: MutableList<Iota>): ReadResult {
        Manifestation.LOGGER.info(
            "MenuReader.readMenu: stack size = {}, top 4 = {}",
            stack.size,
            stack.takeLast(4).map { "${it::class.simpleName}(${it.display().string})" }
        )

        if (stack.size < 2) {
            throw MishapNotEnoughArgs(2, stack.size)
        }

        // Title is on top.
        val titleIota = stack.removeAt(stack.lastIndex)
        val title: Component = titleIota.display()
        Manifestation.LOGGER.info(
            "MenuReader: popped title = '{}' (type {})",
            title.string, titleIota::class.simpleName
        )

        // Button list is below.
        val buttonsIota = stack.removeAt(stack.lastIndex)
        Manifestation.LOGGER.info(
            "MenuReader: popped buttons-slot iota of type {}",
            buttonsIota::class.simpleName
        )
        if (buttonsIota !is ListIota) {
            Manifestation.LOGGER.warn(
                "MenuReader: buttons slot is NOT a ListIota — it is {}. Mishapping.",
                buttonsIota::class.simpleName
            )
            stack.add(buttonsIota)
            stack.add(titleIota)
            throw MishapInvalidIota.ofType(buttonsIota, 1, "list")
        }

        val entries = mutableListOf<MenuEntry>()
        var index = 0
        for (buttonIota in buttonsIota.list) {
            val entry = tryReadEntry(buttonIota, index)
            if (entry != null) {
                Manifestation.LOGGER.info(
                    "MenuReader: entry[{}] ACCEPTED — kind={}, label='{}', {} actions",
                    index, entry.kind(), entry.label().string, entry.actions().size
                )
                entries.add(entry)
            } else {
                Manifestation.LOGGER.warn(
                    "MenuReader: entry[{}] REJECTED (see prior warning for reason)",
                    index
                )
            }
            index++
        }

        Manifestation.LOGGER.info(
            "MenuReader: final result — title='{}', {} of {} entries accepted",
            title.string, entries.size, index
        )
        return ReadResult(title, entries)
    }

    fun readColumnCount(stack: MutableList<Iota>): Int {
        if (stack.isEmpty()) {
            throw MishapNotEnoughArgs(1, 0)
        }
        val top = stack.removeAt(stack.lastIndex)
        if (top !is DoubleIota) {
            stack.add(top)
            throw MishapInvalidIota.ofType(top, 0, "double")
        }
        val cols = Math.round(top.double).toInt()
        return cols.coerceIn(1, 10)
    }

    /**
     * Interpret one iota as either an input or a button.
     *
     * Input forms:
     *   * <label>
     *   * [ <label> ]
     *
     * Button form:
     *   * [ <action-list>, <label> ]
     */
    private fun tryReadEntry(buttonIota: Iota, index: Int): MenuEntry? {
        if (buttonIota !is ListIota) {
            return MenuEntry.input(buttonIota.display())
        }

        val parts = buttonIota.list.toList()
        if (parts.size == 1) {
            return MenuEntry.input(parts[0].display())
        }

        if (parts.size != 2) {
            Manifestation.LOGGER.warn(
                "  button[{}]: REJECTED — expected 2 elements (action-list, label), got {}",
                index, parts.size
            )
            return null
        }

        val actionsIota = parts[0]
        val labelIota = parts[1]

        if (actionsIota !is ListIota) {
            Manifestation.LOGGER.warn(
                "  button[{}]: REJECTED — element[0] is not a ListIota (got {}). " +
                    "The action slot must be a list of iotas to dispatch on click.",
                index, actionsIota::class.simpleName
            )
            return null
        }

        val stored = mutableListOf<StoredIota>()
        var actionIndex = 0
        for (action in actionsIota.list) {
            Manifestation.LOGGER.info(
                "    button[{}].action[{}]: type = {}",
                index, actionIndex, action::class.simpleName
            )
            stored.add(StoredIota.of(action))
            actionIndex++
        }

        if (stored.isEmpty()) {
            Manifestation.LOGGER.warn(
                "  button[{}]: REJECTED — action-list is empty",
                index
            )
            return null
        }

        return MenuEntry.button(labelIota.display(), stored)
    }
}
