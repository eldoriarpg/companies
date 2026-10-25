/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements;

import de.eldoria.companies.configuration.elements.dashboard.NodeDashboardSettings;

import java.util.UUID;

@SuppressWarnings("FieldMayBeFinal")
public class NodeSettings {
    private static final int MAX_NAME = 32;

    private NodeType nodeType = NodeType.PRIMARY;
    private UUID nodeUid = UUID.randomUUID();
    private String name = "";
    private NodeDashboardSettings dashboard = new NodeDashboardSettings();
    private NodeOverrides overrides = new NodeOverrides();

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
     * The dashboard key or server token as configured for this node, see {@link NodeDashboardSettings#serverKey()}.
     *
     * @return the key, empty for none
     */
    public String dashboardKey() {
        return dashboard == null ? "" : dashboard.serverKey();
    }

    /**
     * @return the shared settings this server sets for itself
     */
    public NodeOverrides overrides() {
        return overrides == null ? new NodeOverrides() : overrides;
    }
}
