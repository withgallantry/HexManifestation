package com.bluup.manifestation.client;

import com.bluup.manifestation.common.menu.MenuPayload;
import net.minecraft.world.InteractionHand;
import org.jetbrains.annotations.Nullable;

/**
 * Client-wide slot for "there is one Manifestation menu live right now."
 *
 * <p>Enforces the "one active menu at a time" constraint from the original
 * design doc. If a second trigger fires before the first menu is dismissed
 * or selected, the second one is ignored — no stacking.
 *
 * <p>Also tracks which {@link InteractionHand} is holding the staff, so
 * button-dispatch can route casts through the same hand. The server side
 * needs a valid hand to resolve the caster's active staff.
 *
 * <p>All access is on the render thread, so no synchronization needed.
 */
public final class ActiveMenuState {
    private static final ActiveMenuState INSTANCE = new ActiveMenuState();

    public static ActiveMenuState get() {
        return INSTANCE;
    }

    @Nullable
    private MenuPayload current;
    @Nullable
    private InteractionHand hand;

    private ActiveMenuState() {
    }

    public boolean isActive() {
        return current != null;
    }

    public void open(MenuPayload menu, InteractionHand hand) {
        this.current = menu;
        this.hand = hand;
    }

    /** Idempotent — safe to call on any screen close. */
    public void clear() {
        this.current = null;
        this.hand = null;
    }

    @Nullable
    public MenuPayload current() {
        return current;
    }

    @Nullable
    public InteractionHand hand() {
        return hand;
    }
}
