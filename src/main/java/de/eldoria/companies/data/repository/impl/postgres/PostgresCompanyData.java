package de.eldoria.companies.data.repository.impl.postgres;

import de.chojo.sqlutil.conversion.UUIDConverter;
import de.eldoria.companies.data.repository.impl.mariadb.MariaDbCompanyData;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;
import org.bukkit.plugin.Plugin;

import javax.sql.DataSource;
import java.util.concurrent.ExecutorService;

public class PostgresCompanyData extends MariaDbCompanyData {
    public PostgresCompanyData(DataSource dataSource, Plugin plugin, ExecutorService executorService) {
        super(dataSource, plugin, executorService);
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
    public void upcountFailedOrders(SimpleCompany company, int amount) {
        builder()
                .query("INSERT INTO company_stats(id, failed_orders) VALUES(?,?) ON CONFLICT(id) DO UPDATE SET failed_orders = failed_orders + excluded.failed_orders")
                .paramsBuilder(stmt -> stmt.setInt(company.id()).setInt(amount).setInt(amount))
                .update().executeSync();
    }
}
