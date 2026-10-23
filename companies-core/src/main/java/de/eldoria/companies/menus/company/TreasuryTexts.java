/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.company;

import de.eldoria.companies.components.company.TreasuryKind;
import de.eldoria.companies.data.wrapper.treasury.TreasuryLogEntry;
import de.eldoria.companies.menus.Keys;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.util.Money;
import net.milkbowl.vault.economy.Economy;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Texts shared by the treasury menus: the lines of the treasury log.
 * <p>
 * A line shows when, what, the signed amount and the balance after it, followed by who did it, the order of a payout
 * share, or the new share of a share change.
 */
public final class TreasuryTexts {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM. HH:mm");

    private TreasuryTexts() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * The log lines, or a notice that the log is empty.
     *
     * @param prefix  prefix of the placeholder keys, unique within the menu
     * @param entries log entries, newest first
     * @param names   names of the actors by id
     * @param economy economy formatting the amounts
     * @return one line per entry
     */
    public static MenuText entries(
            String prefix, List<TreasuryLogEntry> entries, Map<UUID, String> names, Economy economy) {
        if (entries.isEmpty()) return MenuText.create().text("<inactive><i18n:company.bank.log.empty>");
        var text = MenuText.create();
        for (var index = 0; index < entries.size(); index++) {
            if (index > 0) text.newLine();
            text.append(entry(Keys.of(prefix, index), entries.get(index), names, economy));
        }
        return text;
    }

    /**
     * One log line.
     *
     * @param key     prefix of the placeholder keys of this line
     * @param entry   log entry
     * @param names   names of the actors by id
     * @param economy economy formatting the amounts
     * @return the line
     */
    public static MenuText entry(String key, TreasuryLogEntry entry, Map<UUID, String> names, Economy economy) {
        var time = Keys.of(key, "time");
        var text = MenuText.create()
                .text(
                        "<inactive><" + time + "></inactive> <name><i18n:"
                                + entry.kind().translationKey() + "></name>",
                        Values.text(time, TIME.format(entry.created())));
        if (entry.kind() != TreasuryKind.SHARE_CHANGED) {
            var amount = Keys.of(key, "amount");
            var balance = Keys.of(key, "balance");
            var style = entry.amount() < 0 ? "remove" : "add";
            var sign = entry.amount() < 0 ? "" : "+";
            text.text(
                    " <" + style + ">" + sign + "<" + amount + "></" + style + "> <inactive>→ <" + balance
                            + "></inactive>",
                    Values.text(
                            amount,
                            economy.format(Money.fromCents(entry.amount()).doubleValue())),
                    Values.text(
                            balance,
                            economy.format(Money.fromCents(entry.balance()).doubleValue())));
        }
        var detail = detail(entry, names);
        if (!detail.isEmpty()) {
            var detailKey = Keys.of(key, "detail");
            text.text(" <value><" + detailKey + ">", Values.text(detailKey, detail));
        }
        return text;
    }

    private static String detail(TreasuryLogEntry entry, Map<UUID, String> names) {
        if (entry.kind() == TreasuryKind.SHARE_CHANGED) {
            var who = entry.actorId()
                    .map(id -> names.getOrDefault(id, "?") + ": ")
                    .orElse("");
            return who + (entry.note() == null ? "" : entry.note() + " %");
        }
        if (entry.order() != null) return "#" + entry.order();
        var who = entry.actorId().map(id -> names.getOrDefault(id, "?")).orElse("");
        if (entry.note() == null || entry.note().isBlank()) return who;
        return who.isEmpty() ? entry.note() : who + " (" + entry.note() + ")";
    }
}
