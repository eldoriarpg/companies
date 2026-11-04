/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.demo;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Keeps visitors to small payments: {@code /pay <player> <amount>} above {@code maxAmount} is refused, the amount read
 * with the abbreviations of XConomy ({@code k}, {@code m}, {@code b}). An amount that can not be read is refused too,
 * so no spelling the economy plugin understands slips past; an amount XConomy would round down to the cap is refused
 * as well. Only the commands named here are capped, so every pay command visitors can reach must be among them.
 * {@value #UNLIMITED} lifts the cap; a cap of 0 or less caps nothing.
 */
final class PayCap implements Listener {
    /**
     * Pays any amount.
     */
    static final String UNLIMITED = "companiesdemo.pay.unlimited";

    private final Set<String> commands;
    private final BigDecimal max;
    private final String refused;

    /**
     * @param commands the labels of the pay command, without slash, with a plugin prefix where it may be typed with one
     * @param max      the largest amount a visitor may pay at once
     * @param refused  the message a refused payment answers with, {@code <max>} the cap
     */
    PayCap(Set<String> commands, BigDecimal max, String refused) {
        this.commands = commands.stream()
                .map(command -> command.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        this.max = max;
        this.refused = refused;
    }

    /**
     * @param command a command as typed, with or without slash
     * @return whether it pays more than the cap, or an amount that can not be read
     */
    boolean exceeds(String command) {
        if (max.signum() <= 0) return false;
        var text = command.startsWith("/") ? command.substring(1) : command;
        var args = text.trim().split("\\s+");
        if (args.length < 3 || !commands.contains(args[0].toLowerCase(Locale.ROOT))) return false;
        var amount = amount(args[2]);
        return amount == null || amount.compareTo(max) > 0;
    }

    /**
     * @param text an amount as XConomy reads it, such as {@code 250}, {@code 1.5k} or {@code 2m}
     * @return the amount, or null if it can not be read
     */
    static BigDecimal amount(String text) {
        var lower = text.toLowerCase(Locale.ROOT);
        var factor =
                switch (lower.isEmpty() ? ' ' : lower.charAt(lower.length() - 1)) {
                    case 'k' -> BigDecimal.TEN.pow(3);
                    case 'm' -> BigDecimal.TEN.pow(6);
                    case 'b' -> BigDecimal.TEN.pow(9);
                    default -> BigDecimal.ONE;
                };
        var number = factor.equals(BigDecimal.ONE) ? lower : lower.substring(0, lower.length() - 1);
        try {
            return new BigDecimal(number).multiply(factor);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Refuses a payment above the cap.
     *
     * @param event the command a player typed
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (event.getPlayer().hasPermission(UNLIMITED) || !exceeds(event.getMessage())) return;
        event.setCancelled(true);
        event.getPlayer().sendRichMessage(refused, Placeholder.unparsed("max", shownMax()));
    }

    /**
     * @return the cap as a visitor is shown it, without trailing zeros
     */
    String shownMax() {
        return max.stripTrailingZeros().toPlainString();
    }
}
