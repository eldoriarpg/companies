/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository;

import de.eldoria.companies.data.wrapper.area.CompanyHome;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * The homes of companies, and the teleports to a home on another server that wait for the player to arrive there. The
 * methods block and are called off the main thread.
 */
public abstract class AHomeData {
    /**
     * @param company id of a company
     * @return its home, empty when it has none
     */
    public abstract Optional<CompanyHome> home(int company);

    /**
     * Sets or moves the home of a company.
     *
     * @param home the home
     * @return true when it was stored
     */
    public abstract boolean set(CompanyHome home);

    /**
     * Removes the home of a company.
     *
     * @param company id of the company
     * @return true when it had one
     */
    public abstract boolean remove(int company);

    /**
     * Notes that a player is on the way to the home of a company on another server, replacing an earlier note.
     *
     * @param player  the player
     * @param company id of the company
     * @param node    the server of the home
     * @param expires until when the player is expected there
     * @return true when the note was stored
     */
    public abstract boolean travelling(UUID player, int company, UUID node, LocalDateTime expires);

    /**
     * Takes the note of a player arriving on a server, once. A note for another server stays, so a player passing a
     * lobby on the way still arrives; notes of the player that expired are dropped.
     *
     * @param player the player
     * @param node   the server they arrived on
     * @param now    the current time
     * @return the company whose home the player travels to, empty when they travel to none on this server or the note
     *     expired
     */
    public abstract OptionalInt arrived(UUID player, UUID node, LocalDateTime now);

    /**
     * Sets the proxy name of the homes set on a server, when the server starts, so a name added or changed in its
     * {@code node.yml} reaches the homes set before.
     *
     * @param node   the server
     * @param server its name in the proxy, empty for none
     */
    public abstract void server(UUID node, String server);
}
