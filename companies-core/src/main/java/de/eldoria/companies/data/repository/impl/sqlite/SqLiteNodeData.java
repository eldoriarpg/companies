package de.eldoria.companies.data.repository.impl.sqlite;

import de.eldoria.companies.configuration.elements.NodeSettings;
import de.eldoria.companies.data.repository.impl.postgres.PostgresNodeData;

import java.util.concurrent.ExecutorService;

public class SqLiteNodeData extends PostgresNodeData {
    public SqLiteNodeData(NodeSettings nodeSettings) {
        super(nodeSettings);
    }
}
