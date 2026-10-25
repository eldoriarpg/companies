/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.mariadb;

import de.chojo.sadu.mapper.wrapper.Row;
import de.chojo.sadu.queries.api.call.adapter.Adapter;
import de.eldoria.companies.configuration.elements.NodeSettings;
import de.eldoria.companies.configuration.elements.NodeType;
import de.eldoria.companies.data.repository.ANodeData;
import de.eldoria.companies.data.repository.impl.TimeColumns;
import de.eldoria.companies.data.wrapper.nodes.AppliedSettings;
import de.eldoria.companies.data.wrapper.nodes.Node;
import de.eldoria.companies.data.wrapper.nodes.SettingsChange;
import de.eldoria.companies.data.wrapper.nodes.SettingsRevision;
import de.eldoria.companies.util.Times;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_BYTES;

public class MariaDbNodeData extends ANodeData {
    public MariaDbNodeData(NodeSettings nodeSettings) {
        super(nodeSettings);
    }

    @Override
    protected void updateNode(UUID nodeId, NodeType type, String version, LocalDateTime seen) {
        @Language("mariadb")
        var insert = """
                INSERT INTO node(uid, type, version, last_seen)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE type      = VALUES(type),
                                        version   = VALUES(version),
                                        last_seen = VALUES(last_seen)""";

        query(insert)
                .single(call().bind(nodeId, UUID_BYTES).bind(type).bind(version).bind(seen, time()))
                .insert();
    }

    @Override
    protected void restartPending(UUID node, String settings) {
        @Language("mariadb")
        var update = """
                UPDATE node
                SET restart_pending = ?
                WHERE uid = ?""";

        query(update).single(call().bind(settings).bind(node, UUID_BYTES)).update();
    }

    @Override
    public Map<UUID, String> pendingRestarts() {
        @Language("mariadb")
        var select = """
                SELECT uid, restart_pending
                FROM node
                WHERE restart_pending IS NOT NULL
                  AND restart_pending <> ''""";

        var pending = new HashMap<UUID, String>();
        query(select)
                .single()
                .map(row -> Map.entry(row.get("uid", UUID_BYTES), row.getString("restart_pending")))
                .all()
                .forEach(entry -> pending.put(entry.getKey(), entry.getValue()));
        return pending;
    }

    @Override
    public List<Node> getPrimaryNodes() {
        @Language("mariadb")
        var select = """
                SELECT id, uid, type, version, last_seen
                FROM node
                WHERE type = 'PRIMARY'""";

        return query(select).single().map(this::node).all();
    }

    @Override
    public List<Node> nodes() {
        @Language("mariadb")
        var select = """
                SELECT id, uid, type, version, last_seen
                FROM node
                ORDER BY id""";

        return query(select).single().map(this::node).all();
    }

    @Override
    protected boolean deleteNode(UUID uid) {
        @Language("mariadb")
        var deleteConfiguration = """
                DELETE FROM node_configuration
                WHERE node_id = (SELECT id FROM node WHERE uid = ?)""";
        @Language("mariadb")
        var deleteNode = """
                DELETE FROM node
                WHERE uid = ?""";

        @Language("mariadb")
        var deleteApplied = """
                DELETE FROM settings_applied
                WHERE node_uid = ?""";

        query(deleteConfiguration).single(call().bind(uid, UUID_BYTES)).delete();
        query(deleteApplied).single(call().bind(uid, UUID_BYTES)).delete();
        return query(deleteNode).single(call().bind(uid, UUID_BYTES)).delete().changed();
    }

    private Node node(Row row) throws SQLException {
        return new Node(
                row.getInt("id"),
                row.get("uid", UUID_BYTES),
                NodeType.valueOf(row.getString("type")),
                row.getString("version"),
                readLastSeen(row));
    }

    /**
     * @return adapter for times
     */
    protected Adapter<LocalDateTime> time() {
        return TimeColumns.TIMESTAMP;
    }

