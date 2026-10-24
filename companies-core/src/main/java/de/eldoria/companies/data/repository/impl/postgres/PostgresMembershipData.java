/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.postgres;

import de.eldoria.companies.data.repository.impl.mariadb.MariaDbMembershipData;
import org.intellij.lang.annotations.Language;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * Invites, applications and joins for PostgreSQL.
 */
public class PostgresMembershipData extends MariaDbMembershipData {
    public PostgresMembershipData(ExecutorService executor) {
        super(executor);
    }

    @Override
    protected boolean saveInvite(int company, UUID target, UUID inviter, LocalDateTime created, LocalDateTime expires) {
        @Language("postgresql")
        var query = """
                INSERT INTO company_invites(company_id, target_uuid, inviter_uuid, created, expires)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (company_id, target_uuid) DO UPDATE SET inviter_uuid = excluded.inviter_uuid,
                                                                    created      = excluded.created,
                                                                    expires      = excluded.expires""";
        return upsertInvite(query, company, target, inviter, created, expires);
    }
}
