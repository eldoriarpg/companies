/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.mariadb;

import de.chojo.sadu.mapper.wrapper.Row;
import de.chojo.sadu.queries.api.configuration.ConnectedQueryConfiguration;
import de.chojo.sadu.queries.api.configuration.QueryConfiguration;
import de.eldoria.companies.commands.company.TopOrder;
import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.company.ISimpleCompany;
import de.eldoria.companies.components.company.JoinMode;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.ConstraintViolations;
import de.eldoria.companies.data.wrapper.company.CompanyDetails;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.company.CompanyRank;
import de.eldoria.companies.data.wrapper.company.CompanyStats;
import de.eldoria.companies.data.wrapper.company.DetailsResult;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;
import org.bukkit.OfflinePlayer;
import org.intellij.lang.annotations.Language;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_BYTES;

public class MariaDbCompanyData extends ACompanyData {
    /**
     * The columns every company select reads, as {@link #parseCompany(Row)} expects them.
     */
    protected static final String COMPANY_COLUMNS = "id, name, founded, level, description, tag, color, join_mode";

    public MariaDbCompanyData(ExecutorService executorService) {
        super(executorService);
    }

    @Override
    protected void updateMember(CompanyMember member) {
        if (member.company() == -1) {
            @Language("mariadb")
            var query = """
                    DELETE
                    FROM company_member
                    WHERE member_uuid = ?""";
            query(query).single(call().bind(member.uuid(), UUID_BYTES)).update();
        } else {
            @Language("mariadb")
            var query = """
                    REPLACE company_member(id, member_uuid, permission)
                    VALUES (?, ?, ?)""";
            query(query)
                    .single(call().bind(member.company())
                            .bind(member.uuid(), UUID_BYTES)
                            .bind(member.permission()))
                    .update();
        }
    }

    @Override
    protected boolean transferOwner(ISimpleCompany company, UUID owner, UUID target) {
        @Language("mariadb")
        var revoke = """
                UPDATE company_member
                SET permission = permission - ?
                WHERE id = ?
                  AND member_uuid = ?
                  AND (permission & ?) <> 0""";
        @Language("mariadb")
        var grant = """
                UPDATE company_member
                SET permission = permission + ?
                WHERE id = ?
                  AND member_uuid = ?
                  AND (permission & ?) = 0""";
        var mask = CompanyPermission.OWNER.mask();
        var conf = QueryConfiguration.getDefault().withSingleTransaction();
        try (conf) {
            var revoked = conf.query(revoke)
                    .single(call().bind(mask)
                            .bind(company.id())
                            .bind(owner, UUID_BYTES)
                            .bind(mask))
                    .update();
            if (!revoked.changed()) return false;
            var granted = conf.query(grant)
                    .single(call().bind(mask)
                            .bind(company.id())
                            .bind(target, UUID_BYTES)
                            .bind(mask))
                    .update();
            if (!granted.changed()) {
                rollback(conf);
                return false;
            }
        }
        return conf.context().exceptions().isEmpty();
    }

    private static void rollback(ConnectedQueryConfiguration conf) {
        try {
            conf.connection().rollback();
        } catch (SQLException e) {
            conf.handleException(e);
        }
    }

    // Company Profiles

    @Override
    protected Optional<CompanyProfile> toCompanyProfile(SimpleCompany simpleCompany) {
        return Optional.ofNullable(simpleCompany.toCompanyProfile(getCompanyMember(simpleCompany)));
    }

    @Override
    protected Optional<SimpleCompany> getPlayerCompany(OfflinePlayer player) {
        @Language("mariadb")
        var query = """
                SELECT %s
                FROM companies
                WHERE id = (SELECT id FROM company_member WHERE member_uuid = ?)""";
        return query(query, COMPANY_COLUMNS)
                .single(call().bind(player.getUniqueId(), UUID_BYTES))
                .map(this::parseCompany)
                .first();
    }

    @Override
    protected Optional<SimpleCompany> getCompanyByName(String name) {
        @Language("mariadb")
        var query = """
                SELECT %s
                FROM companies
                WHERE LOWER(name) = LOWER(?)""";
        return query(query, COMPANY_COLUMNS)
                .single(call().bind(name))
                .map(this::parseCompany)
                .first();
    }

