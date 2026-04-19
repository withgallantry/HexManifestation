package com.bluup.manifestation.common.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Objects;

/**
 * A single menu entry in serializable form.
 *
 * <p>Sent from server to client inside a {@link MenuPayload}. Entries can be
 * either:
 * <ul>
 *   <li>{@link Kind#BUTTON}: dispatches its action list when clicked</li>
 *   <li>{@link Kind#INPUT}: renders a text input field with this label as hint</li>
 * </ul>
 *
 * <p>For button entries, the {@code actions} list preserves order: iotas are
 * dispatched in the sequence they were pushed when the menu was defined.
 */
public final class MenuEntry {
    public enum Kind {
        BUTTON,
        INPUT
    }

    private final Kind kind;
    private final Component label;
    private final List<StoredIota> actions;

    public MenuEntry(Kind kind, Component label, List<StoredIota> actions) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.label = Objects.requireNonNull(label, "label");
        this.actions = List.copyOf(actions);
    }

    public static MenuEntry button(Component label, List<StoredIota> actions) {
        return new MenuEntry(Kind.BUTTON, label, actions);
    }

    public static MenuEntry input(Component label) {
        return new MenuEntry(Kind.INPUT, label, List.of());
    }

    public Kind kind() {
        return kind;
    }

    public boolean isButton() {
        return kind == Kind.BUTTON;
    }

    public boolean isInput() {
        return kind == Kind.INPUT;
    }

    public Component label() {
        return label;
    }

    public List<StoredIota> actions() {
        return actions;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeEnum(kind);
        buf.writeComponent(label);
        if (kind == Kind.BUTTON) {
            buf.writeVarInt(actions.size());
            for (StoredIota a : actions) {
                a.write(buf);
            }
        }
    }

    public static MenuEntry read(FriendlyByteBuf buf) {
        Kind kind = buf.readEnum(Kind.class);
        Component label = buf.readComponent();
        if (kind == Kind.INPUT) {
            return MenuEntry.input(label);
        }

        int n = buf.readVarInt();
        StoredIota[] actions = new StoredIota[n];
        for (int i = 0; i < n; i++) {
            actions[i] = StoredIota.read(buf);
        }
        return MenuEntry.button(label, List.of(actions));
    }
}
