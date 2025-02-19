/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.sqlite;

import de.chojo.sadu.mapper.wrapper.Row;
import de.eldoria.companies.components.company.ISimpleCompany;
import de.eldoria.companies.data.repository.impl.mariadb.MariaDbCompanyData;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.company.CompanyStats;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;
import org.intellij.lang.annotations.Language;

import java.sql.SQLException;
import java.util.concurrent.ExecutorService;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_BYTES;

public class SqLiteCompanyData extends MariaDbCompanyData {

    public SqLiteCompanyData(ExecutorService executorService) {
        super(executorService);
    }

    @Override
    protected void updateMember(CompanyMember member) {
        if (member.company() == -1) {
            @Language("sqlite")
            var query = """
                    DELETE
                    FROM company_member
                    WHERE member_uuid = ?""";
            query(query)
                    .single(call().bind(member.uuid(), UUID_BYTES))
                    .update();
        } else {
            @Language("sqlite")
            var query = """
                    INSERT
                    INTO company_member(id, member_uuid, permission)
                    VALUES (?, ?, ?)
                    ON CONFLICT(member_uuid) DO UPDATE SET id         = excluded.id,
                                                           permission = excluded.permission""";
            query(query)
                    .single(call().bind(member.company())
                            .bind(member.uuid(), UUID_BYTES)
                            .bind(member.permission()))
                    .update();
        }
    }

    @Override
    protected SimpleCompany parseCompany(Row rs) throws SQLException {
        return new SimpleCompany(rs.getInt("id"), rs.getString("name"),
                SqLiteAdapter.getTimestamp(rs, "founded"), rs.getInt("level"));
    }

    @Override
    public void upcountFailedOrders(ISimpleCompany company, int amount) {
        @Language("sqlite")
        var query = """
                INSERT
                        INTO
                        	company_stats(id, failed_orders)
                        VALUES
                        	(?, ?)
                        ON CONFLICT(id) DO UPDATE SET
                        	failed_orders = failed_orders + excluded.failed_orders""";
        query(query)
                .single(call().bind(company.id())
                        .bind(amount)
                        .bind(amount))
                .update();
    }

    protected CompanyStats parseCompanyStats(Row rs) throws SQLException {
        return new CompanyStats(rs.getInt("id"), rs.getString("name"), SqLiteAdapter.getTimestamp(rs, "founded"),
                rs.getInt("member_count"), rs.getInt("order_count"), rs.getDouble("price"), rs.getInt("amount"));
    }
}
