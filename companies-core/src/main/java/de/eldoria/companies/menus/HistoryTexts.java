/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus;

import de.eldoria.companies.data.wrapper.history.HistoryEntry;
import de.eldoria.companies.data.wrapper.history.HistorySnapshot;
import net.milkbowl.vault.economy.Economy;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/**
 * The texts of the order history.
 * <p>
 * Only the side an entry belongs to is shown: owner entries show the price and the fee, company entries the money of
 * the payouts and what every member contributed. Neither names the other side of the order.
 */
public final class HistoryTexts {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yy HH:mm");

    private HistoryTexts() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * @param time point in time
     * @return the time as the history shows it
     */
    public static String date(LocalDateTime time) {
        return DATE.format(time);
    }

    /**
     * One line of a history list: name, outcome, end time and the price for owners or the net payout for companies.
     *
     * @param prefix  key prefix, unique within the list
     * @param entry   the entry
     * @param economy economy formatting the money
     * @return the line
     */
    public static MenuText summary(String prefix, HistoryEntry entry, Economy economy) {
        var name = Keys.of(prefix, "order_name");
        var finished = Keys.of(prefix, "finished");
        var money = Keys.of(prefix, "money");
        return MenuText.create()
                .text("<value><" + name + "> ", Values.text(name, entry.name()))
                .append(outcome(entry))
                .text(" <inactive><" + finished + "> ", Values.text(finished, date(entry.finished())))
                .text("<gold><" + money + ">", Values.money(money, economy, shownMoney(entry)));
    }

    /**
     * @param entry the entry
     * @return how the order ended, in the colour of the outcome
     */
    public static MenuText outcome(HistoryEntry entry) {
        var style =
                switch (entry.outcome()) {
                    case RECEIVED, DELIVERED -> "<active>";
                    case CANCELLED, PURGED -> "<neutral>";
                    default -> "<inactive>";
                };
        return MenuText.create().text(style + "<i18n:" + entry.outcome().translationKey() + ">");
    }

    /**
     * Everything an entry keeps: times, lines and money, and for company entries the members with what they
     * delivered and got.
     *
     * @param entry   the entry
     * @param names   names of the members of a company entry, by id; members without a name show their id
     * @param economy economy formatting the money
     * @return the details
     */
    public static MenuText details(HistoryEntry entry, Map<UUID, String> names, Economy economy) {
        var text = overview(entry, economy);
        var workers = entry.snapshot().workers();
        if (entry.ownerSide().isPresent() || workers.isEmpty()) return text;
        text.newLine().text("<heading><i18n:history.workers>:");
        for (var index = 0; index < workers.size(); index++) {
            var worker = workers.get(index);
            var name = names.getOrDefault(worker.worker(), worker.worker().toString());
            text.newLine().append(worker(index, worker, name, economy));
        }
        return text;
    }

    /**
     * The times, lines and money of an entry: price and fee for owner entries, price and the payouts for company
     * entries.
     *
     * @param entry   the entry
     * @param economy economy formatting the money
     * @return the overview
     */
    public static MenuText overview(HistoryEntry entry, Economy economy) {
        var snapshot = entry.snapshot();
        var text = MenuText.create()
                .text("<name><i18n:history.outcome>: ")
                .append(outcome(entry))
                .newLine()
                .text("<name><i18n:history.placed>: <value><placed_at>", Values.text("placed_at", date(entry.placed())))
                .newLine()
                .text(
                        "<name><i18n:history.finished>: <value><finished_at>",
                        Values.text("finished_at", date(entry.finished())));
        var lines = snapshot.lines();
        for (var index = 0; index < lines.size(); index++) {
            text.newLine().append(line(index, lines.get(index), economy));
        }
        text.newLine().append(money("order_price", "words.price", snapshot.price(), economy));
        if (entry.ownerSide().isPresent()) {
            return text.newLine().append(money("order_fee", "words.fee", snapshot.fee(), economy));
        }
        return text.newLine()
                .append(money("payout_gross", "history.gross", snapshot.gross(), economy))
                .newLine()
                .append(money("payout_tax", "words.tax", snapshot.tax(), economy))
                .newLine()
                .append(money("payout_net", "words.net", snapshot.net(), economy));
    }

    private static double shownMoney(HistoryEntry entry) {
        var snapshot = entry.snapshot();
        return entry.ownerSide().isPresent() ? snapshot.price() : snapshot.net();
    }

    private static MenuText line(int index, HistorySnapshot.Line line, Economy economy) {
        var item = Keys.of("line", index, "item");
        var delivered = Keys.of("line", index, "delivered");
        var amount = Keys.of("line", index, "amount");
        var price = Keys.of("line", index, "price");
        return MenuText.create()
                .text(
                        "<yellow><" + item + "> <blue><" + delivered + ">/<" + amount + "> <gold><" + price + ">",
                        Values.item(item, line.template()),
                        Values.number(delivered, line.delivered()),
                        Values.number(amount, line.amount()),
                        Values.money(price, economy, line.price()));
    }

    private static MenuText money(String key, String label, double amount, Economy economy) {
        return MenuText.create()
                .text("<name><i18n:" + label + ">: <value><" + key + ">", Values.money(key, economy, amount));
    }

    private static MenuText worker(int index, HistorySnapshot.Worker worker, String name, Economy economy) {
        var nameKey = Keys.of("worker", index, "name");
        var items = Keys.of("worker", index, "items");
        var net = Keys.of("worker", index, "net");
        return MenuText.create()
                .text(
                        "<value><" + nameKey + "> <blue><" + items + "> <i18n:words.items> <gold><" + net + ">",
                        Values.text(nameKey, name),
                        Values.number(items, worker.items()),
                        Values.money(net, economy, worker.net()));
    }
}
