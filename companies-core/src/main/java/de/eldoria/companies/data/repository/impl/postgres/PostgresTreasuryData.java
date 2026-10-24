/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.postgres;

import de.eldoria.companies.data.repository.impl.mariadb.MariaDbTreasuryData;
import org.intellij.lang.annotations.Language;

import java.util.concurrent.ExecutorService;

/**
 * Treasuries for PostgreSQL, whose upserts use {@code ON CONFLICT}.
 */
public class PostgresTreasuryData extends MariaDbTreasuryData {
    public PostgresTreasuryData(ExecutorService executor) {
        super(executor);
    }

    @Override
    protected String creditUpsert() {
        @Language("postgresql")
        var query = """
                INSERT INTO company_treasury(company_id, balance)
                VALUES (?, ?)
                ON CONFLICT (company_id) DO UPDATE SET balance = company_treasury.balance + excluded.balance""";
        return query;
    }

    @Override
    protected String shareUpsert() {
        @Language("postgresql")
        var query = """
                INSERT INTO company_treasury(company_id, payout_share)
                VALUES (?, ?)
                ON CONFLICT (company_id) DO UPDATE SET payout_share = excluded.payout_share""";
        return query;
    }
}
