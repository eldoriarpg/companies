/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements;

import java.util.UUID;

@SuppressWarnings("FieldMayBeFinal")
public class NodeSettings {
    private NodeType nodeType = NodeType.PRIMARY;
    private UUID nodeUid = UUID.randomUUID();

    public NodeType nodeType() {
        return nodeType;
    }

    public UUID nodeUid() {
        return nodeUid;
    }
}