    @Override
    protected Optional<SimpleCompany> getCompanyById(int id) {
        @Language("mariadb")
        var query = """
                SELECT %s
                FROM companies
                WHERE id = ?""";
        return query(query, COMPANY_COLUMNS)
                .single(call().bind(id))
                .map(this::parseCompany)
                .first();
    }

    @Override
    protected Optional<SimpleCompany> getCompanyByTag(String tag) {
        @Language("mariadb")
        var query = """
                SELECT %s
                FROM companies
                WHERE tag = ?""";
        return query(query, COMPANY_COLUMNS)
                .single(call().bind(tag.toUpperCase(Locale.ROOT)))
                .map(this::parseCompany)
                .first();
    }

    @Override
    protected DetailsResult updateDetails(ISimpleCompany company, CompanyDetails details) {
        @Language("mariadb")
        var query = """
                UPDATE companies
                SET description = ?,
                    tag         = ?,
                    color       = ?,
                    join_mode   = ?
                WHERE id = ?""";
        var errors = new ArrayList<SQLException>();
        var conf = QueryConfiguration.getDefault()
                .edit()
                .setExceptionHandler(errors::add)
                .build();
        var tag = Optional.ofNullable(details.tag())
                .map(value -> value.toUpperCase(Locale.ROOT))
                .orElse(null);
        var result = conf.query(query)
                .single(call().bind(details.description())
                        .bind(tag)
                        .bind(details.color())
                        .bind(details.joinMode().id())
                        .bind(company.id()))
                .update();
        if (errors.isEmpty()) return result.changed() ? DetailsResult.SAVED : DetailsResult.UNKNOWN_COMPANY;
        if (errors.stream().allMatch(ConstraintViolations::isViolation)) return DetailsResult.TAG_TAKEN;
        errors.forEach(QueryConfiguration.getDefault().exceptionHandler());
        return DetailsResult.FAILED;
    }

    @Override
    protected Integer createCompany(String name, JoinMode joinMode) {
        @Language("mariadb")
        var query = """
                INSERT INTO companies(name, join_mode)
                VALUES (?, ?)
                RETURNING id""";
        return query(query)
                .single(call().bind(name).bind(joinMode.id()))
                .map(rs -> rs.getInt(1))
                .first()
                .get();
    }

    @Override
    protected SimpleCompany parseCompany(Row rs) throws SQLException {
        return new SimpleCompany(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getTimestamp("founded").toLocalDateTime(),
                rs.getInt("level"),
                parseDetails(rs));
    }

    /**
     * Reads description, tag, colour and join mode of a company row. An unknown join mode counts as invite only.
     *
     * @param rs row with the company columns
     * @return details
     * @throws SQLException when a column is missing
     */
    protected CompanyDetails parseDetails(Row rs) throws SQLException {
        return new CompanyDetails(
                rs.getString("description"),
                rs.getString("tag"),
                rs.getString("color"),
                JoinMode.byId(rs.getInt("join_mode")).orElse(JoinMode.INVITE_ONLY));
    }

    @Override
    public boolean purgeCompany(SimpleCompany company) {
        @Language("mariadb")
        var deleteMembers = """
                DELETE FROM company_member WHERE id = ?""";
        @Language("mariadb")
        var deleteStats = """
                DELETE FROM company_stats WHERE id = ?""";
        @Language("mariadb")
        var deleteInvites = """
                DELETE FROM company_invites WHERE company_id = ?""";
        @Language("mariadb")
        var deleteApplications = """
                DELETE FROM company_applications WHERE company_id = ?""";
        @Language("mariadb")
        var deleteCompany = """
                DELETE FROM companies WHERE id = ?""";
        var conf = QueryConfiguration.getDefault().withSingleTransaction();
        try (conf) {
            conf.query(deleteMembers).single(call().bind(company.id())).delete();
            conf.query(deleteStats).single(call().bind(company.id())).delete();
            conf.query(deleteInvites).single(call().bind(company.id())).delete();
            conf.query(deleteApplications).single(call().bind(company.id())).delete();
            conf.query(deleteCompany).single(call().bind(company.id())).delete();
        }
        return conf.context().exceptions().isEmpty();
    }

