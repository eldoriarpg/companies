/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.sqlite;

import de.eldoria.companies.configuration.elements.NodeSettings;
import de.eldoria.companies.data.repository.impl.postgres.PostgresNodeData;

import java.util.concurrent.ExecutorService;

public class SqLiteNodeData extends PostgresNodeData {
    public SqLiteNodeData(NodeSettings nodeSettings) {
        super(nodeSettings);
    }
}
