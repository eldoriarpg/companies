/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.sqlite;

import de.eldoria.companies.data.repository.ASavedItemData;
import de.eldoria.companies.data.repository.SavedItemDataContract;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;

import javax.sql.DataSource;

class SqLiteSavedItemDataTest extends SavedItemDataContract {
    @TempDir
    static Path directory;

    @Override
    protected DataSource start() throws Exception {
        return SqLiteTestDatabase.create(directory);
    }

    @Override
    protected void stop() {}

    @Override
    protected ASavedItemData data(ExecutorService executor) {
        return new SqLiteSavedItemData(SqLiteTestDatabase.mapper(), executor);
    }
}
