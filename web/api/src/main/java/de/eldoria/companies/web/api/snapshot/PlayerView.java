/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api.snapshot;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * The player a session belongs to.
 *
 * @param uuid the player's id
 * @param name the player's name
 * @param company the id of the player's company, none without one
 * @param permissions the player's permissions in the company
 * @param invites pending invites
 * @param unreadInbox the number of unread inbox entries
 * @param online whether the player is online on a node of the server; the gateway fills it in from the nodes'
 *               {@link de.eldoria.companies.web.api.session.PlayerPresence}, a plugin leaves it out
 */
public record PlayerView(
        UUID uuid,
        String name,
        @Nullable Integer company,
        List<MemberPermission> permissions,
        List<InviteView> invites,
        int unreadInbox,
        @Nullable Boolean online) {}
