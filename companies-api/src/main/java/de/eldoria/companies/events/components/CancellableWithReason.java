/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.components;

import net.kyori.adventure.text.Component;
import org.bukkit.event.Cancellable;

import java.util.Optional;

/**
 * An event that stops an action when it is cancelled, and tells the player who triggered the action why.
 * <p>
 * Cancel with {@link #cancel(Component)} to show your own reason. {@link #setCancelled(boolean) setCancelled(true)}
 * without a reason shows a generic message that an integration prevented the action. Un-cancelling the event drops
 * the reason. When the action was triggered through the API, the reason is passed back to the calling plugin instead.
 */
public interface CancellableWithReason extends Cancellable {
    /**
     * Cancels the action and sets the reason shown to the player who triggered it.
     *
     * @param reason message for the player
     */
    void cancel(Component reason);

    /**
     * The reason set with {@link #cancel(Component)}.
     *
     * @return the reason while the event is cancelled with one, otherwise empty
     */
    Optional<Component> cancelReason();
}
