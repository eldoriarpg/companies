/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.mariadb;

import de.eldoria.companies.configuration.elements.NodeSettings;
import de.eldoria.companies.configuration.elements.NodeType;
import de.eldoria.companies.data.repository.ANodeData;
import de.eldoria.companies.data.wrapper.nodes.Node;
import org.intellij.lang.annotations.Language;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import static de.eldoria.companies.data.StaticQueryAdapter.builder;

public class MariaDbNodeData extends ANodeData {
    public MariaDbNodeData(NodeSettings nodeSettings) {
        super(nodeSettings);
    }

    @Override
    protected void updateNode(UUID nodeId, NodeType type, String version) {
        @Language("mariadb")
        var insert = """
                INSERT INTO node(uid, type, version)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE type    = VALUES(type),
                                        version = VALUES(version)""";

        builder().query(insert)
                 .parameter(stmt -> stmt.setUuidAsBytes(nodeId).setEnum(type).setString(version))
                 .insert()
                 .sendSync();
    }

    @Override
    public List<Node> getPrimaryNodes() {
        @Language("mariadb")
        var select = """
                SELECT id, uid, type, version
                FROM node
                WHERE type = 'PRIMARY'""";

        return builder(Node.class)
                .query(select)
                .emptyParams()
                .readRow(row -> new Node(row.getInt("id"), row.getUuidFromBytes("uid"), row.getString("version")))
                .allSync();
    }

    @Override
    protected Optional<String> loadPrimaryConfiguration(String path) {
         @Language("mariadb")
          var select = """
              SELECT content
              FROM node_configuration c
                       LEFT JOIN node n on n.id = c.node_id
              WHERE n.type = 'PRIMARY'
                AND path = ?""";

        return builder(String.class)
                .query(select)
                .parameter(stmt -> stmt.setString(path))
                .readRow(row -> row.getString(1))
                .firstSync();
    }

    @Override
    protected void savePrimaryConfiguration(String path, String content) {
         @Language("mariadb")
          var insert = """
              INSERT INTO node_configuration(node_id, path, content) VALUES (?, ?, ?)
              ON DUPLICATE KEY UPDATE content = VALUES(content)""";

        builder().query(insert)
                .parameter(stmt -> stmt.setInt(getNodeId()).setString(path).setString(content))
                .insert()
                .sendSync();
    }

    @Override
    protected Optional<Integer> retrieveNodeId() {
         @Language("mariadb")
          var select = """
              SELECT id
              FROM node
              where uid = ?""";

        return builder(Integer.class)
                .query(select)
                .parameter(stmt -> stmt.setUuidAsBytes(nodeUid()))
                .readRow(row -> row.getInt(1))
                .firstSync();
    }
}
