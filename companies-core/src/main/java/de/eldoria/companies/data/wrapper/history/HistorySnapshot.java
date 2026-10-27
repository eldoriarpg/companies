/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.history;

import de.eldoria.companies.components.order.ItemCriteria;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

/**
 * What an order looked like when it ended, kept because cancelled and purged orders are deleted and aborted or expired
 * orders lose their deliveries to the item returns.
 * <p>
 * An owner snapshot has the price and fee and no money of workers. A company snapshot has the gross, tax and net of
 * the payouts and the workers, and no fee.
 *
 * @param lines   ordered lines with what was delivered
 * @param price   price of the order
 * @param fee     fee the owner paid, 0 on company snapshots
 * @param gross   paid shares before tax, 0 on owner snapshots
 * @param tax     tax taken from the shares, 0 on owner snapshots
 * @param net     amount paid out, 0 on owner snapshots
 * @param workers what every worker delivered and got, empty on owner snapshots
 */
public record HistorySnapshot(
        List<Line> lines, double price, double fee, double gross, double tax, double net, List<Worker> workers) {
    public HistorySnapshot {
        lines = List.copyOf(lines);
        workers = List.copyOf(workers);
    }

    /**
     * Reads a stored snapshot.
     *
     * @param mapper mapper able to read item stacks
     * @param json   stored snapshot
     * @return snapshot
     * @throws JacksonException when the text is no snapshot
     */
    public static HistorySnapshot fromJson(ObjectMapper mapper, String json) {
        return mapper.readValue(json, HistorySnapshot.class);
    }

    /**
     * Writes the snapshot for storage.
     *
     * @param mapper mapper able to write item stacks
     * @return JSON object
     */
    public String toJson(ObjectMapper mapper) {
        return mapper.writeValueAsString(this);
    }

    /**
     * One line of the order.
     *
     * @param template  ordered item, for a criteria line the item standing for it
     * @param exact     whether the item had to match exactly
     * @param amount    ordered amount
     * @param delivered delivered amount
     * @param price     price of the line
     * @param criteria  what a criteria line accepted, null for material and exact lines
     */
    public record Line(
            ItemStack template,
            boolean exact,
            int amount,
            int delivered,
            double price,
            @Nullable ItemCriteria criteria) {
        /**
         * A material or exact line.
         *
         * @param template  ordered item
         * @param exact     whether the item had to match exactly
         * @param amount    ordered amount
         * @param delivered delivered amount
         * @param price     price of the line
         */
        public Line(ItemStack template, boolean exact, int amount, int delivered, double price) {
            this(template, exact, amount, delivered, price, null);
        }
    }

    /**
     * What one worker contributed.
     *
     * @param worker worker
     * @param items  items delivered
     * @param gross  share before tax
     * @param net    amount paid out
     */
    public record Worker(UUID worker, int items, double gross, double net) {}
}
