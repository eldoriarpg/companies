/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.mariadb;

import de.chojo.sadu.mapper.wrapper.Row;
import de.chojo.sadu.queries.api.call.adapter.Adapter;
import de.eldoria.companies.data.repository.AAreaData;
import de.eldoria.companies.data.repository.impl.TimeColumns;
import de.eldoria.companies.data.wrapper.area.AreaSource;
import de.eldoria.companies.data.wrapper.area.CompanyArea;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_BYTES;

/**
 * Company areas for MariaDB. PostgreSQL and SQLite run the same statements.
 */
public class MariaDbAreaData extends AAreaData {
    @Language("mariadb")
    private static final String AREA = """
            SELECT id, company_id, provider, ref, world, node, source, linked_by, price, rent_until, created
            FROM company_areas
            """;

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
    public Optional<Integer> add(CompanyArea area) {
        if (byRef(area.provider(), area.ref()).isPresent()) return Optional.empty();
        @Language("mariadb")
        var insert = """
                INSERT INTO company_areas(company_id, provider, ref, world, node, source, linked_by, price, rent_until,
                                          created)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id""";
        try {
            return query(insert)
                    .single(call().bind(area.company())
                            .bind(area.provider())
                            .bind(area.ref())
                            .bind(area.world())
                            .bind(area.node(), UUID_BYTES)
                            .bind(area.source().name())
                            .bind(area.linkedBy(), UUID_BYTES)
                            .bind(area.price())
                            .bind(area.rentUntil(), time())
                            .bind(area.created(), time()))
                    .map(row -> row.getInt(1))
                    .first();
        } catch (RuntimeException e) {
            if (byRef(area.provider(), area.ref()).isPresent()) return Optional.empty();
            throw e;
        }
    }

    @Override
    public Optional<CompanyArea> area(int id) {
        return query(AREA + "WHERE id = ?")
                .single(call().bind(id))
                .map(this::parse)
                .first();
    }

    @Override
    public Optional<CompanyArea> byRef(String provider, String ref) {
        return query(AREA + "WHERE provider = ? AND ref = ?")
                .single(call().bind(provider).bind(ref))
                .map(this::parse)
                .first();
    }

    @Override
    public List<CompanyArea> ofCompany(int company) {
        return query(AREA + "WHERE company_id = ? ORDER BY id")
                .single(call().bind(company))
                .map(this::parse)
                .all();
    }

    @Override
    public List<CompanyArea> ofNode(UUID node) {
        return query(AREA + "WHERE node = ? ORDER BY id")
                .single(call().bind(node, UUID_BYTES))
                .map(this::parse)
                .all();
    }

    @Override
    public List<CompanyArea> linkedBy(UUID player) {
        return query(AREA + "WHERE linked_by = ? ORDER BY id")
                .single(call().bind(player, UUID_BYTES))
                .map(this::parse)
                .all();
    }

    @Override
    public boolean remove(int id) {
        query("DELETE FROM company_area_builders WHERE area_id = ?")
                .single(call().bind(id))
                .delete();
        return query("DELETE FROM company_areas WHERE id = ?")
                .single(call().bind(id))
                .delete()
                .changed();
    }

    @Override
    public boolean rename(int id, String ref) {
        var area = area(id);
        if (area.isEmpty()) return false;
        var taken = byRef(area.get().provider(), ref);
        if (taken.isPresent()) return taken.get().id() == id;
        return query("UPDATE company_areas SET ref = ? WHERE id = ?")
                .single(call().bind(ref).bind(id))
                .update()
                .changed();
    }

    @Override
    public Set<UUID> builders(int area) {
        return new HashSet<>(query("SELECT player_uuid FROM company_area_builders WHERE area_id = ?")
                .single(call().bind(area))
                .map(row -> row.get("player_uuid", UUID_BYTES))
                .all());
    }

    @Override
    public void addBuilder(int area, UUID player) {
        if (builders(area).contains(player)) return;
        query("INSERT INTO company_area_builders(area_id, player_uuid) VALUES (?, ?)")
                .single(call().bind(area).bind(player, UUID_BYTES))
                .insert();
    }

    @Override
    public void removeBuilder(int area, UUID player) {
        query("DELETE FROM company_area_builders WHERE area_id = ? AND player_uuid = ?")
                .single(call().bind(area).bind(player, UUID_BYTES))
                .delete();
    }

    private CompanyArea parse(Row row) throws SQLException {
        return new CompanyArea(
                row.getInt("id"),
                row.getInt("company_id"),
                row.getString("provider"),
                row.getString("ref"),
                row.getString("world"),
                row.get("node", UUID_BYTES),
                AreaSource.valueOf(row.getString("source")),
                row.get("linked_by", UUID_BYTES),
                row.getDouble("price"),
                readTime(row, "rent_until"),
                readTime(row, "created"));
    }
}
