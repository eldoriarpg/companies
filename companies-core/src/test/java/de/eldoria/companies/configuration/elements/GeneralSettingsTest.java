/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneralSettingsTest {
    private final ObjectMapper mapper = YAMLMapper.builder()
                                                  .changeDefaultVisibility(visibility -> visibility.withFieldVisibility(JsonAutoDetect.Visibility.ANY))
                                                  .build();

    @Test
    void oldUpdateSwitchBecomesNotice() {
        var settings = mapper.readValue("checkUpdates: false", GeneralSettings.class);

        assertFalse(settings.updates().notice());
    }

    @Test
    void oldUpdateSwitchIsNotWrittenAgain() {
        var yaml = mapper.writeValueAsString(mapper.readValue("checkUpdates: true", GeneralSettings.class));

        assertFalse(yaml.contains("checkUpdates"));
        assertTrue(yaml.contains("notice: true"));
    }
}
