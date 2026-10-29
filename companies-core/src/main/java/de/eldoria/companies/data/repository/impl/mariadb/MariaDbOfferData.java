/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.mariadb;

import de.chojo.sadu.mapper.wrapper.Row;
import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.call.adapter.Adapter;
import de.chojo.sadu.queries.api.configuration.QueryConfiguration;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.elements.systemorders.MoneySource;
import de.eldoria.companies.configuration.elements.systemorders.RuleEntry;
import de.eldoria.companies.data.repository.AOfferData;
import de.eldoria.companies.data.repository.impl.TimeColumns;
import de.eldoria.companies.data.wrapper.offer.ChunkState;
import de.eldoria.companies.data.wrapper.offer.Contributor;
import de.eldoria.companies.data.wrapper.offer.EventOffer;
import de.eldoria.companies.data.wrapper.offer.OfferKind;
import de.eldoria.companies.data.wrapper.offer.OfferLine;
import de.eldoria.companies.data.wrapper.offer.OfferMark;
import de.eldoria.companies.data.wrapper.offer.OfferState;
import de.eldoria.companies.data.wrapper.offer.OpenChunk;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nullable;
import tools.jackson.databind.ObjectMapper;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_BYTES;

/**
 * Event offers for MariaDB. PostgreSQL and SQLite run the same statements.
 */
public class MariaDbOfferData extends AOfferData {
    private static final int MARKS_PER_QUERY = 500;

    @Language("mariadb")
    private static final String OFFER = """
            SELECT id, kind, name, event_id, material, criteria, resolve, item, label, amount, unit_price, chunk_size,
                   open_chunks, source, account, budget, spent, delivered, starts, ends, state, created_by, created,
                   closed
            FROM event_offers
            """;

    /**
     * @param mapper mapper for the exact items of offers, able to read and write item stacks
     */
    public MariaDbOfferData(ObjectMapper mapper) {
        super(mapper);
    }

    /**
     * Binds a point in time in the form the time columns of this dialect compare correctly.
     *
     * @return adapter for times
     */
    protected Adapter<LocalDateTime> time() {
        return TimeColumns.TIMESTAMP;
    }

    /**
     * Reads a time column.
     *
     * @param row    row
     * @param column name of the column
     * @return time, null when the column is null
     * @throws SQLException when the column is missing
     */
    protected @Nullable LocalDateTime readTime(Row row, String column) throws SQLException {
        return row.getObject(column, LocalDateTime.class);
    }

    @Override
    public Optional<Integer> create(EventOffer offer) {
        @Language("mariadb")
        var insert = """
                INSERT INTO event_offers(kind, name, event_id, material, criteria, resolve, item, label, amount,
                                         unit_price, chunk_size, open_chunks, source, account, budget, spent,
                                         delivered, starts, ends, state, created_by, created)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, ?, ?, ?, ?, ?)
                RETURNING id""";
        var line = offer.line();
        var item = line.item();
        var resolve = line.resolve();
        return query(insert)
                .single(call().bind(offer.kind().name())
                        .bind(offer.name())
                        .bind(offer.event())
                        .bind(line.material())
                        .bind(line.criteria())
                        .bind(resolve == null ? null : resolve.name())
                        .bind(item == null ? null : toJson(item))
                        .bind(line.label())
                        .bind(offer.amount())
                        .bind(offer.unitPrice())
                        .bind(offer.chunkSize())
                        .bind(offer.openChunks())
                        .bind(offer.source().name())
                        .bind(offer.account())
                        .bind(offer.budget())
                        .bind(offer.starts(), time())
                        .bind(offer.ends(), time())
                        .bind(offer.state().name())
                        .bind(offer.createdBy())
                        .bind(offer.created(), time()))
                .map(row -> row.getInt(1))
                .first();
    }

    @Override
    public Optional<EventOffer> offer(int id) {
        return query(OFFER + "WHERE id = ?")
                .single(call().bind(id))
                .map(this::parseOffer)
                .first();
    }

    @Override
    public List<EventOffer> offers(int limit) {
        return query(OFFER + "ORDER BY id DESC LIMIT ?")
                .single(call().bind(limit))
                .map(this::parseOffer)
                .all();
    }

    @Override
    public List<EventOffer> working() {
        @Language("mariadb")
        var filter = """
                WHERE state IN (?, ?, ?)
                   OR id IN (SELECT offer_id FROM event_offer_chunks WHERE state = ?)
                ORDER BY id""";
        return query(OFFER + filter)
                .single(call().bind(OfferState.SCHEDULED.name())
                        .bind(OfferState.ACTIVE.name())
                        .bind(OfferState.PAUSED.name())
                        .bind(ChunkState.OPEN.name()))
                .map(this::parseOffer)
                .all();
    }

    @Override
    public boolean state(int offer, OfferState from, OfferState to, LocalDateTime at) {
        @Language("mariadb")
        var update = """
                UPDATE event_offers
                SET state  = ?,
                    closed = ?
                WHERE id = ?
                  AND state = ?""";
        return query(update)
                .single(call().bind(to.name())
                        .bind(to.closed() ? at : null, time())
                        .bind(offer)
                        .bind(from.name()))
                .update()
                .changed();
    }

