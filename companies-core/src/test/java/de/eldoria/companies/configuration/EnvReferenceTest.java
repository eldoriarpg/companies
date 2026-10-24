/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvReferenceTest {
    private final Map<String, String> environment = Map.of("SECRET", " s3cr3t ", "BLANK", " ");
    private final List<String> missing = new ArrayList<>();

    private Optional<String> resolve(String configured) {
        return EnvReference.resolve(configured, environment::get, missing::add);
    }

    @Test
    void plainValueIsUsedTrimmed() {
        assertEquals(Optional.of("value"), resolve(" value "));
        assertEquals(Optional.of(""), resolve(""));
        assertEquals(Optional.of(""), resolve(null));
        assertTrue(missing.isEmpty());
    }

    @Test
    void referenceIsResolvedInAnyCase() {
        assertEquals(Optional.of("s3cr3t"), resolve("env:SECRET"));
        assertEquals(Optional.of("s3cr3t"), resolve("ENV: SECRET"));
        assertTrue(missing.isEmpty());
    }

    @Test
    void unsetOrBlankVariableIsReportedByName() {
        assertEquals(Optional.empty(), resolve("env:UNSET"));
        assertEquals(Optional.empty(), resolve("env:BLANK"));
        assertEquals(Optional.empty(), resolve("env:"));
        assertEquals(List.of("UNSET", "BLANK", ""), missing);
    }
}
