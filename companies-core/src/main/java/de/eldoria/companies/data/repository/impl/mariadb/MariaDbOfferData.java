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
import de.eldoria.companies.data.wrapper.offer.ContestDelivery;
import de.eldoria.companies.data.wrapper.offer.ContestEntry;
import de.eldoria.companies.data.wrapper.offer.ContestPrize;
import de.eldoria.companies.data.wrapper.offer.Contributor;
import de.eldoria.companies.data.wrapper.offer.EventOffer;
import de.eldoria.companies.data.wrapper.offer.OfferKind;
import de.eldoria.companies.data.wrapper.offer.OfferLine;
import de.eldoria.companies.data.wrapper.offer.OfferMark;
import de.eldoria.companies.data.wrapper.offer.OfferState;
import de.eldoria.companies.data.wrapper.offer.OpenChunk;
import de.eldoria.companies.data.wrapper.offer.PrizeState;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nullable;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_BYTES;

/**
 * Event offers for MariaDB. PostgreSQL and SQLite run the same statements. A delivery into a contest reads the contest,
 * then changes it only while its delivered items are still what it read; a delivery that lost a race reads again.
 */
public class MariaDbOfferData extends AOfferData {
    private static final int MARKS_PER_QUERY = 500;
    private static final int DELIVERY_ATTEMPTS = 8;