    @Override
    public boolean chunkLimit(int offer, int open) {
        @Language("mariadb")
        var update = """
                UPDATE event_offers SET open_chunks = ? WHERE id = ?""";
        return query(update).single(call().bind(open).bind(offer)).update().changed();
    }

    @Override
    public boolean addChunk(int offer, int order, int amount, double paid, @Nullable String account, LocalDateTime at) {
        @Language("mariadb")
        var insert = """
                INSERT INTO event_offer_chunks(order_id, offer_id, amount, delivered, paid, account, state, placed)
                VALUES (?, ?, ?, 0, ?, ?, ?, ?)""";
        @Language("mariadb")
        var mark = """
                UPDATE orders SET offer_id = ? WHERE id = ?""";
        @Language("mariadb")
        var spend = """
                UPDATE event_offers SET spent = spent + ? WHERE id = ?""";
        var conf = QueryConfiguration.getDefault().withSingleTransaction();
        try (conf) {
            conf.query(insert)
                    .single(call().bind(order)
                            .bind(offer)
                            .bind(amount)
                            .bind(paid)
                            .bind(account)
                            .bind(ChunkState.OPEN.name())
                            .bind(at, time()))
                    .insert();
            conf.query(mark).single(call().bind(offer).bind(order)).update();
            conf.query(spend).single(call().bind(paid).bind(offer)).update();
        }
        return conf.context().exceptions().isEmpty();
    }

    @Override
    public List<OpenChunk> openChunks(int offer) {
        @Language("mariadb")
        var select = """
                SELECT c.order_id, c.amount, c.paid, c.account, s.state
                FROM event_offer_chunks c
                         LEFT JOIN order_states s ON s.id = c.order_id
                WHERE c.offer_id = ?
                  AND c.state = ?
                ORDER BY c.placed, c.order_id""";
        return query(select)
                .single(call().bind(offer).bind(ChunkState.OPEN.name()))
                .map(row -> new OpenChunk(
                        row.getInt("order_id"),
                        row.getInt("amount"),
                        row.getDouble("paid"),
                        row.getString("account"),
                        row.getObject("state") == null ? null : OrderState.byId(row.getInt("state"))))
                .all();
    }

    @Override
    public boolean chunkDelivered(int order, LocalDateTime at) {
        @Language("mariadb")
        var select = """
                SELECT c.offer_id,
                       c.amount,
                       c.paid,
                       s.state,
                       (SELECT COALESCE(SUM(d.amount), 0) FROM order_deliveries d WHERE d.order_id = c.order_id) AS items
                FROM event_offer_chunks c
                         JOIN order_states s ON s.id = c.order_id
                WHERE c.order_id = ?
                  AND c.state = ?""";
        @Language("mariadb")
        var settle = """
                UPDATE event_offer_chunks
                SET state       = ?,
                    delivered   = ?,
                    paid        = ?,
                    company     = (SELECT s.company FROM order_states s WHERE s.id = ?),
                    solo_worker = (SELECT s.solo_worker FROM order_states s WHERE s.id = ?),
                    ended       = ?
                WHERE order_id = ?
                  AND state = ?""";
        @Language("mariadb")
        var count = """
                UPDATE event_offers
                SET delivered = delivered + ?,
                    spent     = spent + ?
                WHERE id = ?""";
        var conf = QueryConfiguration.getDefault().withSingleTransaction();
        var settled = false;
        try (conf) {
            var found = conf.query(select)
                    .single(call().bind(order).bind(ChunkState.OPEN.name()))
                    .map(row -> new Settling(
                            row.getInt("offer_id"),
                            row.getInt("amount"),
                            row.getDouble("paid"),
                            row.getInt("state"),
                            row.getLong("items")))
                    .first();
            if (found.isEmpty() || found.get().state() < OrderState.DELIVERED.stateId()) return false;
            var chunk = found.get();
            var delivered = (int) Math.min(chunk.amount(), Math.max(0, chunk.items()));
            var kept = chunk.amount() <= 0 ? 0 : chunk.paid() * delivered / chunk.amount();
            var changed = conf.query(settle)
                    .single(call().bind(ChunkState.DELIVERED.name())
                            .bind(delivered)
                            .bind(kept)
                            .bind(order)
                            .bind(order)
                            .bind(at, time())
                            .bind(order)
                            .bind(ChunkState.OPEN.name()))
                    .update()
                    .changed();
            if (!changed) return false;
            conf.query(count)
                    .single(call().bind(delivered).bind(kept - chunk.paid()).bind(chunk.offer()))
                    .update();
            settled = true;
        }
        return settled && conf.context().exceptions().isEmpty();
    }

