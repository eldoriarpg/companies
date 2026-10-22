/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.sqlite;

import de.chojo.sadu.mapper.wrapper.Row;
import de.chojo.sadu.queries.api.call.adapter.Adapter;
import de.chojo.sadu.queries.api.configuration.ConnectedQueryConfiguration;
import de.eldoria.companies.data.repository.impl.postgres.PostgresMembershipData;
import org.intellij.lang.annotations.Language;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;

import static de.chojo.sadu.queries.api.call.Call.call;

/**
 * Invites, applications and joins for SQLite. Times are stored as text in the format of {@code CURRENT_TIMESTAMP}, so
 * they compare correctly as strings.
 */
public class SqLiteMembershipData extends PostgresMembershipData {
    private static final Adapter<LocalDateTime> TEXT_TIME =
            Adapter.create(PreparedStatement::setString, Types.VARCHAR, SqLiteAdapter::format);

    public SqLiteMembershipData(ExecutorService executor) {
        super(executor);
    }

    @Override
    protected Adapter<LocalDateTime> time() {
        return TEXT_TIME;
    }

    @Override
    protected LocalDateTime readTime(Row row, String column) throws SQLException {
        return SqLiteAdapter.getTimestamp(row, column);
    }

    /**
     * Takes the database's write lock by touching the company row. SQLite has no row locks; once a transaction has
     * written, other writers wait until it ends, which serialises every join.
     *
     * @param conf    open transaction
     * @param company id of the company
     * @return true when the company exists
     */
    @Override
    protected boolean lockCompany(ConnectedQueryConfiguration conf, int company) {
        @Language("sqlite")
        var query = """
                UPDATE companies SET join_mode = join_mode WHERE id = ?""";
        return conf.query(query).single(call().bind(company)).update().changed();
    }
}
