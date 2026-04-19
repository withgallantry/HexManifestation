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
 *   * Input:  <any iota>
 *   * Button: [<action-list>, <label>]
 *
 * Parsing rule:
 *   * We only treat an entry as a button when it is exactly a 2-element list
 *     whose first element is also a list.
 *   * Any other shape is treated as a literal input-label iota and rendered
 *     via display(). This includes list iotas produced by intro/retro.
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

        // Fixed contract: top is title, below is buttons-list.
        // This corresponds to input order [buttons-list, title].
        val titleIota = stack.removeAt(stack.lastIndex)
        val buttonsIota = stack.removeAt(stack.lastIndex)

        val title: Component = titleIota.display()
        Manifestation.LOGGER.info(
            "MenuReader: title = '{}' (type {})",
            title.string, titleIota::class.simpleName
        )

        Manifestation.LOGGER.info(
            "MenuReader: buttons-slot iota type {}",
            buttonsIota::class.simpleName
        )
        if (buttonsIota !is ListIota) {
            Manifestation.LOGGER.warn(
                "MenuReader: buttons slot is NOT a ListIota — it is {}. Mishapping.",
                buttonsIota::class.simpleName
            )
            // Restore original order on mishap.
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
    * Input form:
    *   * <any iota>
     *
     * Button form:
     *   * [ <action-list>, <label> ]
     */
    private fun tryReadEntry(buttonIota: Iota, index: Int): MenuEntry? {
        if (buttonIota !is ListIota) {
            return MenuEntry.input(buttonIota.display())
        }

        val parts = buttonIota.list.toList()
        if (parts.size != 2) {
            // Not the explicit button shape: treat the whole iota as a label.
            return MenuEntry.input(buttonIota.display())
        }

        val actionsIota = parts[0]
        val labelIota = parts[1]

        if (actionsIota !is ListIota) {
            // Also not explicit button shape; keep as literal label.
            return MenuEntry.input(buttonIota.display())
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