    @Language("mariadb")
    private static final String OFFER = """
            SELECT id, kind, name, event_id, material, criteria, resolve, item, label, amount, unit_price, chunk_size,
                   open_chunks, source, account, budget, spent, delivered, starts, ends, state, created_by, created,
                   closed, prizes, held, started, settled, refund, refunded
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
                                         delivered, starts, ends, state, created_by, created, prizes, held)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, ?, ?, ?, ?, ?, ?, 0)
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
                        .bind(offer.created(), time())
                        .bind(prizesText(offer.prizes())))
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
                   OR (kind = ? AND refunded IS NULL)
                ORDER BY id""";
        return query(OFFER + filter)
                .single(call().bind(OfferState.SCHEDULED.name())
                        .bind(OfferState.ACTIVE.name())
                        .bind(OfferState.PAUSED.name())
                        .bind(ChunkState.OPEN.name())
                        .bind(OfferKind.CONTEST.name()))
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
                row.getObject("closed") == null ? null : readTime(row, "closed"),
                prizes(row.getString("prizes")),
                row.getDouble("held"),
                row.getObject("started") == null ? null : readTime(row, "started"),
                row.getObject("settled") == null ? null : readTime(row, "settled"),
                row.getObject("refund") == null ? null : row.getDouble("refund"),
                row.getObject("refunded") == null ? null : readTime(row, "refunded"));
    }

    /**
     * @param prizes the money of the places
     * @return them as stored, null for none
     */
    private static @Nullable String prizesText(List<Double> prizes) {
        if (prizes.isEmpty()) return null;
        return prizes.stream()
                .map(prize -> BigDecimal.valueOf(prize).stripTrailingZeros().toPlainString())
                .collect(Collectors.joining(","));
    }

    /**
     * @param text the money of the places as stored, may be null
     * @return the money of the places
     */
    private static List<Double> prizes(@Nullable String text) {
        if (text == null || text.isBlank()) return List.of();
        return Arrays.stream(text.split(","))
                .map(String::trim)
                .map(Double::valueOf)
                .toList();
    }

    @Override
    public List<EventOffer> running() {
        return query(OFFER + "WHERE state IN (?, ?, ?) ORDER BY ends, id")
                .single(call().bind(OfferState.SCHEDULED.name())
                        .bind(OfferState.ACTIVE.name())
                        .bind(OfferState.PAUSED.name()))
                .map(this::parseOffer)
                .all();
    }

    @Override
    public boolean progressPosted(int offer, int threshold) {
        @Language("mariadb")
        var update = """
                UPDATE event_offers
                SET posted_progress = ?
                WHERE id = ?
                  AND posted_progress < ?""";
        return query(update)
                .single(call().bind(threshold).bind(offer).bind(threshold))
                .update()
                .changed();
    }

    @Override
    public int unclaimedChunks(int offer) {
        @Language("mariadb")
        var select = """
                SELECT COUNT(1) AS chunks
                FROM orders o
                         JOIN order_states s ON s.id = o.id
                WHERE o.offer_id IS NOT NULL
                  AND (? = 0 OR o.offer_id = ?)
                  AND s.state = ?""";
        return query(select)
                .single(call().bind(offer).bind(offer).bind(OrderState.UNCLAIMED.stateId()))
                .map(row -> row.getInt("chunks"))
                .first()
                .orElse(0);
    }

    @Override
    public List<EventOffer> contests() {
        return query(OFFER + "WHERE kind = ? AND state IN (?, ?, ?) ORDER BY id")
                .single(call().bind(OfferKind.CONTEST.name())
                        .bind(OfferState.SCHEDULED.name())
                        .bind(OfferState.ACTIVE.name())
                        .bind(OfferState.PAUSED.name()))
                .map(this::parseOffer)
                .all();
    }

    @Override
    public boolean join(int offer, int company, LocalDateTime at) {
        var found = offer(offer);
        if (found.isEmpty()
                || found.get().kind() != OfferKind.CONTEST
                || found.get().state().closed()) return false;
        @Language("mariadb")
        var rejoin = """
                UPDATE contest_entries
                SET left_at   = NULL,
                    joined_at = ?
                WHERE offer_id = ?
                  AND company_id = ?
                  AND left_at IS NOT NULL""";
        if (query(rejoin)
                .single(call().bind(at, time()).bind(offer).bind(company))
                .update()
                .changed()) {
            return true;
        }
        if (entry(offer, company).isPresent()) return false;
        @Language("mariadb")
        var insert = """
                INSERT INTO contest_entries(offer_id, company_id, delivered, paid, joined_at)
                VALUES (?, ?, 0, 0, ?)""";
        var conf = QueryConfiguration.getDefault().withSingleTransaction();
        try (conf) {
            conf.query(insert)
                    .single(call().bind(offer).bind(company).bind(at, time()))
                    .insert();
        }
        return conf.context().exceptions().isEmpty();
    }

    @Override
    public boolean leave(int offer, int company, LocalDateTime at) {
        @Language("mariadb")
        var update = """
                UPDATE contest_entries
                SET left_at = ?
                WHERE offer_id = ?
                  AND company_id = ?
                  AND left_at IS NULL""";
        return query(update)
                .single(call().bind(at, time()).bind(offer).bind(company))
                .update()
                .changed();
    }

    @Language("mariadb")
    private static final String ENTRY = """
            SELECT e.offer_id, e.company_id, c.name, e.delivered, e.paid, e.joined_at, e.left_at, e.last_delivery
            FROM contest_entries e
                     LEFT JOIN companies c ON c.id = e.company_id
            """;

    @Override
    public Optional<ContestEntry> entry(int offer, int company) {
        return query(ENTRY + "WHERE e.offer_id = ? AND e.company_id = ?")
                .single(call().bind(offer).bind(company))
                .map(this::parseEntry)
                .first();
    }

    @Override
    public List<Integer> joined(int company) {
        @Language("mariadb")
        var select = """
                SELECT e.offer_id
                FROM contest_entries e
                         JOIN event_offers o ON o.id = e.offer_id
                WHERE e.company_id = ?
                  AND e.left_at IS NULL
                  AND o.state IN (?, ?, ?)
                ORDER BY e.offer_id""";
        return query(select)
                .single(call().bind(company)
                        .bind(OfferState.SCHEDULED.name())
                        .bind(OfferState.ACTIVE.name())
                        .bind(OfferState.PAUSED.name()))
                .map(row -> row.getInt("offer_id"))
                .all();
    }

    @Override
    public List<ContestEntry> ranking(int offer) {
        // A company that never delivered sorts after every one that did, whatever the dialect does with nulls.
        return query(ENTRY + """
                        WHERE e.offer_id = ?
                        ORDER BY e.delivered DESC,
                                 CASE WHEN e.last_delivery IS NULL THEN 1 ELSE 0 END,
                                 e.last_delivery,
                                 e.joined_at,
                                 e.company_id""")
                .single(call().bind(offer))
                .map(this::parseEntry)
                .all();
    }

    private ContestEntry parseEntry(Row row) throws SQLException {
        return new ContestEntry(
                row.getInt("offer_id"),
                row.getInt("company_id"),
                row.getString("name"),
                row.getInt("delivered"),
                row.getDouble("paid"),
                readTime(row, "joined_at"),
                row.getObject("left_at") == null ? null : readTime(row, "left_at"),
                row.getObject("last_delivery") == null ? null : readTime(row, "last_delivery"));
    }

    @Override
    public ContestDelivery deliver(int offer, int company, UUID worker, int offered, LocalDateTime at) {
        @Language("mariadb")
        var read = """
                SELECT o.kind, o.state, o.amount, o.delivered, o.spent, o.budget, o.unit_price, o.prizes,
                       e.company_id AS entered, e.left_at
                FROM event_offers o
                         LEFT JOIN contest_entries e ON e.offer_id = o.id AND e.company_id = ?
                WHERE o.id = ?""";
        @Language("mariadb")
        var take = """
                UPDATE event_offers
                SET delivered = ?,
                    spent     = ?,
                    state     = ?,
                    closed    = ?
                WHERE id = ?
                  AND state = ?
                  AND delivered = ?""";
        @Language("mariadb")
        var credit = """
                UPDATE contest_entries
                SET delivered     = delivered + ?,
                    paid          = paid + ?,
                    last_delivery = ?
                WHERE offer_id = ?
                  AND company_id = ?""";
        @Language("mariadb")
        var record = """
                INSERT INTO contest_deliveries(offer_id, company_id, worker, items, gross, created)
                VALUES (?, ?, ?, ?, ?, ?)""";
        for (var attempt = 0; attempt < DELIVERY_ATTEMPTS; attempt++) {
            var found = query(read)
                    .single(call().bind(company).bind(offer))
                    .map(row -> new Reading(
                            OfferKind.valueOf(row.getString("kind")),
                            OfferState.valueOf(row.getString("state")),
                            row.getInt("amount"),
                            row.getInt("delivered"),
                            row.getDouble("spent"),
                            row.getDouble("budget"),
                            row.getDouble("unit_price"),
                            prizes(row.getString("prizes")),
                            row.getObject("entered") != null && row.getObject("left_at") == null))
                    .first();
            if (found.isEmpty() || found.get().kind() != OfferKind.CONTEST) {
                return ContestDelivery.refused(ContestDelivery.Outcome.UNKNOWN, OfferState.CANCELLED);
            }
            var now = found.get();
            if (now.state() != OfferState.ACTIVE)
                return ContestDelivery.refused(ContestDelivery.Outcome.NOT_ACTIVE, now.state());
            if (!now.entered()) return ContestDelivery.refused(ContestDelivery.Outcome.NOT_JOINED, now.state());
            var prizeTotal =
                    now.prizes().stream().mapToDouble(Double::doubleValue).sum();
            var money = now.budget() - prizeTotal - now.spent();
            var items = EventOffer.affordableAfter(
                    now.unitPrice(),
                    now.delivered(),
                    Math.min(offered, Math.max(0, now.amount() - now.delivered())),
                    money);
            if (items <= 0) return ContestDelivery.refused(ContestDelivery.Outcome.FULL, now.state());
            var gross = EventOffer.marginal(now.unitPrice(), now.delivered(), items);
            var delivered = now.delivered() + items;
            var next = OfferState.ACTIVE;
            if (delivered >= now.amount()) {
                next = OfferState.REACHED;
            } else if (EventOffer.affordableAfter(now.unitPrice(), delivered, 1, money - gross) <= 0) {
                next = OfferState.DEPLETED;
            }
            var conf = QueryConfiguration.getDefault().withSingleTransaction();
            var taken = false;
            try (conf) {
                taken = conf.query(take)
                        .single(call().bind(delivered)
                                .bind(now.spent() + gross)
                                .bind(next.name())
                                .bind(next.closed() ? at : null, time())
                                .bind(offer)
                                .bind(OfferState.ACTIVE.name())
                                .bind(now.delivered()))
                        .update()
                        .changed();
                if (taken) {
                    conf.query(credit)
                            .single(call().bind(items)
                                    .bind(gross)
                                    .bind(at, time())
                                    .bind(offer)
                                    .bind(company))
                            .update();
                    conf.query(record)
                            .single(call().bind(offer)
                                    .bind(company)
                                    .bind(worker, UUID_BYTES)
                                    .bind(items)
                                    .bind(gross)
                                    .bind(at, time()))
                            .insert();
                }
            }
            if (taken && conf.context().exceptions().isEmpty()) {
                return new ContestDelivery(ContestDelivery.Outcome.DELIVERED, items, gross, next);
            }
        }
        return ContestDelivery.refused(ContestDelivery.Outcome.BUSY, OfferState.ACTIVE);
    }

    @Override
    public boolean activate(int offer, double held, LocalDateTime at) {
        @Language("mariadb")
        var update = """
                UPDATE event_offers
                SET state   = ?,
                    held    = ?,
                    started = ?
                WHERE id = ?
                  AND state = ?
                  AND started IS NULL""";
        return query(update)
                .single(call().bind(OfferState.ACTIVE.name())
                        .bind(held)
                        .bind(at, time())
                        .bind(offer)
                        .bind(OfferState.SCHEDULED.name()))
                .update()
                .changed();
    }

    @Override
    public boolean settle(int offer, List<ContestPrize> prizes, LocalDateTime at) {
        @Language("mariadb")
        var claim = """
                UPDATE event_offers
                SET settled = ?
                WHERE id = ?
                  AND settled IS NULL
                  AND state NOT IN (?, ?, ?)""";
        @Language("mariadb")
        var insert = """
                INSERT INTO contest_prizes(offer_id, place, company_id, amount, state, settled)
                VALUES (?, ?, ?, ?, ?, ?)""";
        @Language("mariadb")
        var reserve = """
                UPDATE event_offers SET spent = spent + ? WHERE id = ?""";
        var conf = QueryConfiguration.getDefault().withSingleTransaction();
        var claimed = false;
        try (conf) {
            claimed = conf.query(claim)
                    .single(call().bind(at, time())
                            .bind(offer)
                            .bind(OfferState.SCHEDULED.name())
                            .bind(OfferState.ACTIVE.name())
                            .bind(OfferState.PAUSED.name()))
                    .update()
                    .changed();
            if (claimed && !prizes.isEmpty()) {
                conf.query(insert)
                        .batch(prizes.stream().map(prize -> call().bind(offer)
                                .bind(prize.place())
                                .bind(prize.company())
                                .bind(prize.amount())
                                .bind(prize.state().name())
                                .bind(at, time())))
                        .insert();
                var reserved = prizes.stream()
                        .filter(prize -> prize.state() == PrizeState.PENDING)
                        .mapToDouble(ContestPrize::amount)
                        .sum();
                if (reserved > 0)
                    conf.query(reserve)
                            .single(call().bind(reserved).bind(offer))
                            .update();
            }
        }
        return claimed && conf.context().exceptions().isEmpty();
    }

    @Override
    public boolean claimPrize(int offer, int place) {
        @Language("mariadb")
        var update = """
                UPDATE contest_prizes
                SET state = ?
                WHERE offer_id = ?
                  AND place = ?
                  AND state = ?""";
        return query(update)
                .single(call().bind(PrizeState.PAYING.name())
                        .bind(offer)
                        .bind(place)
                        .bind(PrizeState.PENDING.name()))
                .update()
                .changed();
    }

    @Override
    public boolean prizeOutcome(int offer, int place, boolean paid) {
        @Language("mariadb")
        var note = """
                UPDATE contest_prizes
                SET state = ?
                WHERE offer_id = ?
                  AND place = ?
                  AND state = ?""";
        @Language("mariadb")
        var release = """
                UPDATE event_offers
                SET spent = spent - (SELECT amount FROM contest_prizes WHERE offer_id = ? AND place = ?)
                WHERE id = ?""";
        var conf = QueryConfiguration.getDefault().withSingleTransaction();
        var noted = false;
        try (conf) {
            noted = conf.query(note)
                    .single(call().bind((paid ? PrizeState.PAID : PrizeState.FAILED).name())
                            .bind(offer)
                            .bind(place)
                            .bind(PrizeState.PAYING.name()))
                    .update()
                    .changed();
            if (noted && !paid) {
                conf.query(release)
                        .single(call().bind(offer).bind(place).bind(offer))
                        .update();
            }
        }
        return noted && conf.context().exceptions().isEmpty();
    }

    @Override
    public boolean refund(int offer, double amount, LocalDateTime at) {
        @Language("mariadb")
        var update = """
                UPDATE event_offers
                SET refund   = ?,
                    refunded = ?
                WHERE id = ?
                  AND settled IS NOT NULL
                  AND refunded IS NULL""";
        return query(update)
                .single(call().bind(amount).bind(at, time()).bind(offer))
                .update()
                .changed();
    }

    @Override
    public List<ContestPrize> prizes(int offer) {
        @Language("mariadb")
        var select = """
                SELECT p.place, p.amount, p.company_id, c.name, p.state
                FROM contest_prizes p
                         LEFT JOIN companies c ON c.id = p.company_id
                WHERE p.offer_id = ?
                ORDER BY p.place""";
        return query(select)
                .single(call().bind(offer))
                .map(row -> new ContestPrize(
                        row.getInt("place"),
                        row.getDouble("amount"),
                        row.getObject("company_id") == null ? null : row.getInt("company_id"),
                        row.getString("name"),
                        PrizeState.valueOf(row.getString("state"))))
                .all();
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

    /**
     * A contest as a delivery reads it before taking items.
     *
     * @param kind      kind of the offer
     * @param state     state of the offer
     * @param amount    items asked for
     * @param delivered items delivered, the version the delivery changes
     * @param spent     money paid out
     * @param budget    budget of the contest
     * @param unitPrice price per item
     * @param prizes    money of the places, kept back
     * @param entered   whether the company takes part
     */
    private record Reading(
            OfferKind kind,
            OfferState state,
            int amount,
            int delivered,
            double spent,
            double budget,
            double unitPrice,
            List<Double> prizes,
            boolean entered) {}
}
