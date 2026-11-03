/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.mariadb;

import de.chojo.sadu.mapper.wrapper.Row;
import de.chojo.sadu.queries.api.call.adapter.Adapter;
import de.eldoria.companies.data.repository.APerkData;
import de.eldoria.companies.data.repository.impl.TimeColumns;
import de.eldoria.companies.data.wrapper.perk.OwnedPerk;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The perks of companies for MariaDB, and the base of the other dialects.
 */
public class MariaDbPerkData extends APerkData {
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
        return row.getObject(column, LocalDateTime.class);
    }

    @Override
    public List<OwnedPerk> perks(int company) {
        return query("SELECT company_id, perk, bought FROM company_perks WHERE company_id = ? ORDER BY bought, perk")
                .single(call().bind(company))
                .map(row -> new OwnedPerk(row.getInt("company_id"), row.getString("perk"), readTime(row, "bought")))
                .all();
    }

    @Override
    public boolean add(int company, String perk, LocalDateTime bought) {
        if (owns(company, perk)) return false;
        try {
            return query("INSERT INTO company_perks(company_id, perk, bought) VALUES (?, ?, ?)")
                    .single(call().bind(company).bind(perk).bind(bought, time()))
                    .insert()
                    .changed();
        } catch (RuntimeException e) {
            if (owns(company, perk)) return false;
            throw e;
        }
    }

    @Override
    public boolean remove(int company, String perk) {
        return query("DELETE FROM company_perks WHERE company_id = ? AND perk = ?")
                .single(call().bind(company).bind(perk))
                .delete()
                .changed();
    }

    private boolean owns(int company, String perk) {
        return query("SELECT 1 FROM company_perks WHERE company_id = ? AND perk = ?")
                .single(call().bind(company).bind(perk))
                .map(row -> true)
                .first()
                .isPresent();
    }
}
