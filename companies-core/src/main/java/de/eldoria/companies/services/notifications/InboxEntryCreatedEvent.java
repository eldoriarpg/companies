/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.notifications;

import de.eldoria.companies.data.wrapper.inbox.InboxEntry;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * A notification was stored in the inbox of a player. Internal to the plugin: the entry carries message keys and
 * placeholder values, which are no stable contract for other plugins.
 * <p>
 * Fired after the entry was saved, off the main thread, only on the server that sent the notification. Muted
 * notifications and those shown live fire it too; {@link InboxEntry#delivered()} and {@link InboxEntry#read()} tell
 * them apart from pending ones.
 */
public final class InboxEntryCreatedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final InboxEntry entry;

    /**
     * Creates a new event.
     *
     * @param entry the stored entry
     */
    public InboxEntryCreatedEvent(InboxEntry entry) {
        super(true);
        this.entry = Objects.requireNonNull(entry, "entry");
    }

    /**
     * The handlers of this event.
     *
     * @return handler list
     */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    /**
     * The stored entry, as it was saved.
     *
     * @return the entry
     */
    public InboxEntry entry() {
        return entry;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
