/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.hooks.sanitizer;

import de.eldoria.companies.jobs.hooks.SchematicCleaner;
import de.eldoria.schematicsanitizer.sanitizer.Sanitizer;
import de.eldoria.schematicsanitizer.sanitizer.settings.Settings;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Cleans uploaded schematics with Schematic Sanitizer, by its default rules: command and structure blocks, the
 * contents of containers, loot tables, text with click commands and creatures go. Its rules are read when the cleaner
 * is made, so a FastAsyncWorldEdit the sanitizer does not fit fails while hooks load rather than on the first upload.
 */
public final class SanitizerCleaner implements SchematicCleaner {
    private final Settings settings = Settings.DEFAULT;

    @Override
    public Cleaned clean(Path in, Path out) throws IOException {
        var report = Sanitizer.create(in, settings).fix(out);
        return new Cleaned(
                report.blocks().size(),
                report.blocksNbt().size() + report.entitiesNbt().size(),
                report.entities().size());
    }
}
