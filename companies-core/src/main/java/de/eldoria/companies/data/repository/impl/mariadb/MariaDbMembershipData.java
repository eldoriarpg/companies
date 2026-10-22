/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.mariadb;

import de.chojo.sadu.mapper.wrapper.Row;
import de.chojo.sadu.queries.api.call.adapter.Adapter;
import de.chojo.sadu.queries.api.configuration.ConnectedQueryConfiguration;
import de.chojo.sadu.queries.api.configuration.QueryConfiguration;
import de.chojo.sadu.queries.configuration.ConnectedQueryConfigurationImpl;
import de.eldoria.companies.data.repository.AMembershipData;
import de.eldoria.companies.data.repository.ConstraintViolations;
import de.eldoria.companies.data.wrapper.company.Application;
import de.eldoria.companies.data.wrapper.company.ApplyResult;
import de.eldoria.companies.data.wrapper.company.Invite;
import de.eldoria.companies.data.wrapper.company.JoinResult;
import org.intellij.lang.annotations.Language;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
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
 * Invites, applications and joins for MariaDB. The other dialects override the statements whose syntax differs, the
 * company lock and the binding of times.
 */
public class MariaDbMembershipData extends AMembershipData {
    @Language("mariadb")
    private static final String INVITES = """
            SELECT i.id, i.company_id, c.name, i.target_uuid, i.inviter_uuid, i.created, i.expires
            FROM company_invites i
                     JOIN companies c
                          ON c.id = i.company_id
            """;

    @Language("mariadb")
    private static final String APPLICATIONS = """
            SELECT a.id, a.company_id, c.name, a.applicant_uuid, a.message, a.created, a.expires
            FROM company_applications a
                     JOIN companies c
                          ON c.id = a.company_id
            """;

    private static final Adapter<LocalDateTime> TIMESTAMP =
            Adapter.create(PreparedStatement::setTimestamp, Types.TIMESTAMP, Timestamp::valueOf);

    public MariaDbMembershipData(ExecutorService executor) {
        super(executor);
    }

