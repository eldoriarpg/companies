/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.mariadb;

import de.chojo.sadu.mapper.wrapper.Row;
import de.chojo.sadu.queries.api.call.adapter.Adapter;
import de.chojo.sadu.queries.api.configuration.ConnectedQueryConfiguration;
import de.eldoria.companies.components.company.TreasuryKind;
import de.eldoria.companies.data.repository.ATreasuryData;
import de.eldoria.companies.data.repository.impl.TimeColumns;
import de.eldoria.companies.data.repository.impl.Transactions;
import de.eldoria.companies.data.wrapper.treasury.Treasury;
import de.eldoria.companies.data.wrapper.treasury.TreasuryEntry;
import de.eldoria.companies.data.wrapper.treasury.TreasuryLogEntry;
import org.intellij.lang.annotations.Language;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_BYTES;

/**
 * Treasuries for MariaDB. The other dialects override the upserts and the binding of times.
 * <p>
 * MariaDB has no {@code UPDATE … RETURNING}, so every change reads the new balance with a select in the same
 * transaction, while the updated row is still locked.
 */
public class MariaDbTreasuryData extends ATreasuryData {
    @Language("mariadb")
    private static final String BALANCE = """
            SELECT balance FROM company_treasury WHERE company_id = ?""";

    public MariaDbTreasuryData(ExecutorService executor) {
        super(executor);
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
     * @return time
     * @throws SQLException when the column is missing
     */
    protected LocalDateTime readTime(Row row, String column) throws SQLException {
        return row.getTimestamp(column).toLocalDateTime();
    }

    /**
     * The upsert that adds cents to a balance, creating the row when it is missing.
     *
     * @return statement with the parameters company and cents
     */
    protected String creditUpsert() {
        @Language("mariadb")
        var query = """
                INSERT INTO company_treasury(company_id, balance)
                VALUES (?, ?)
                ON DUPLICATE KEY UPDATE balance = balance + VALUES(balance)""";
        return query;
    }

    /**
     * The upsert that sets the payout share, creating the row when it is missing.
     *
     * @return statement with the parameters company and percent
     */
    protected String shareUpsert() {
        @Language("mariadb")
        var query = """
                INSERT INTO company_treasury(company_id, payout_share)
                VALUES (?, ?)
                ON DUPLICATE KEY UPDATE payout_share = VALUES(payout_share)""";
        return query;
    }

    @Override
    protected Treasury getTreasury(int company) {
        @Language("mariadb")
        var query = """
                SELECT company_id, balance, payout_share FROM company_treasury WHERE company_id = ?""";
        return query(query)
                .single(call().bind(company))
                .map(row -> new Treasury(row.getInt("company_id"), row.getLong("balance"), row.getInt("payout_share")))
                .first()
                .orElseGet(() -> Treasury.empty(company));
    }

    @Override
    protected Optional<Long> credit(int company, long cents, TreasuryEntry entry) {
        var errors = new ArrayList<SQLException>();
        var conf = Transactions.open(errors);
        try (conf) {
            conf.query(creditUpsert()).single(call().bind(company).bind(cents)).insert();
            var balance = balance(conf, company);
            if (balance.isEmpty() || !errors.isEmpty()) return failed(conf, errors);
            log(conf, company, cents, balance.get(), entry);
            if (!errors.isEmpty()) return failed(conf, errors);
            return balance;
        }
    }

    @Override
    protected Optional<Long> debit(int company, long cents, TreasuryEntry entry) {
        @Language("mariadb")
        var take = """
                UPDATE company_treasury
                SET balance = balance - ?
                WHERE company_id = ?
                  AND balance >= ?""";
        var errors = new ArrayList<SQLException>();
        var conf = Transactions.open(errors);
        try (conf) {
            var taken = conf.query(take)
                    .single(call().bind(cents).bind(company).bind(cents))
                    .update()
                    .changed();
            if (!errors.isEmpty()) return failed(conf, errors);
            if (!taken) return Optional.empty();
            var balance = balance(conf, company);
            if (balance.isEmpty() || !errors.isEmpty()) return failed(conf, errors);
            log(conf, company, -cents, balance.get(), entry);
            if (!errors.isEmpty()) return failed(conf, errors);
            return balance;
        }
    }

    @Override
    protected boolean saveShare(int company, int percent, UUID actor, LocalDateTime now) {
        var errors = new ArrayList<SQLException>();
        var conf = Transactions.open(errors);
        try (conf) {
            conf.query(shareUpsert()).single(call().bind(company).bind(percent)).insert();
            var balance = balance(conf, company);
            if (balance.isEmpty() || !errors.isEmpty())
                return failed(conf, errors).isPresent();
            var entry = new TreasuryEntry(TreasuryKind.SHARE_CHANGED, actor, null, String.valueOf(percent), now);
            log(conf, company, 0, balance.get(), entry);
            if (!errors.isEmpty()) return failed(conf, errors).isPresent();
            return true;
        }
    }

    @Override
    protected Optional<Long> drain(int company, TreasuryEntry entry) {
        @Language("mariadb")
        var lock = """
                UPDATE company_treasury SET balance = balance WHERE company_id = ?""";
        @Language("mariadb")
        var empty = """
                UPDATE company_treasury SET balance = 0 WHERE company_id = ?""";
        var errors = new ArrayList<SQLException>();
        var conf = Transactions.open(errors);
        try (conf) {
            conf.query(lock).single(call().bind(company)).update();
            var balance = balance(conf, company);
            if (!errors.isEmpty()) return failed(conf, errors);
            var old = balance.orElse(0L);
            if (old == 0) return Optional.of(0L);
            conf.query(empty).single(call().bind(company)).update();
            log(conf, company, -old, 0, entry);
            if (!errors.isEmpty()) return failed(conf, errors);
            return Optional.of(old);
        }
    }

    @Override
    protected List<TreasuryLogEntry> getLog(int company, int page, int size) {
        @Language("mariadb")
        var query = """
                SELECT id, company_id, kind, amount, balance, actor_uuid, order_id, note, created
                FROM treasury_log
                WHERE company_id = ?
                ORDER BY created DESC, id DESC
                LIMIT ? OFFSET ?""";
        return query(query)
                .single(call().bind(company).bind(size).bind(Math.max(0, page - 1) * size))
                .map(this::parseLogEntry)
                .all()
                .stream()
                .flatMap(Optional::stream)
                .toList();
    }

    @Override
    protected int purgeLogBefore(LocalDateTime before) {
        @Language("mariadb")
        var query = """
                DELETE FROM treasury_log WHERE created < ?""";
        return query(query).single(call().bind(before, time())).delete().rows();
    }

    private Optional<Long> balance(ConnectedQueryConfiguration conf, int company) {
        return conf.query(BALANCE)
                .single(call().bind(company))
                .map(row -> row.getLong("balance"))
                .first();
    }

    private void log(ConnectedQueryConfiguration conf, int company, long amount, long balance, TreasuryEntry entry) {
        @Language("mariadb")
        var query = """
                INSERT INTO treasury_log(company_id, kind, amount, balance, actor_uuid, order_id, note, created)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)""";
        conf.query(query)
                .single(call().bind(company)
                        .bind(entry.kind().id())
                        .bind(amount)
                        .bind(balance)
                        .bind(entry.actor(), UUID_BYTES)
                        .bind(entry.order())
                        .bind(entry.note())
                        .bind(entry.created(), time()))
                .insert();
    }

    private static Optional<Long> failed(ConnectedQueryConfiguration conf, List<SQLException> errors) {
        Transactions.rollback(conf, errors);
        return Optional.empty();
    }

    private Optional<TreasuryLogEntry> parseLogEntry(Row row) throws SQLException {
        var kind = TreasuryKind.byId(row.getInt("kind"));
        if (kind.isEmpty()) return Optional.empty();
        var order = row.getObject("order_id") instanceof Number number ? number.intValue() : null;
        return Optional.of(new TreasuryLogEntry(
                row.getLong("id"),
                row.getInt("company_id"),
                kind.get(),
                row.getLong("amount"),
                row.getLong("balance"),
                row.get("actor_uuid", UUID_BYTES),
                order,
                row.getString("note"),
                readTime(row, "created")));
    }
}
