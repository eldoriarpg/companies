/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.company;

import de.eldoria.companies.components.company.ISimpleCompany;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * A company got or lost a perk: a member bought it, or an admin granted or took it. Read the perks with
 * {@code companies().perks(id)}.
 * <p>
 * Fired asynchronously after the change was saved, only on the server where it happened.
 */
public class CompanyPerkChangeEvent extends CompanyEvent<ISimpleCompany> {
    private static final HandlerList HANDLERS = new HandlerList();

    private final String perk;
    private final Change change;
    private final @Nullable UUID actor;

    /**
     * Creates a new asynchronous event.
     *
     * @param company the company
     * @param perk    the id of the perk
     * @param change  what happened
     * @param actor   who did it, null when the console did
     */
    public CompanyPerkChangeEvent(ISimpleCompany company, String perk, Change change, @Nullable UUID actor) {
        super(company, true);
        this.perk = Objects.requireNonNull(perk, "perk");
        this.change = Objects.requireNonNull(change, "change");
        this.actor = actor;
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
     * The perk.
     *
     * @return the id of the perk
     */
    public String perk() {
        return perk;
    }

    /**
     * What happened.
     *
     * @return bought, granted or taken
     */
    public Change change() {
        return change;
    }

    /**
     * Who bought, granted or took the perk.
     *
     * @return the player, null when the console did
     */
    public @Nullable UUID actor() {
        return actor;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    /**
     * What happened to a perk.
     */
    public enum Change {
        /**
         * A member bought it from the treasury.
         */
        BOUGHT,
        /**
         * An admin granted it.
         */
        GRANTED,
        /**
         * An admin took it.
         */
        TAKEN
    }
}
