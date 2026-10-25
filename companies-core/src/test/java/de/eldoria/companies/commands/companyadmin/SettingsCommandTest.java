/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.companyadmin;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.wrapper.nodes.SettingsChange;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SettingsCommandTest {
    private static final UUID NODE = UUID.fromString("0badc0de-0000-0000-0000-000000000001");

    @Test
    void everyFileGetsItsOwnRowWithItsMarkers() {
        var rows = SettingsCommand.statusRows(List.of(
                new Configuration.FileStatus("config.yml", 3, 3, false, List.of()),
                new Configuration.FileStatus("company.yml", 5, 4, true, List.of(NODE))));

        assertEquals(
                List.of(
                        "<i18n:companyadmin.settings.file>",
                        "<i18n:companyadmin.settings.file> <i18n:companyadmin.settings.edited>"
                                + " <i18n:companyadmin.settings.behind>"),
                rows.stream().map(row -> row.build()).toList());
    }

    @Test
    void everyRevisionGetsItsOwnRow() {
        var changed = LocalDateTime.of(2026, 10, 25, 12, 0);
        var rows = SettingsCommand.historyRows(
                List.of(new SettingsChange(2, "Ada", NODE, changed), new SettingsChange(1, "startup", NODE, changed)));

        assertEquals(
                List.of("<i18n:companyadmin.settings.history.entry>", "<i18n:companyadmin.settings.history.entry>"),
                rows.stream().map(row -> row.build()).toList());
    }
}
