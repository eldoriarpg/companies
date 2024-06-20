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

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_BYTES;

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

        query(insert)
                 .single(call().bind(nodeId, UUID_BYTES).bind(type).bind(version))
                 .insert();
    }

    @Override
    public List<Node> getPrimaryNodes() {
        @Language("mariadb")
        var select = """
                SELECT id, uid, type, version
                FROM node
                WHERE type = 'PRIMARY'""";

        return query(select)
                .single()
                .map(row -> new Node(row.getInt("id"), row.get("uid", UUID_BYTES), row.getString("version")))
                .all();
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

        return query(select)
                .single(call().bind(path))
                .map(row -> row.getString(1))
                .first();
    }

    @Override
    protected void savePrimaryConfiguration(String path, String content) {
         @Language("mariadb")
          var insert = """
              INSERT INTO node_configuration(node_id, path, content) VALUES (?, ?, ?)
              ON DUPLICATE KEY UPDATE content = VALUES(content)""";

        query(insert)
                .single(call().bind(getNodeId()).bind(path).bind(content))
                .insert();
    }

    @Override
    protected Optional<Integer> retrieveNodeId() {
         @Language("mariadb")
          var select = """
              SELECT id
              FROM node
              where uid = ?""";

        return query(select)
                .single(call().bind(nodeUid(), UUID_BYTES))
                .map(row -> row.getInt(1))
                .first();
    }
}