    @Override
    protected CompanyStats getCompanyStats(ISimpleCompany company) {
        @Language("mariadb")
        var query = """
                SELECT id,
                       name,
                       founded,
                       member_count,
                       order_count,
                       price,
                       amount
                FROM company_stats_view
                WHERE id = ?""";
        return query(query)
                .single(call().bind(company.id()))
                .map(this::parseCompanyStats)
                .first()
                .get();
    }

    @Override
    protected void updateCompanyLevel(ISimpleCompany company, int level) {
        @Language("mariadb")
        var query = """
                UPDATE companies
                SET level = ?
                WHERE id = ?""";
        query(query).single(call().bind(level).bind(company.id())).update();
    }

    @Override
    public void upcountFailedOrders(ISimpleCompany company, int amount) {
        @Language("mariadb")
        var query = """
                INSERT
                INTO company_stats(id, failed_orders)
                VALUES (?, ?)
                ON DUPLICATE KEY UPDATE failed_orders = failed_orders + VALUES(failed_orders)""";
        query(query).single(call().bind(company.id()).bind(amount)).update();
    }

    @Override
    protected List<CompanyRank> getRanking(TopOrder order, int page, int pageSize) {
        @Language("mariadb")
        var query = """
                SELECT ROW_NUMBER() OVER (ORDER BY %s) AS comp_rank,
                       id,
                       name,
                       founded,
                       member_count,
                       order_count,
                       price,
                       amount
                FROM company_stats_view
                ORDER BY comp_rank
                LIMIT ? OFFSET ?""";
        return query(query, order.orderColumn())
                .single(call().bind(pageSize).bind((page - 1) * pageSize))
                .map(rs -> parseCompanyStats(rs).toRank(rs.getInt("comp_rank")))
                .all();
    }

    @Override
    protected boolean setCompanyName(SimpleCompany company, String name) {
        @Language("mariadb")
        var query = """
                UPDATE companies
                SET name = ?
                WHERE id = ?""";
        return query(query)
                .single(call().bind(name).bind(company.id()))
                .update()
                .changed();
    }

    @Override
    public List<SimpleCompany> getCompanies() {
        @Language("mariadb")
        var query = """
                SELECT %s
                FROM companies""";
        return query(query, COMPANY_COLUMNS).single().map(this::parseCompany).all();
    }

    protected CompanyStats parseCompanyStats(Row rs) throws SQLException {
        return new CompanyStats(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getTimestamp("founded").toLocalDateTime(),
                rs.getInt("member_count"),
                rs.getInt("order_count"),
                rs.getDouble("price"),
                rs.getInt("amount"));
    }

    @Override
    protected List<CompanyMember> getCompanyMember(SimpleCompany company) {
        @Language("mariadb")
        var query = """
                SELECT member_uuid, permission
                FROM company_member
                WHERE id = ?""";
        return query(query)
                .single(call().bind(company.id()))
                .map(rs -> CompanyMember.of(company.id(), rs.get("member_uuid", UUID_BYTES), rs.getLong("permission")))
                .all();
    }

    @Override
    protected Optional<CompanyMember> getCompanyMember(OfflinePlayer player) {
        @Language("mariadb")
        var query = """
                SELECT id, member_uuid, permission
                FROM company_member
                WHERE member_uuid = ?""";
        return query(query)
                .single(call().bind(player.getUniqueId(), UUID_BYTES))
                .map(rs ->
                        CompanyMember.of(rs.getInt("id"), rs.get("member_uuid", UUID_BYTES), rs.getLong("permission")))
                .first();
    }

    @Override
    protected Optional<SimpleCompany> getSimpleCompany(int companyId) {
        @Language("mariadb")
        var query = """
                SELECT %s
                FROM companies
                WHERE id = ?""";
        return query(query, COMPANY_COLUMNS)
                .single(call().bind(companyId))
                .map(this::parseCompany)
                .first();
    }
}