    @Override
    public boolean chunkReturned(int order, LocalDateTime at) {
        @Language("mariadb")
        var select = """
                SELECT offer_id, paid
                FROM event_offer_chunks
                WHERE order_id = ?
                  AND state = ?
                  AND NOT EXISTS (SELECT 1 FROM orders o WHERE o.id = ?)""";
        @Language("mariadb")
        var settle = """
                UPDATE event_offer_chunks
                SET state = ?,
                    ended = ?
                WHERE order_id = ?
                  AND state = ?""";
        @Language("mariadb")
        var refund = """
                UPDATE event_offers SET spent = spent - ? WHERE id = ?""";
        var conf = QueryConfiguration.getDefault().withSingleTransaction();
        var settled = false;
        try (conf) {
            var found = conf.query(select)
                    .single(call().bind(order).bind(ChunkState.OPEN.name()).bind(order))
                    .map(row -> Map.entry(row.getInt("offer_id"), row.getDouble("paid")))
                    .first();
            if (found.isEmpty()) return false;
            var changed = conf.query(settle)
                    .single(call().bind(ChunkState.RETURNED.name())
                            .bind(at, time())
                            .bind(order)
                            .bind(ChunkState.OPEN.name()))
                    .update()
                    .changed();
            if (!changed) return false;
            conf.query(refund)
                    .single(call().bind(found.get().getValue()).bind(found.get().getKey()))
                    .update();
            settled = true;
        }
        return settled && conf.context().exceptions().isEmpty();
    }

    @Override
    public List<Contributor> contributors(int offer, int limit) {
        @Language("mariadb")
        var select = """
                SELECT COALESCE(c.company, 0) AS company_id,
                       co.name                AS company_name,
                       c.solo_worker,
                       SUM(c.delivered)       AS total,
                       COUNT(1)               AS chunks
                FROM event_offer_chunks c
                         LEFT JOIN companies co ON co.id = c.company
                WHERE c.offer_id = ?
                  AND c.state = ?
                  AND c.delivered > 0
                GROUP BY COALESCE(c.company, 0), co.name, c.solo_worker
                ORDER BY total DESC, company_id
                LIMIT ?""";
        return query(select)
                .single(call().bind(offer).bind(ChunkState.DELIVERED.name()).bind(limit))
                .map(row -> new Contributor(
                        row.getInt("company_id"),
                        row.getString("company_name"),
                        row.get("solo_worker", UUID_BYTES),
                        (int) row.getLong("total"),
                        (int) row.getLong("chunks")))
                .all();
    }

    @Override
    public Map<Integer, OfferMark> marks(Collection<Integer> orders) {
        if (orders.isEmpty()) return Map.of();
        var ids = new ArrayList<>(orders);
        var marks = new HashMap<Integer, OfferMark>();
        for (var from = 0; from < ids.size(); from += MARKS_PER_QUERY) {
            var part = ids.subList(from, Math.min(ids.size(), from + MARKS_PER_QUERY));
            var select = """
                    SELECT o.id AS order_id, e.id, e.name, e.ends, e.state, e.amount, e.delivered
                    FROM orders o
                             JOIN event_offers e ON e.id = o.offer_id
                    WHERE o.id IN (%s)""".formatted(String.join(", ", Collections.nCopies(part.size(), "?")));
            Call call = call();
            for (var id : part) call = call.bind(id);
            query(select)
                    .single(call)
                    .map(row -> Map.entry(
                            row.getInt("order_id"),
                            new OfferMark(
                                    row.getInt("id"),
                                    row.getString("name"),
                                    readTime(row, "ends"),
                                    OfferState.valueOf(row.getString("state")),
                                    row.getInt("amount"),
                                    row.getInt("delivered"))))
                    .all()
                    .forEach(entry -> marks.put(entry.getKey(), entry.getValue()));
        }
        return marks;
    }

    private EventOffer parseOffer(Row row) throws SQLException {
        var resolve = row.getString("resolve");
        var line = new OfferLine(
                row.getString("material"),
                row.getString("criteria"),
                resolve == null ? null : RuleEntry.Resolve.valueOf(resolve),
                toItem(row.getString("item")),
                row.getString("label"));
        return new EventOffer(
                row.getInt("id"),
                OfferKind.valueOf(row.getString("kind")),
                row.getString("name"),
                row.getObject("event_id") == null ? null : row.getInt("event_id"),
                line,
                row.getInt("amount"),
                row.getDouble("unit_price"),
                row.getInt("chunk_size"),
                row.getInt("open_chunks"),
                MoneySource.SourceType.valueOf(row.getString("source")),
                row.getString("account"),
                row.getDouble("budget"),
                row.getDouble("spent"),
                row.getInt("delivered"),
                readTime(row, "starts"),
                readTime(row, "ends"),
                OfferState.valueOf(row.getString("state")),
                row.getString("created_by"),
                readTime(row, "created"),
                row.getObject("closed") == null ? null : readTime(row, "closed"));
    }

    /**
     * An open chunk being settled as delivered.
     *
     * @param offer  id of its offer
     * @param amount items it asks for
     * @param paid   what was put into it
     * @param state  id of its order's state
     * @param items  items delivered to its order
     */
    private record Settling(int offer, int amount, double paid, int state, long items) {}
}
