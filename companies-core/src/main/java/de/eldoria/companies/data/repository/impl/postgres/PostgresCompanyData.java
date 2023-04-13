/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.postgres;

import de.eldoria.companies.components.company.ISimpleCompany;
import de.eldoria.companies.data.repository.impl.mariadb.MariaDbCompanyData;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import org.bukkit.plugin.Plugin;
import org.intellij.lang.annotations.Language;

import javax.sql.DataSource;
import java.util.concurrent.ExecutorService;
import static de.eldoria.companies.data.StaticQueryAdapter.builder;

public class PostgresCompanyData extends MariaDbCompanyData {
    public PostgresCompanyData(ExecutorService executorService) {
        super(executorService);
    }

    @Override
    protected void updateMember(CompanyMember member) {
        if (member.company() == -1) {
            @Language("postgresql")
            var query = """
                    DELETE
                    FROM company_member
                    WHERE member_uuid = ?""";
            builder()
                    .query(query)
                    .parameter(stmt -> stmt.setUuidAsBytes(member.uuid()))
                    .update()
                    .sendSync();
        } else {
            @Language("postgresql")
            var query = """
                    INSERT
                    INTO company_member(id, member_uuid, permission)
                    VALUES (?, ?, ?)
                    ON CONFLICT(member_uuid)
                        DO UPDATE SET id         = excluded.id,
                                      permission = excluded.permission""";
            builder()
                    .query(query)
                    .parameter(stmt -> stmt.setInt(member.company())
                                           .setUuidAsBytes(member.uuid())
                                           .setLong(member.permission()))
                    .update()
                    .sendSync();
        }
    }

    @Override
    public void upcountFailedOrders(ISimpleCompany company, int amount) {
        @Language("postgresql")
        var query = """
                INSERT
                INTO company_stats(id, failed_orders)
                VALUES (?, ?)
                ON CONFLICT(id) DO UPDATE SET failed_orders = failed_orders + excluded.failed_orders""";
        builder()
                .query(query)
                .parameter(stmt -> stmt.setInt(company.id())
                                       .setInt(amount)
                                       .setInt(amount))
                .update()
                .sendSync();
    }
}
