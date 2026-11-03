/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.sqlite;

import de.chojo.sadu.mapper.wrapper.Row;
import de.chojo.sadu.queries.api.call.adapter.Adapter;
import de.chojo.sadu.queries.api.configuration.ConnectedQueryConfiguration;
import de.eldoria.companies.components.job.JobState;
import de.eldoria.companies.data.repository.impl.TimeColumns;
import de.eldoria.companies.data.repository.impl.postgres.PostgresJobData;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.time.LocalDateTime;

import static de.chojo.sadu.queries.api.call.Call.call;

/**
 * Jobs for SQLite. Times are stored as text in the format of {@code CURRENT_TIMESTAMP}, so they compare correctly as
 * strings.
 */
public class SqLiteJobData extends PostgresJobData {
    @Override
    protected Adapter<LocalDateTime> time() {
        return TimeColumns.SQLITE_TEXT;
    }

    @Override
    protected @Nullable LocalDateTime readTime(Row row, String column) throws SQLException {
        return row.getString(column) == null ? null : SqLiteAdapter.getTimestamp(row, column);
    }

    /**
     * Takes the database's write lock by touching the draft's row. SQLite has no row locks; once a transaction has
     * written, other writers wait until it ends.
     *
     * @param conf         open transaction
     * @param job          id of the draft
     * @param suppliedOnly whether only a draft whose owner supplies the materials counts
     * @return true when the job is such a draft
     */
    @Override
    protected boolean lockDraft(ConnectedQueryConfiguration conf, int job, boolean suppliedOnly) {
        @Language("sqlite")
        var query = "UPDATE jobs SET supplied = supplied WHERE id = ? AND state = ?"
                + (suppliedOnly ? " AND supplied" : "");
        return conf.query(query)
                .single(call().bind(job).bind(JobState.DRAFT.name()))
                .update()
                .changed();
    }
}
