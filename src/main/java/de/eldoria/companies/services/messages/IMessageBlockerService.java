package de.eldoria.companies.services.messages;

import org.bukkit.entity.Player;

public interface IMessageBlockerService {
    void blockPlayer(Player player);

    void unblockPlayer(Player player);

    boolean isActive();

    static IMessageBlockerService dummy() {
        return new IMessageBlockerService() {
            @Override
            public void blockPlayer(Player player) {
            }

            @Override
            public void unblockPlayer(Player player) {
            }

            @Override
            public boolean isActive() {
                return false;
            }
        };
    }
}
