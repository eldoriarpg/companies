/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.mariadb;

import de.chojo.sadu.mapper.wrapper.Row;
import de.chojo.sadu.queries.api.call.adapter.Adapter;
import de.eldoria.companies.data.repository.AHomeData;
import de.eldoria.companies.data.repository.impl.TimeColumns;
import de.eldoria.companies.data.repository.impl.Transactions;
import de.eldoria.companies.data.wrapper.area.CompanyHome;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_BYTES;

/**
 * Company homes for MariaDB. PostgreSQL and SQLite run the same statements.
 */
public class MariaDbHomeData extends AHomeData {
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
    public Optional<CompanyHome> home(int company) {
        @Language("mariadb")
        var select = """
                SELECT company_id, node, server, world, x, y, z, yaw, pitch, set_by, set_at
                FROM company_homes
                WHERE company_id = ?""";
        return query(select).single(call().bind(company)).map(this::parse).first();
    }

    @Override
    public boolean set(CompanyHome home) {
        @Language("mariadb")
        var insert = """
                INSERT INTO company_homes(company_id, node, server, world, x, y, z, yaw, pitch, set_by, set_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";
        var errors = new ArrayList<SQLException>();
        var conf = Transactions.open(errors);
        try (conf) {
            conf.query("DELETE FROM company_homes WHERE company_id = ?")
                    .single(call().bind(home.company()))
                    .delete();
            conf.query(insert)
                    .single(call().bind(home.company())
                            .bind(home.node(), UUID_BYTES)
                            .bind(home.server())
                            .bind(home.world())
                            .bind(home.x())
                            .bind(home.y())
                            .bind(home.z())
                            .bind(home.yaw())
                            .bind(home.pitch())
                            .bind(home.setBy(), UUID_BYTES)
                            .bind(home.setAt(), time()))
                    .insert();
            if (!errors.isEmpty()) {
                Transactions.rollback(conf, errors);
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean remove(int company) {
        return query("DELETE FROM company_homes WHERE company_id = ?")
                .single(call().bind(company))
                .delete()
                .changed();
    }

    @Override
    public boolean travelling(UUID player, int company, UUID node, LocalDateTime expires) {
        var errors = new ArrayList<SQLException>();
        var conf = Transactions.open(errors);
        try (conf) {
            conf.query("DELETE FROM home_teleports WHERE player_uuid = ?")
                    .single(call().bind(player, UUID_BYTES))
                    .delete();
            conf.query("INSERT INTO home_teleports(player_uuid, company_id, node, expires) VALUES (?, ?, ?, ?)")
                    .single(call().bind(player, UUID_BYTES)
                            .bind(company)
                            .bind(node, UUID_BYTES)
                            .bind(expires, time()))
                    .insert();
            if (!errors.isEmpty()) {
                Transactions.rollback(conf, errors);
                return false;
            }
        }
        return true;
    }

    @Override
    public OptionalInt arrived(UUID player, UUID node, LocalDateTime now) {
        @Language("mariadb")
        var select = """
                SELECT company_id
                FROM home_teleports
                WHERE player_uuid = ?
                  AND node = ?
                  AND expires > ?""";
        var company = query(select)
                .single(call().bind(player, UUID_BYTES).bind(node, UUID_BYTES).bind(now, time()))
                .map(row -> row.getInt("company_id"))
                .first();
        query("DELETE FROM home_teleports WHERE player_uuid = ? AND expires <= ?")
                .single(call().bind(player, UUID_BYTES).bind(now, time()))
                .delete();
        if (company.isEmpty()) return OptionalInt.empty();
        var taken = query("DELETE FROM home_teleports WHERE player_uuid = ? AND node = ?")
                .single(call().bind(player, UUID_BYTES).bind(node, UUID_BYTES))
                .delete()
                .changed();
        return taken ? OptionalInt.of(company.get()) : OptionalInt.empty();
    }

    @Override
    public void server(UUID node, String server) {
        query("UPDATE company_homes SET server = ? WHERE node = ?")
                .single(call().bind(server).bind(node, UUID_BYTES))
                .update();
    }

    private CompanyHome parse(Row row) throws SQLException {
        return new CompanyHome(
                row.getInt("company_id"),
                row.get("node", UUID_BYTES),
                row.getString("server"),
                row.getString("world"),
                row.getDouble("x"),
                row.getDouble("y"),
                row.getDouble("z"),
                row.getFloat("yaw"),
                row.getFloat("pitch"),
                row.get("set_by", UUID_BYTES),
                readTime(row, "set_at"));
    }
}