    /**
     * Reads the time a node was last seen.
     *
     * @param row row of the node
     * @return the time, or null before it was recorded
     * @throws SQLException when the column can not be read
     */
    protected @Nullable LocalDateTime readLastSeen(Row row) throws SQLException {
        var timestamp = row.getObject("last_seen", LocalDateTime.class);
        return timestamp == null ? null : timestamp;
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

        query(insert).single(call().bind(getNodeId()).bind(path).bind(content)).insert();
    }

    @Override
    public Optional<SettingsRevision> latestSettings(String path) {
        @Language("mariadb")
        var select = """
                SELECT path, revision, content, hash
                FROM settings
                WHERE path = ?
                ORDER BY revision DESC
                LIMIT 1""";

        return query(select)
                .single(call().bind(path))
                .map(row -> new SettingsRevision(
                        row.getString("path"), row.getInt("revision"), row.getString("content"), row.getString("hash")))
                .first();
    }

    @Override
    public Optional<SettingsRevision> settingsAt(String path, int revision) {
        @Language("mariadb")
        var select = """
                SELECT path, revision, content, hash
                FROM settings
                WHERE path = ?
                  AND revision = ?""";

        return query(select)
                .single(call().bind(path).bind(revision))
                .map(row -> new SettingsRevision(
                        row.getString("path"), row.getInt("revision"), row.getString("content"), row.getString("hash")))
                .first();
    }

    @Override
    public List<SettingsChange> settingsHistory(String path, int offset, int limit) {
        @Language("mariadb")
        var select = """
                SELECT revision, actor, node_uid, changed_at
                FROM settings
                WHERE path = ?
                ORDER BY revision DESC
                LIMIT ? OFFSET ?""";

        return query(select)
                .single(call().bind(path).bind(limit).bind(offset))
                .map(row -> new SettingsChange(
                        row.getInt("revision"),
                        row.getString("actor"),
                        row.get("node_uid", UUID_BYTES),
                        changedAt(row)))
                .all();
    }

    @Override
    public List<AppliedSettings> settingsApplied() {
        @Language("mariadb")
        var select = """
                SELECT node_uid, path, revision
                FROM settings_applied""";

        return query(select)
                .single()
                .map(row -> new AppliedSettings(
                        row.get("node_uid", UUID_BYTES), row.getString("path"), row.getInt("revision")))
                .all();
    }

    /**
     * Reads when a revision was stored.
     *
     * @param row row of the revision
     * @return the time in UTC
     * @throws SQLException when the column can not be read
     */
    protected LocalDateTime changedAt(Row row) throws SQLException {
        return row.getObject("changed_at", LocalDateTime.class);
    }

    @Override
    public OptionalInt appliedRevision(String path) {
        @Language("mariadb")
        var select = """
                SELECT revision
                FROM settings_applied
                WHERE node_uid = ?
                  AND path = ?""";

        return query(select)
                .single(call().bind(nodeUid(), UUID_BYTES).bind(path))
                .map(row -> row.getInt("revision"))
                .first()
                .map(OptionalInt::of)
                .orElse(OptionalInt.empty());
    }

    @Override
    public Map<String, Integer> settingsRevisions() {
        @Language("mariadb")
        var select = """
                SELECT path, MAX(revision) AS revision
                FROM settings
                GROUP BY path""";

        var revisions = new HashMap<String, Integer>();
        query(select)
                .single()
                .map(row -> Map.entry(row.getString("path"), row.getInt("revision")))
                .all()
                .forEach(entry -> revisions.put(entry.getKey(), entry.getValue()));
        return revisions;
    }

    @Override
    public void appliedSettings(String path, int revision) {
        @Language("mariadb")
        var upsert = """
                INSERT INTO settings_applied(node_uid, path, revision, applied_at)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE revision   = VALUES(revision),
                                        applied_at = VALUES(applied_at)""";

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
        @Language("mariadb")
        var insert = """
                INSERT IGNORE INTO settings(path, revision, content, hash, node_uid, actor, changed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)""";

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
