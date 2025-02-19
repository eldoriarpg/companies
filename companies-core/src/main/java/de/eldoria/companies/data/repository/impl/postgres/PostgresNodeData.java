/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.postgres;

import de.eldoria.companies.configuration.elements.NodeSettings;
import de.eldoria.companies.configuration.elements.NodeType;
import de.eldoria.companies.data.repository.impl.mariadb.MariaDbNodeData;
import org.intellij.lang.annotations.Language;

import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_BYTES;

public class PostgresNodeData extends MariaDbNodeData {
    public PostgresNodeData(NodeSettings nodeSettings) {
        super(nodeSettings);
    }

    @Override
    protected void updateNode(UUID nodeId, NodeType type, String version) {
        @Language("postgresql")
        var insert = """
                INSERT INTO node(uid, type, version)
                VALUES (?, ?, ?)
                ON CONFLICT(uid) DO UPDATE SET type    = excluded.type,
                                               version = excluded.version""";

        query(insert)
                .single(call().bind(nodeId, UUID_BYTES).bind(type).bind(version))
                .insert();
    }

    @Override
    protected void savePrimaryConfiguration(String path, String content) {
        @Language("postgresql")
        var insert = """
                INSERT INTO node_configuration(node_id, path, content)
                VALUES (?, ?, ?)
                ON CONFLICT(node_id, path) DO UPDATE SET content = excluded.content""";

        query(insert)
                .single(call().bind(getNodeId()).bind(path).bind(content))
                .insert();
    }
}
