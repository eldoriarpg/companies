package de.eldoria.companies.services.messages;

import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface IMessageBlockerService {
    /**
     * Creates a dummy {@link IMessageBlockerService} which will not block any messages, but will behave like an active message blocker
     *
     * @return new message blocker instance
     */
    static IMessageBlockerService dummy() {
        return p -> false;
    }

    /**
     * Checks if the message contains a generally whitelisted character. This can be used to allow messages identified by a plugin prefix to be delivered.
     *
     * @param value value to check
     * @return true when a whitelisted char combination was found
     */
    default boolean isWhitelisted(String value) {
        return false;
    }

    /**
     * @param player player to block
     */
    default void blockPlayer(Player player) {
    }

    /**
     * Unblock a player from message blocker
     *
     * @param player player to unblock
     * @return a completable future which will be completed once all blocked packets were send
     */
    default CompletableFuture<Void> unblockPlayer(Player player) {
        return null;
    }

    /**
     * Announces that a player will soon receive a message which needs to be let through
     *
     * @param player player which will receive the message
     * @param key    a key inside the message. This needs to be part of the content of the message in case senstive way.
     */
    default void announce(Player player, String key) {
    }

    /**
     * Indicates whether the service is active or not.
     *
     * @return true if active
     */
    default boolean isActive() {
        return false;
    }

    /**
     * Checks if the messages for a player are blocked
     *
     * @param player player to check
     * @return true if the player has blocked messages currently
     */
    boolean isBlocked(Player player);
}
