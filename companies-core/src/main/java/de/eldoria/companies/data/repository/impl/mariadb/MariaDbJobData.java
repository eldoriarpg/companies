/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.mariadb;

import de.chojo.sadu.mapper.wrapper.Row;
import de.chojo.sadu.queries.api.call.adapter.Adapter;
import de.eldoria.companies.data.repository.AJobData;
import de.eldoria.companies.data.repository.impl.TimeColumns;
import de.eldoria.companies.data.repository.impl.Transactions;
import de.eldoria.companies.data.wrapper.job.Job;
import de.eldoria.companies.data.wrapper.job.JobArea;
import de.eldoria.companies.data.wrapper.job.JobKind;
import de.eldoria.companies.data.wrapper.job.JobLand;
import de.eldoria.companies.data.wrapper.job.JobReview;
import de.eldoria.companies.data.wrapper.job.JobState;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_BYTES;

/**
 * Jobs for MariaDB. PostgreSQL and SQLite run the same statements.
 */
public class MariaDbJobData extends AJobData {
    @Language("mariadb")
    private static final String JOB = """
            SELECT id, owner_uuid, kind, title, description, price, fee, state, company_id, node, world, min_x, min_y,
                   min_z, max_x, max_y, max_z, land, land_ref, due_hours, created, expires, taken, due, closed,
                   closed_by, granted, done_at, done_by, rejected, dispute_by, dispute, disputed_at, settled
            FROM jobs
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
    public Optional<Integer> create(Job job) {
        @Language("mariadb")
        var insert = """
                INSERT INTO jobs(owner_uuid, kind, title, description, price, fee, state, node, world, min_x, min_y,
                                 min_z, max_x, max_y, max_z, land, land_ref, due_hours, created, expires)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id""";
        var area = job.area();
        return query(insert)
                .single(call().bind(job.owner(), UUID_BYTES)
                        .bind(job.kind().name())
                        .bind(job.title())
                        .bind(job.description())
                        .bind(job.price())
                        .bind(job.fee())
                        .bind(JobState.OPEN.name())
                        .bind(job.node(), UUID_BYTES)
                        .bind(area.world())
                        .bind(area.minX())
                        .bind(area.minY())
                        .bind(area.minZ())
                        .bind(area.maxX())
                        .bind(area.maxY())
                        .bind(area.maxZ())
                        .bind(job.land().name())
                        .bind(job.landRef())
                        .bind(job.dueHours())
                        .bind(job.created(), time())
                        .bind(job.expires(), time()))
                .map(row -> row.getInt(1))
                .first();
    }

    @Override
    public Optional<Job> job(int id) {
        return query(JOB + "WHERE id = ?")
                .single(call().bind(id))
                .map(this::parse)
                .first();
    }

    @Override
    public List<Job> open(LocalDateTime now, int limit) {
        return query(JOB + "WHERE state = ? AND expires > ? ORDER BY id DESC LIMIT ?")
                .single(call().bind(JobState.OPEN.name()).bind(now, time()).bind(limit))
                .map(this::parse)
                .all();
    }

    @Override
    public List<Job> ofOwner(UUID owner, int limit) {
        return query(JOB + "WHERE owner_uuid = ? ORDER BY id DESC LIMIT ?")
                .single(call().bind(owner, UUID_BYTES).bind(limit))
                .map(this::parse)
                .all();
    }

    @Override
    public List<Job> ofCompany(int company) {
        return query(JOB + "WHERE company_id = ? AND state IN (?, ?, ?) ORDER BY due, id")
                .single(call().bind(company)
                        .bind(JobState.TAKEN.name())
                        .bind(JobState.DONE.name())
                        .bind(JobState.DISPUTED.name()))
                .map(this::parse)
                .all();
    }

    @Override
    public boolean take(int job, int company, LocalDateTime at, LocalDateTime due) {
        @Language("mariadb")
        var update = """
                UPDATE jobs
                SET state      = ?,
                    company_id = ?,
                    taken      = ?,
                    due        = ?
                WHERE id = ?
                  AND state = ?
                  AND expires > ?""";
        return query(update)
                .single(call().bind(JobState.TAKEN.name())
                        .bind(company)
                        .bind(at, time())
                        .bind(due, time())
                        .bind(job)
                        .bind(JobState.OPEN.name())
                        .bind(at, time()))
                .update()
                .changed();
    }

    @Override
    public boolean release(int job, int company) {
        @Language("mariadb")
        var update = """
                UPDATE jobs
                SET state      = ?,
                    company_id = NULL,
                    taken      = NULL,
                    due        = NULL,
                    done_at    = NULL,
                    done_by    = NULL,
                    rejected   = NULL
                WHERE id = ?
                  AND state IN (?, ?)
                  AND company_id = ?""";
        @Language("mariadb")
        var forget = "DELETE FROM job_work WHERE job_id = ?";
        var errors = new ArrayList<SQLException>();
        var conf = Transactions.open(errors);
        try (conf) {
            var released = conf.query(update)
                    .single(call().bind(JobState.OPEN.name())
                            .bind(job)
                            .bind(JobState.TAKEN.name())
                            .bind(JobState.DONE.name())
                            .bind(company))
                    .update()
                    .changed();
            if (!released || !errors.isEmpty()) {
                Transactions.rollback(conf, errors);
                return false;
            }
            conf.query(forget).single(call().bind(job)).delete();
            if (!errors.isEmpty()) {
                Transactions.rollback(conf, errors);
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean describe(int job, @Nullable String description) {
        return query("UPDATE jobs SET description = ? WHERE id = ? AND state = ?")
                .single(call().bind(description).bind(job).bind(JobState.OPEN.name()))
                .update()
                .changed();
    }

    @Override
    public boolean markDone(int job, int company, UUID by, LocalDateTime at) {
        @Language("mariadb")
        var update = """
                UPDATE jobs
                SET state   = ?,
                    done_at = ?,
                    done_by = ?
                WHERE id = ?
                  AND state = ?
                  AND company_id = ?""";
        return query(update)
                .single(call().bind(JobState.DONE.name())
                        .bind(at, time())
                        .bind(by, UUID_BYTES)
                        .bind(job)
                        .bind(JobState.TAKEN.name())
                        .bind(company))
                .update()
                .changed();
    }

    @Override
    public boolean reject(int job, String reason, LocalDateTime due) {
        @Language("mariadb")
        var update = """
                UPDATE jobs
                SET state    = ?,
                    done_at  = NULL,
                    done_by  = NULL,
                    rejected = ?,
                    due      = ?
                WHERE id = ?
                  AND state = ?""";
        return query(update)
                .single(call().bind(JobState.TAKEN.name())
                        .bind(reason)
                        .bind(due, time())
                        .bind(job)
                        .bind(JobState.DONE.name()))
                .update()
                .changed();
    }

    @Override
    public boolean dispute(int job, int company, JobState from, UUID by, String reason, LocalDateTime at) {
        @Language("mariadb")
        var update = """
                UPDATE jobs
                SET state       = ?,
                    dispute_by  = ?,
                    dispute     = ?,
                    disputed_at = ?
                WHERE id = ?
                  AND state = ?
                  AND company_id = ?""";
        return query(update)
                .single(call().bind(JobState.DISPUTED.name())
                        .bind(by, UUID_BYTES)
                        .bind(reason)
                        .bind(at, time())
                        .bind(job)
                        .bind(from.name())
                        .bind(company))
                .update()
                .changed();
    }

    @Override
    public boolean withdraw(int job, UUID by, LocalDateTime due, @Nullable LocalDateTime doneAt) {
        @Language("mariadb")
        var update = """
                UPDATE jobs
                SET state       = ?,
                    done_at     = ?,
                    dispute_by  = NULL,
                    dispute     = NULL,
                    disputed_at = NULL,
                    due         = ?
                WHERE id = ?
                  AND state = ?
                  AND dispute_by = ?""";
        return query(update)
                .single(call().bind((doneAt == null ? JobState.TAKEN : JobState.DONE).name())
                        .bind(doneAt, time())
                        .bind(due, time())
                        .bind(job)
                        .bind(JobState.DISPUTED.name())
                        .bind(by, UUID_BYTES))
                .update()
                .changed();
    }

    @Override
    public boolean settle(int job, int percent, String by, LocalDateTime at) {
        @Language("mariadb")
        var update = """
                UPDATE jobs
                SET state     = ?,
                    settled   = ?,
                    closed    = ?,
                    closed_by = ?
                WHERE id = ?
                  AND state = ?""";
        return query(update)
                .single(call().bind(JobState.SETTLED.name())
                        .bind(percent)
                        .bind(at, time())
                        .bind(by)
                        .bind(job)
                        .bind(JobState.DISPUTED.name()))
                .update()
                .changed();
    }

    @Override
    public List<Job> waiting(LocalDateTime before) {
        return query(JOB + "WHERE state = ? AND done_at <= ? ORDER BY id")
                .single(call().bind(JobState.DONE.name()).bind(before, time()))
                .map(this::parse)
                .all();
    }

    @Override
    public List<Job> disputed() {
        return query(JOB + "WHERE state = ? ORDER BY disputed_at, id")
                .single(call().bind(JobState.DISPUTED.name()))
                .map(this::parse)
                .all();
    }

    @Override
    public boolean close(int job, JobState from, JobState to, @Nullable String by, LocalDateTime at) {
        @Language("mariadb")
        var update = """
                UPDATE jobs
                SET state     = ?,
                    closed    = ?,
                    closed_by = ?
                WHERE id = ?
                  AND state = ?""";
        return query(update)
                .single(call().bind(to.name())
                        .bind(at, time())
                        .bind(by)
                        .bind(job)
                        .bind(from.name()))
                .update()
                .changed();
    }

    @Override
    public boolean closeHeld(int job, int company, JobState from, JobState to, @Nullable String by, LocalDateTime at) {
        @Language("mariadb")
        var update = """
                UPDATE jobs
                SET state     = ?,
                    closed    = ?,
                    closed_by = ?
                WHERE id = ?
                  AND state = ?
                  AND company_id = ?""";
        return query(update)
                .single(call().bind(to.name())
                        .bind(at, time())
                        .bind(by)
                        .bind(job)
                        .bind(from.name())
                        .bind(company))
                .update()
                .changed();
    }

    @Override
    public int openOf(UUID owner) {
        return query("SELECT COUNT(1) AS count FROM jobs WHERE owner_uuid = ? AND state = ?")
                .single(call().bind(owner, UUID_BYTES).bind(JobState.OPEN.name()))
                .map(row -> row.getInt("count"))
                .first()
                .orElse(0);
    }

    @Override
    public List<Job> expired(LocalDateTime now) {
        return query(JOB + "WHERE state = ? AND expires <= ? ORDER BY id")
                .single(call().bind(JobState.OPEN.name()).bind(now, time()))
                .map(this::parse)
                .all();
    }

    @Override
    public List<Job> overdue(LocalDateTime now) {
        return query(JOB + "WHERE state = ? AND due <= ? ORDER BY id")
                .single(call().bind(JobState.TAKEN.name()).bind(now, time()))
                .map(this::parse)
                .all();
    }

    @Override
    public List<Job> access(UUID node) {
        return query(JOB + "WHERE node = ? AND kind <> ? AND (state IN (?, ?, ?) OR granted) ORDER BY id")
                .single(call().bind(node, UUID_BYTES)
                        .bind(JobKind.CONTRACT.name())
                        .bind(JobState.OPEN.name())
                        .bind(JobState.TAKEN.name())
                        .bind(JobState.DISPUTED.name()))
                .map(this::parse)
                .all();
    }

    @Override
    public void granted(int job, boolean granted) {
        query("UPDATE jobs SET granted = ? WHERE id = ?")
                .single(call().bind(granted).bind(job))
                .update();
    }

    @Override
    public Set<UUID> grants(int job) {
        return new HashSet<>(query("SELECT player_uuid FROM job_grants WHERE job_id = ?")
                .single(call().bind(job))
                .map(row -> row.get("player_uuid", UUID_BYTES))
                .all());
    }

    @Override
    public boolean grants(int job, Set<UUID> players) {
        var errors = new ArrayList<SQLException>();
        var conf = Transactions.open(errors);
        try (conf) {
            conf.query("DELETE FROM job_grants WHERE job_id = ?")
                    .single(call().bind(job))
                    .delete();
            for (var player : players) {
                conf.query("INSERT INTO job_grants(job_id, player_uuid) VALUES (?, ?)")
                        .single(call().bind(job).bind(player, UUID_BYTES))
                        .insert();
            }
            if (!errors.isEmpty()) {
                Transactions.rollback(conf, errors);
                return false;
            }
        }
        return true;
    }

    @Override
    public void work(int job, int company, UUID worker, int blocks) {
        if (blocks <= 0) return;
        @Language("mariadb")
        var add = """
                UPDATE job_work
                SET blocks = blocks + ?
                WHERE job_id = ?
                  AND worker_uuid = ?
                  AND job_id IN (SELECT id FROM jobs WHERE id = ? AND state = ? AND company_id = ?)""";
        @Language("mariadb")
        var insert = """
                INSERT INTO job_work(job_id, worker_uuid, blocks)
                SELECT id, ?, ?
                FROM jobs
                WHERE id = ?
                  AND state = ?
                  AND company_id = ?""";
        var errors = new ArrayList<SQLException>();
        var conf = Transactions.open(errors);
        try (conf) {
            var added = conf.query(add)
                    .single(call().bind(blocks)
                            .bind(job)
                            .bind(worker, UUID_BYTES)
                            .bind(job)
                            .bind(JobState.TAKEN.name())
                            .bind(company))
                    .update()
                    .changed();
            if (!added && errors.isEmpty()) {
                conf.query(insert)
                        .single(call().bind(worker, UUID_BYTES)
                                .bind(blocks)
                                .bind(job)
                                .bind(JobState.TAKEN.name())
                                .bind(company))
                        .insert();
            }
            if (!errors.isEmpty()) Transactions.rollback(conf, errors);
        }
    }

    @Override
    public List<Job> overlapping(JobArea area) {
        @Language("mariadb")
        var where = """
                WHERE state IN (?, ?, ?, ?)
                  AND kind <> ?
                  AND world = ?
                  AND min_x <= ? AND max_x >= ?
                  AND min_y <= ? AND max_y >= ?
                  AND min_z <= ? AND max_z >= ?
                ORDER BY id""";
        return query(JOB + where)
                .single(call().bind(JobState.OPEN.name())
                        .bind(JobState.TAKEN.name())
                        .bind(JobState.DONE.name())
                        .bind(JobState.DISPUTED.name())
                        .bind(JobKind.CONTRACT.name())
                        .bind(area.world())
                        .bind(area.maxX())
                        .bind(area.minX())
                        .bind(area.maxY())
                        .bind(area.minY())
                        .bind(area.maxZ())
                        .bind(area.minZ()))
                .map(this::parse)
                .all();
    }

    @Override
    public Map<UUID, Integer> workOf(int job) {
        Map<UUID, Integer> work = new LinkedHashMap<>();
        query("SELECT worker_uuid, blocks FROM job_work WHERE job_id = ? ORDER BY blocks DESC")
                .single(call().bind(job))
                .map(row -> Map.entry(row.get("worker_uuid", UUID_BYTES), row.getInt("blocks")))
                .all()
                .forEach(entry -> work.put(entry.getKey(), entry.getValue()));
        return work;
    }

    private Job parse(Row row) throws SQLException {
        Integer companyId = row.getObject("company_id") == null ? null : row.getInt("company_id");
        return new Job(
                row.getInt("id"),
                row.get("owner_uuid", UUID_BYTES),
                JobKind.valueOf(row.getString("kind")),
                row.getString("title"),
                row.getString("description"),
                row.getDouble("price"),
                row.getDouble("fee"),
                JobState.valueOf(row.getString("state")),
                companyId,
                row.get("node", UUID_BYTES),
                new JobArea(
                        row.getString("world"),
                        row.getInt("min_x"),
                        row.getInt("min_y"),
                        row.getInt("min_z"),
                        row.getInt("max_x"),
                        row.getInt("max_y"),
                        row.getInt("max_z")),
                JobLand.valueOf(row.getString("land")),
                row.getString("land_ref"),
                row.getInt("due_hours"),
                readTime(row, "created"),
                readTime(row, "expires"),
                readTime(row, "taken"),
                readTime(row, "due"),
                readTime(row, "closed"),
                row.getString("closed_by"),
                row.getBoolean("granted"),
                new JobReview(
                        readTime(row, "done_at"),
                        row.get("done_by", UUID_BYTES),
                        row.getString("rejected"),
                        row.get("dispute_by", UUID_BYTES),
                        row.getString("dispute"),
                        readTime(row, "disputed_at"),
                        row.getObject("settled") == null ? null : row.getInt("settled")));
    }
}
