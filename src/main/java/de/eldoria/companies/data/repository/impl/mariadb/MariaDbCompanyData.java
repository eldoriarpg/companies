package de.eldoria.companies.data.repository.impl.mariadb;

import de.chojo.sqlutil.conversion.UUIDConverter;
import de.eldoria.companies.commands.company.TopOrder;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.company.CompanyRank;
import de.eldoria.companies.data.wrapper.company.CompanyStats;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

public class MariaDbCompanyData extends ACompanyData {

    public MariaDbCompanyData(DataSource dataSource, Plugin plugin, ExecutorService executorService) {
        super(plugin, dataSource, executorService);
    }

    @Override
    protected void updateMember(CompanyMember member) {
        if (member.company() == -1) {
            builder()
                    .query("DELETE FROM company_member WHERE member_uuid = ?")
                    .paramsBuilder(stmt -> stmt.setBytes(UUIDConverter.convert(member.uuid())))
                    .update().executeSync();
        } else {
            builder()
                    .query("REPLACE company_member(id, member_uuid, permission) VALUES(?,?,?)")
                    .paramsBuilder(stmt -> stmt.setInt(member.company()).setBytes(UUIDConverter.convert(member.uuid())).setLong(member.permission()))
                    .update().executeSync();
        }
    }

    // Company Profiles

    @Override
    protected Optional<CompanyProfile> toCompanyProfile(SimpleCompany simpleCompany) {
        return Optional.ofNullable(simpleCompany.toCompanyProfile(getCompanyMember(simpleCompany)));
    }

    @Override
    protected Optional<SimpleCompany> getPlayerCompany(OfflinePlayer player) {
        return builder(SimpleCompany.class)
                .query("SELECT c.id, c.name, c.founded, c.level FROM company_member LEFT JOIN companies c ON c.id = company_member.id WHERE member_uuid = ?")
                .paramsBuilder(stmt -> stmt.setBytes(UUIDConverter.convert(player.getUniqueId())))
                .readRow(this::parseCompany)
                .firstSync();
    }

    @Override
    protected Optional<SimpleCompany> getCompanyByName(String name) {
        return builder(SimpleCompany.class)
                .query("SELECT c.id, c.name, c.founded, c.level FROM company_member LEFT JOIN companies c ON c.id = company_member.id WHERE c.name LIKE ?")
                .paramsBuilder(stmt -> stmt.setString(name))
                .readRow(this::parseCompany)
                .firstSync();
    }

    @Override
    protected Optional<SimpleCompany> getCompanyById(int id) {
        return builder(SimpleCompany.class)
                .query("SELECT id, name, founded, level FROM companies WHERE id = ?")
                .paramsBuilder(stmt -> stmt.setInt(id))
                .readRow(this::parseCompany)
                .firstSync();
    }

    @Override
    protected Integer createCompany(String name) {
        return builder(Integer.class)
                .query("INSERT INTO companies(name) VALUES(?) RETURNING id")
                .paramsBuilder(stmt -> stmt.setString(name))
                .readRow(rs -> rs.getInt(1))
                .firstSync().get();
    }

    @Override
    protected SimpleCompany parseCompany(ResultSet rs) throws SQLException {
        return new SimpleCompany(rs.getInt("id"), rs.getString("name"),
                rs.getTimestamp("founded").toLocalDateTime(), rs.getInt("level"));
    }

    @Override
    public void purgeCompany(SimpleCompany company) {
        var members = getCompanyMember(company);
        for (var member : members) {
            updateMember(member.kick());
        }
    }

    @Override
    protected CompanyStats getCompanyStats(SimpleCompany company) {
        return builder(CompanyStats.class)
                .query("SELECT id, name, founded, member_count, order_count, price, amount FROM company_stats_view WHERE id = ?")
                .paramsBuilder(stmt -> stmt.setInt(company.id()))
                .readRow(this::parseCompanyStats)
                .firstSync().get();
    }

    @Override
    protected void updateCompanyLevel(SimpleCompany company, int level) {
        builder().query("UPDATE companies SET level = ? WHERE id = ?")
                .paramsBuilder(stmt -> stmt.setInt(level).setInt(company.id()))
                .update().executeSync();
    }

    @Override
    public void upcountFailedOrders(SimpleCompany company, int amount) {
        builder()
                .query("INSERT INTO company_stats(id, failed_orders) VALUES(?,?) ON DUPLICATE KEY UPDATE failed_orders = failed_orders + ?")
                .paramsBuilder(stmt -> stmt.setInt(company.id()).setInt(amount).setInt(amount))
                .update().executeSync();
    }

    @Override
    protected List<CompanyRank> getRanking(TopOrder order, int page, int pageSize) {
        return builder(CompanyRank.class)
                .query("SELECT ROW_NUMBER() OVER (ORDER BY %s) as comp_rank, id, name, founded, member_count, order_count, price, amount FROM company_stats_view ORDER BY comp_rank LIMIT ? OFFSET ?", order.orderColumn())
                .paramsBuilder(stmt -> stmt.setInt(pageSize).setInt((page - 1) * pageSize))
                .readRow(rs -> parseCompanyStats(rs).toRank(rs.getInt("comp_rank")))
                .allSync();
    }

    protected CompanyStats parseCompanyStats(ResultSet rs) throws SQLException {
        return new CompanyStats(rs.getInt("id"), rs.getString("name"), rs.getTimestamp("founded").toLocalDateTime(),
                rs.getInt("member_count"), rs.getInt("order_count"), rs.getDouble("price"), rs.getInt("amount"));
    }

    @Override
    protected List<CompanyMember> getCompanyMember(SimpleCompany company) {
        return builder(CompanyMember.class)
                .query("SELECT member_uuid, permission FROM company_member WHERE id = ?")
                .paramsBuilder(stmt -> stmt.setInt(company.id()))
                .readRow(rs -> CompanyMember.of(company.id(), UUIDConverter.convert(rs.getBytes("member_uuid")),
                        rs.getLong("permission")))
                .allSync();
    }

    @Override
    protected Optional<CompanyMember> getCompanyMember(OfflinePlayer player) {
        return builder(CompanyMember.class)
                .query("SELECT id, member_uuid, permission FROM company_member WHERE member_uuid = ?")
                .paramsBuilder(stmt -> stmt.setBytes(UUIDConverter.convert(player.getUniqueId())))
                .readRow(rs -> CompanyMember.of(rs.getInt("id"), UUIDConverter.convert(rs.getBytes("member_uuid")),
                        rs.getLong("permission")))
                .firstSync();
    }

    @Override
    protected Optional<SimpleCompany> getSimpleCompany(int companyId) {
        return builder(SimpleCompany.class)
                .query("SELECT id, name, founded, level FROM companies WHERE id = ?")
                .paramsBuilder(stmt -> stmt.setInt(companyId))
                .readRow(this::parseCompany)
                .firstSync();
    }
}
