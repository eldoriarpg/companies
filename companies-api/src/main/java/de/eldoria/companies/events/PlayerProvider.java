package de.eldoria.companies.events;

import org.bukkit.OfflinePlayer;

public interface PlayerProvider<Player extends OfflinePlayer> {
    Player player();
}
