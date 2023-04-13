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
import java.util.concurrent.ExecutorService;

import static de.eldoria.companies.data.StaticQueryAdapter.builder;

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

        builder().query(insert)
                 .parameter(stmt -> stmt.setUuidAsBytes(nodeId).setEnum(type).setString(version))
                 .insert()
                 .sendSync();
    }

    @Override
    protected void savePrimaryConfiguration(String path, String content) {
        @Language("postgresql")
        var insert = """
                INSERT INTO node_configuration(node_id, path, content)
                VALUES (?, ?, ?)
                ON CONFLICT(node_id, path) DO UPDATE SET content = excluded.content""";

        builder().query(insert)
                 .parameter(stmt -> stmt.setInt(getNodeId()).setString(path).setString(content))
                 .insert()
                 .sendSync();
    }
}
