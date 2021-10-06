package de.eldoria.companies.events.components;

import org.bukkit.OfflinePlayer;

@FunctionalInterface
public interface PlayerProvider<P extends OfflinePlayer> {
    P player();
}
