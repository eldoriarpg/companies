package de.eldoria.companies.data.repository.impl.sqlite;

import de.chojo.sqlutil.conversion.UUIDConverter;
import de.eldoria.companies.components.company.ISimpleCompany;
import de.eldoria.companies.data.repository.impl.mariadb.MariaDbCompanyData;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.company.CompanyStats;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;
import org.bukkit.plugin.Plugin;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;

public class SqLiteCompanyData extends MariaDbCompanyData {

    public SqLiteCompanyData(DataSource dataSource, Plugin plugin, ExecutorService executorService) {
        super(dataSource, plugin, executorService);
    }

    @Override
    protected Integer createCompany(String name) {
        return builder(Integer.class)
                .query("INSERT INTO companies(name) VALUES(?)")
                .paramsBuilder(stmt -> stmt.setString(name))
                .append()
                .query("SELECT id FROM companies ORDER BY founded DESC")
                .emptyParams()
                .readRow(rs -> rs.getInt(1))
                .firstSync().get();
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
                    .query("INSERT INTO company_member(id, member_uuid, permission) VALUES(?,?,?) ON CONFLICT(member_uuid) DO UPDATE SET id = excluded.id, permission = excluded.permission")
                    .paramsBuilder(stmt -> stmt.setInt(member.company()).setBytes(UUIDConverter.convert(member.uuid())).setLong(member.permission()))
                    .update().executeSync();
        }
    }

    @Override
    protected SimpleCompany parseCompany(ResultSet rs) throws SQLException {
        return new SimpleCompany(rs.getInt("id"), rs.getString("name"),
                SqLiteAdapter.getTimestamp(rs, "founded"), rs.getInt("level"));
    }

    protected CompanyStats parseCompanyStats(ResultSet rs) throws SQLException {
        return new CompanyStats(rs.getInt("id"), rs.getString("name"), SqLiteAdapter.getTimestamp(rs, "founded"),
                rs.getInt("member_count"), rs.getInt("order_count"), rs.getDouble("price"), rs.getInt("amount"));
    }

    @Override
    public void upcountFailedOrders(ISimpleCompany company, int amount) {
        builder()
                .query("INSERT INTO company_stats(id, failed_orders) VALUES(?,?) ON CONFLICT(id) DO UPDATE SET failed_orders = failed_orders + excluded.failed_orders")
                .paramsBuilder(stmt -> stmt.setInt(company.id()).setInt(amount).setInt(amount))
                .update().executeSync();
    }
}