    /**
     * Binds a point in time in the form the time columns of this dialect compare correctly.
     *
     * @return adapter for times
     */
    protected Adapter<LocalDateTime> time() {
        return TIMESTAMP;
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

    @Override
    protected boolean saveInvite(int company, UUID target, UUID inviter, LocalDateTime created, LocalDateTime expires) {
        @Language("mariadb")
        var query = """
                INSERT INTO company_invites(company_id, target_uuid, inviter_uuid, created, expires)
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE inviter_uuid = VALUES(inviter_uuid),
                                        created      = VALUES(created),
                                        expires      = VALUES(expires)""";
        return upsertInvite(query, company, target, inviter, created, expires);
    }

    /**
     * Runs the dialect's upsert of an invite.
     *
     * @param query   upsert with the parameters company, target, inviter, created and expires
     * @param company id of the company
     * @param target  invited player
     * @param inviter inviting member
     * @param created current time
     * @param expires expiry
     * @return true when a row was written
     */
    protected boolean upsertInvite(
            String query, int company, UUID target, UUID inviter, LocalDateTime created, LocalDateTime expires) {
        return query(query)
                .single(call().bind(company)
                        .bind(target, UUID_BYTES)
                        .bind(inviter, UUID_BYTES)
                        .bind(created, time())
                        .bind(expires, time()))
                .insert()
                .changed();
    }

    @Override
    protected List<Invite> getInvitesOf(UUID target, LocalDateTime now) {
        return query(INVITES + "WHERE i.target_uuid = ? AND i.expires > ? ORDER BY i.created, i.id")
                .single(call().bind(target, UUID_BYTES).bind(now, time()))
                .map(this::parseInvite)
                .all();
    }

    @Override
    protected List<Invite> getInvitesBy(int company, LocalDateTime now) {
        return query(INVITES + "WHERE i.company_id = ? AND i.expires > ? ORDER BY i.created, i.id")
                .single(call().bind(company).bind(now, time()))
                .map(this::parseInvite)
                .all();
    }

    @Override
    protected Optional<Invite> getInvite(int company, UUID target, LocalDateTime now) {
        return query(INVITES + "WHERE i.company_id = ? AND i.target_uuid = ? AND i.expires > ?")
                .single(call().bind(company).bind(target, UUID_BYTES).bind(now, time()))
                .map(this::parseInvite)
                .first();
    }

    @Override
    protected boolean removeInvite(int invite) {
        @Language("mariadb")
        var query = """
                DELETE FROM company_invites WHERE id = ?""";
        return query(query).single(call().bind(invite)).delete().changed();
    }

    @Override
    protected List<Invite> getExpiredInvites(LocalDateTime now, int limit) {
        return query(INVITES + "WHERE i.expires <= ? ORDER BY i.expires, i.id LIMIT ?")
                .single(call().bind(now, time()).bind(limit))
                .map(this::parseInvite)
                .all();
    }

    @Override
    protected ApplyResult saveApplication(
            int company, UUID applicant, String message, LocalDateTime created, LocalDateTime expires, int maxPending) {
        @Language("mariadb")
        var deleteExpired = """
                DELETE FROM company_applications WHERE company_id = ? AND applicant_uuid = ? AND expires <= ?""";
        @Language("mariadb")
        var pending = """
                SELECT company_id FROM company_applications WHERE applicant_uuid = ? AND expires > ?""";
        @Language("mariadb")
        var insert = """
                INSERT INTO company_applications(company_id, applicant_uuid, message, created, expires)
                VALUES (?, ?, ?, ?, ?)""";
        var errors = new ArrayList<SQLException>();
        var conf = transaction(errors);
        try (conf) {
            conf.query(deleteExpired)
                    .single(call().bind(company).bind(applicant, UUID_BYTES).bind(created, time()))
                    .delete();
            var open = conf.query(pending)
                    .single(call().bind(applicant, UUID_BYTES).bind(created, time()))
                    .map(row -> row.getInt("company_id"))
                    .all();
            if (!errors.isEmpty()) return failed(conf, errors, ApplyResult.FAILED, ApplyResult.FAILED);
            if (open.contains(company)) return ApplyResult.ALREADY_PENDING;
            if (open.size() >= maxPending) return ApplyResult.TOO_MANY;
            conf.query(insert)
                    .single(call().bind(company)
                            .bind(applicant, UUID_BYTES)
                            .bind(message)
                            .bind(created, time())
                            .bind(expires, time()))
                    .insert();
            if (!errors.isEmpty()) return failed(conf, errors, ApplyResult.ALREADY_PENDING, ApplyResult.FAILED);
        }
        return ApplyResult.CREATED;
    }

    @Override
    protected List<Application> getApplicationsTo(int company, LocalDateTime now) {
        return query(APPLICATIONS + "WHERE a.company_id = ? AND a.expires > ? ORDER BY a.created, a.id")
                .single(call().bind(company).bind(now, time()))
                .map(this::parseApplication)
                .all();
    }

    @Override
    protected List<Application> getApplicationsOf(UUID applicant, LocalDateTime now) {
        return query(APPLICATIONS + "WHERE a.applicant_uuid = ? AND a.expires > ? ORDER BY a.created, a.id")
                .single(call().bind(applicant, UUID_BYTES).bind(now, time()))
                .map(this::parseApplication)
                .all();
    }

    @Override
    protected Optional<Application> getApplication(int company, UUID applicant, LocalDateTime now) {
        return query(APPLICATIONS + "WHERE a.company_id = ? AND a.applicant_uuid = ? AND a.expires > ?")
                .single(call().bind(company).bind(applicant, UUID_BYTES).bind(now, time()))
                .map(this::parseApplication)
                .first();
    }

    @Override
    protected boolean removeApplication(int application) {
        @Language("mariadb")
        var query = """
                DELETE FROM company_applications WHERE id = ?""";
        return query(query).single(call().bind(application)).delete().changed();
    }

    @Override
    protected List<Application> getExpiredApplications(LocalDateTime now, int limit) {
        return query(APPLICATIONS + "WHERE a.expires <= ? ORDER BY a.expires, a.id LIMIT ?")
                .single(call().bind(now, time()).bind(limit))
                .map(this::parseApplication)
                .all();
    }

    @Override
    protected JoinResult join(int company, UUID player, int maxMembers) {
        @Language("mariadb")
        var membership = """
                SELECT id FROM company_member WHERE member_uuid = ?""";
        @Language("mariadb")
        var members = """
                SELECT COUNT(1) AS count FROM company_member WHERE id = ?""";
        @Language("mariadb")
        var insert = """
                INSERT INTO company_member(id, member_uuid, permission) VALUES (?, ?, 0)""";
        @Language("mariadb")
        var deleteInvites = """
                DELETE FROM company_invites WHERE target_uuid = ?""";
        @Language("mariadb")
        var deleteApplications = """
                DELETE FROM company_applications WHERE applicant_uuid = ?""";
        var errors = new ArrayList<SQLException>();
        var conf = transaction(errors);
        try (conf) {
            var exists = lockCompany(conf, company);
            if (!errors.isEmpty()) return failed(conf, errors, JoinResult.FAILED, JoinResult.FAILED);
            if (!exists) return JoinResult.UNKNOWN_COMPANY;
            var member = conf.query(membership)
                    .single(call().bind(player, UUID_BYTES))
                    .map(row -> row.getInt("id"))
                    .first();
            var count = conf.query(members)
                    .single(call().bind(company))
                    .map(row -> row.getInt("count"))
                    .first()
                    .orElse(0);
            if (!errors.isEmpty()) return failed(conf, errors, JoinResult.FAILED, JoinResult.FAILED);
            if (member.isPresent()) return JoinResult.ALREADY_MEMBER;
            if (count >= maxMembers) return JoinResult.COMPANY_FULL;
            conf.query(insert)
                    .single(call().bind(company).bind(player, UUID_BYTES))
                    .insert();
            if (!errors.isEmpty()) return failed(conf, errors, JoinResult.ALREADY_MEMBER, JoinResult.FAILED);
            conf.query(deleteInvites).single(call().bind(player, UUID_BYTES)).delete();
            conf.query(deleteApplications)
                    .single(call().bind(player, UUID_BYTES))
                    .delete();
            if (!errors.isEmpty()) return failed(conf, errors, JoinResult.FAILED, JoinResult.FAILED);
        }
        return JoinResult.JOINED;
    }

    /**
     * Locks the company row until the transaction ends, so joins into the same company run one after the other.
     *
     * @param conf    open transaction
     * @param company id of the company
     * @return true when the company exists
     */
    protected boolean lockCompany(ConnectedQueryConfiguration conf, int company) {
        @Language("mariadb")
        var query = """
                SELECT id FROM companies WHERE id = ? FOR UPDATE""";
        return conf.query(query)
                .single(call().bind(company))
                .map(row -> row.getInt("id"))
                .first()
                .isPresent();
    }

    /**
     * Opens a transaction that collects errors instead of logging them, so expected constraint violations can be
     * turned into results. The transaction is committed on close only when no error was collected.
     *
     * @param errors list receiving the errors
     * @return transaction
     */
    private static ConnectedQueryConfigurationImpl transaction(List<SQLException> errors) {
        return QueryConfiguration.getDefault()
                .edit()
                .setExceptionHandler(errors::add)
                .build()
                .withSingleTransaction();
    }

    /**
     * Rolls the transaction back and maps the collected errors to a result. Errors other than constraint violations
     * are logged.
     *
     * @param conf      transaction to roll back
     * @param errors    collected errors
     * @param violation result when every error is a constraint violation
     * @param failure   result otherwise
     * @param <T>       type of result
     * @return result
     */
    private static <T> T failed(ConnectedQueryConfiguration conf, List<SQLException> errors, T violation, T failure) {
        try {
            conf.connection().rollback();
        } catch (SQLException e) {
            errors.add(e);
        }
        if (errors.stream().allMatch(ConstraintViolations::isViolation)) return violation;
        errors.forEach(QueryConfiguration.getDefault().exceptionHandler());
        return failure;
    }

    private Invite parseInvite(Row row) throws SQLException {
        return new Invite(
                row.getInt("id"),
                row.getInt("company_id"),
                row.getString("name"),
                row.get("target_uuid", UUID_BYTES),
                row.get("inviter_uuid", UUID_BYTES),
                readTime(row, "created"),
                readTime(row, "expires"));
    }

    private Application parseApplication(Row row) throws SQLException {
        return new Application(
                row.getInt("id"),
                row.getInt("company_id"),
                row.getString("name"),
                row.get("applicant_uuid", UUID_BYTES),
                row.getString("message"),
                readTime(row, "created"),
                readTime(row, "expires"));
    }
}
