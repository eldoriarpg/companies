/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements;

import de.eldoria.companies.configuration.elements.dashboard.NodeDashboardSettings;

import java.util.UUID;

@SuppressWarnings("FieldMayBeFinal")
public class NodeSettings {
    private NodeType nodeType = NodeType.PRIMARY;
    private UUID nodeUid = UUID.randomUUID();
    private NodeDashboardSettings dashboard = new NodeDashboardSettings();

    public NodeType nodeType() {
        return nodeType;
    }

    public UUID nodeUid() {
        return nodeUid;
    }

    /**
     * The dashboard key or server token as configured for this node, see {@link NodeDashboardSettings#serverKey()}.
     *
     * @return the configured key, {@code env:NAME} or empty
     */
    public String dashboardKey() {
        return dashboard == null ? "" : dashboard.serverKey();
    }
}
