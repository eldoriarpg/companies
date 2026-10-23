/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * Resolves a configured secret that may be written as {@code env:NAME}.
 * <p>
 * A value starting with {@code env:} (in any case) is read from the environment variable {@code NAME}, so only the
 * name of the variable is stored in the file and, for synced files, in the database. Any other value is used as
 * written. Values are trimmed. Nothing here ever reports the resolved value.
 */
public final class EnvReference {
    private static final String PREFIX = "env:";

    private EnvReference() {}

    /**
     * Resolves a configured value.
     *
     * @param configured  the configured value or {@code env:NAME}
     * @param environment reads an environment variable, null when it is unset
     * @param missing     receives the name of a referenced variable that is unset or blank
     * @return the trimmed value, which may be empty when it was configured empty; empty when a referenced variable is
     *     unset or blank
     */
    public static Optional<String> resolve(
            String configured, UnaryOperator<String> environment, Consumer<String> missing) {
        var value = configured == null ? "" : configured.trim();
        if (!value.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) return Optional.of(value);
        var name = value.substring(PREFIX.length()).trim();
        var resolved = name.isEmpty() ? null : environment.apply(name);
        if (resolved == null || resolved.isBlank()) {
            missing.accept(name);
            return Optional.empty();
        }
        return Optional.of(resolved.trim());
    }
}
