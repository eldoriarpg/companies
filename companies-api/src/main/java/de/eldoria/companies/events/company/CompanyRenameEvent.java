/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.company;

import de.eldoria.companies.components.company.ICompanyProfile;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A company was renamed, by its owner or by an admin.
 * <p>
 * Fired after the new name was saved, usually off the main thread; the event may be asynchronous. It fires only on the
 * server where the company was renamed.
 */
public class CompanyRenameEvent extends CompanyEvent<ICompanyProfile> {
    private static final HandlerList HANDLERS = new HandlerList();

    private final String oldName;

    @Nullable
    private final UUID actor;

    private final boolean byAdmin;

    /**
     * Creates a new event. It is asynchronous when created off the main thread.
     *
     * @param company the company with its new name
     * @param oldName name before the change
     * @param actor   player who renamed the company, or null for the console
     * @param byAdmin whether the company was renamed with an admin command
     */
    public CompanyRenameEvent(ICompanyProfile company, String oldName, @Nullable UUID actor, boolean byAdmin) {
        super(company, !Bukkit.isPrimaryThread());
        this.oldName = Objects.requireNonNull(oldName, "oldName");
        this.actor = actor;
        this.byAdmin = byAdmin;
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
     * The name before the change.
     *
     * @return old name
     */
    public String oldName() {
        return oldName;
    }

    /**
     * The name after the change.
     *
     * @return new name, the same as {@code company().name()}
     */
    public String newName() {
        return company().name();
    }

    /**
     * The player who renamed the company.
     *
     * @return id of the player, or empty when the console renamed it
     */
    public Optional<UUID> actor() {
        return Optional.ofNullable(actor);
    }

    /**
     * Whether the company was renamed with an admin command rather than by its owner.
     *
     * @return true for an admin rename
     */
    public boolean byAdmin() {
        return byAdmin;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
