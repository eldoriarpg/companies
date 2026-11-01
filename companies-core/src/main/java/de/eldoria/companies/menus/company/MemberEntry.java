/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.company;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.util.PlayerNames;
import de.eldoria.companies.util.Times;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * One member as the member list shows it.
 *
 * @param uuid        id of the player
 * @param name        name of the player
 * @param lastSeen    when the player was last online, formatted, or empty while online
 * @param permissions permissions of the member
 */
public record MemberEntry(UUID uuid, String name, Optional<String> lastSeen, List<CompanyPermission> permissions) {
    private static final DateTimeFormatter LAST_SEEN = DateTimeFormatter.ofPattern("dd.MM.yy HH:mm");

    /**
     * @param member member of a company
     * @return the entry of the member
     */
    public static MemberEntry of(CompanyMember member) {
        var player = member.player();
        return new MemberEntry(member.uuid(), nameOf(player), lastSeen(player), List.copyOf(member.permissions()));
    }

    /**
     * The name of a player, or the id of the player when neither the server nor its user cache knows the name.
     *
     * @param player the player
     * @return name to show
     */
    public static String nameOf(OfflinePlayer player) {
        return nameOf(player, PlayerNames::of);
    }

    /**
     * The name of a player, or the id of the player when the lookup does not know the name.
     *
     * @param player the player
     * @param names  looks up a player's name
     * @return name to show
     */
    static String nameOf(OfflinePlayer player, Function<OfflinePlayer, @Nullable String> names) {
        return Optional.ofNullable(names.apply(player))
                .orElseGet(() -> player.getUniqueId().toString());
    }

    /**
     * @return whether the member is online
     */
    public boolean online() {
        return lastSeen.isEmpty();
    }

    /**
     * @return whether the member owns the company
     */
    public boolean owner() {
        return permissions.contains(CompanyPermission.OWNER);
    }

    private static Optional<String> lastSeen(OfflinePlayer player) {
        if (player.isOnline()) return Optional.empty();
        var time = LocalDateTime.ofInstant(Instant.ofEpochMilli(player.getLastSeen()), Times.zone());
        return Optional.of(LAST_SEEN.format(time));
    }
}
