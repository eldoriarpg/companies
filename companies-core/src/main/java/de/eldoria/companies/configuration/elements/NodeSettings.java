/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import de.eldoria.companies.configuration.elements.dashboard.NodeDashboardSettings;
import org.jetbrains.annotations.Nullable;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

@SuppressWarnings("FieldMayBeFinal")
public class NodeSettings {
    private static final int MAX_NAME = 32;

    private NodeType nodeType = NodeType.PRIMARY;
    private UUID nodeUid = UUID.randomUUID();
    private String name = "";
    private String proxyServer = "";
    private NodeDashboardSettings dashboard = new NodeDashboardSettings();

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private @Nullable JsonNode overrides;

    @JsonIgnore
    private transient @Nullable NodeOverrides parsedOverrides;

    public NodeType nodeType() {
        return nodeType;
    }

    public UUID nodeUid() {
        return nodeUid;
    }

    /**
     * The name admins gave this node, shown where the dashboard lets them pick a server.
     *
     * @return the name, at most {@value #MAX_NAME} characters, empty for none
     */
    public String name() {
        if (name == null) return "";
        var stripped = name.strip();
        return stripped.length() > MAX_NAME ? stripped.substring(0, MAX_NAME) : stripped;
    }

    /**
     * The name of this server in the proxy's configuration, such as {@code survival} in Velocity's {@code [servers]}.
     * Players are sent here through the proxy to reach a company home set on this server.
     *
     * @return the name, empty when this server is not behind a proxy or has no name there
     */
    public String proxyServer() {
        return proxyServer == null ? "" : proxyServer.strip();
    }

    /**
     * @param proxyServer the name of this server in the proxy's configuration, empty for none
     */
    public void proxyServer(String proxyServer) {
        this.proxyServer = proxyServer == null ? "" : proxyServer.strip();
    }

    /**
     * Names this node; the dashboard shows the new name after it reconnects.
     *
     * @param name the name, at most {@value #MAX_NAME} characters, empty for none
     */
    public void name(String name) {
        var stripped = name == null ? "" : name.strip();
        this.name = stripped.length() > MAX_NAME ? stripped.substring(0, MAX_NAME) : stripped;
    }

    /**
     * The dashboard key or server token as configured for this node, see {@link NodeDashboardSettings#serverKey()}.
     *
     * @return the key, empty for none
     */
    public String dashboardKey() {
        return dashboard == null ? "" : dashboard.serverKey();
    }

    /**
     * The {@code overrides} section, read leniently, so a typo in it is reported instead of making the file unreadable.
     * It is written back as it was.
     *
     * @return the shared settings this server sets for itself
     */
    public NodeOverrides overrides() {
        var parsed = parsedOverrides;
        if (parsed == null) {
            if (overrides != null && overrides.isNull()) overrides = null;
            parsed = NodeOverrides.parse(overrides);
            parsedOverrides = parsed;
        }
        return parsed;
    }
}
