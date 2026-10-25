/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.postgres;

import de.eldoria.companies.configuration.elements.NodeSettings;
import de.eldoria.companies.configuration.elements.NodeType;
import de.eldoria.companies.data.repository.impl.mariadb.MariaDbNodeData;
import de.eldoria.companies.util.Times;
import org.intellij.lang.annotations.Language;

import java.time.LocalDateTime;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_BYTES;

public class PostgresNodeData extends MariaDbNodeData {
    public PostgresNodeData(NodeSettings nodeSettings) {
        super(nodeSettings);
    }

    @Override
    protected void updateNode(UUID nodeId, NodeType type, String version, LocalDateTime seen) {
        @Language("postgresql")
        var insert = """
                INSERT INTO node(uid, type, version, last_seen)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(uid) DO UPDATE SET type      = excluded.type,
                                               version   = excluded.version,
                                               last_seen = excluded.last_seen""";

        query(insert)
                .single(call().bind(nodeId, UUID_BYTES).bind(type).bind(version).bind(seen, time()))
                .insert();
    }

    @Override
    public void appliedSettings(String path, int revision) {
        @Language("postgresql")
        var upsert = """
                INSERT INTO settings_applied(node_uid, path, revision, applied_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(node_uid, path) DO UPDATE SET revision   = excluded.revision,
                                                          applied_at = excluded.applied_at""";

        query(upsert)
                .single(call().bind(nodeUid(), UUID_BYTES)
                        .bind(path)
                        .bind(revision)
                        .bind(Times.now(), time()))
                .insert();
    }

    @Override
    protected boolean insertSettings(
            String path, int revision, String content, String hash, String actor, LocalDateTime changed) {
        @Language("postgresql")
        var insert = """
                INSERT INTO settings(path, revision, content, hash, node_uid, actor, changed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(path, revision) DO NOTHING""";

        return query(insert)
                .single(call().bind(path)
                        .bind(revision)
                        .bind(content)
                        .bind(hash)
                        .bind(nodeUid(), UUID_BYTES)
                        .bind(actor)
                        .bind(changed, time()))
                .insert()
                .changed();
    }

    @Override
    protected void savePrimaryConfiguration(String path, String content) {
        @Language("postgresql")
        var insert = """
                INSERT INTO node_configuration(node_id, path, content)
                VALUES (?, ?, ?)
                ON CONFLICT(node_id, path) DO UPDATE SET content = excluded.content""";

        query(insert).single(call().bind(getNodeId()).bind(path).bind(content)).insert();
    }
}
