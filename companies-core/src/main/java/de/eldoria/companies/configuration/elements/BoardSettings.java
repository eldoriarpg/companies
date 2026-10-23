/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements;

import de.eldoria.companies.configuration.elements.boardsettings.BoardPreset;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Order boards, the {@code boards} section of the config: signs and NPCs opening the order search, optionally with a
 * preset filter.
 * <p>
 * Preset names are matched ignoring case. The open order count on signs is refreshed every {@link #refresh()}, and
 * a few seconds after an order on this node changed.
 */
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "CanBeFinal"})
public class BoardSettings {
    private static final int MIN_REFRESH_SECONDS = 10;

    private boolean enabled = true;
    private int refreshSeconds = 60;
    private String signHeader = "[Companies]";
    private Map<String, BoardPreset> presets = examplePresets();

    public BoardSettings() {}

    private static Map<String, BoardPreset> examplePresets() {
        var examples = new LinkedHashMap<String, BoardPreset>();
        examples.put("iron", new BoardPreset("Iron", List.of("iron_ingot", "iron_block", "raw_iron"), true));
        return examples;
    }

    /**
     * Whether board signs work. Without them no chunk is scanned for signs. {@code /company board} works either way.
     *
     * @return true when board signs are enabled
     */
    public boolean enabled() {
        return enabled;
    }

    /**
     * Time between two refreshes of the open order counts on signs. At least ten seconds.
     *
     * @return refresh interval
     */
    public Duration refresh() {
        return Duration.ofSeconds(Math.max(MIN_REFRESH_SECONDS, refreshSeconds));
    }

    /**
     * The first line that turns a sign into a board, compared ignoring case.
     *
     * @return the header of board signs
     */
    public String signHeader() {
        return signHeader == null || signHeader.isBlank() ? "[Companies]" : signHeader.strip();
    }

    /**
     * @return the presets by their name in lower case, in the configured order
     */
    public Map<String, BoardPreset> presets() {
        var normalized = new LinkedHashMap<String, BoardPreset>();
        if (presets == null) return normalized;
        presets.forEach((name, preset) -> {
            if (name != null && preset != null && !name.isBlank()) {
                normalized.putIfAbsent(name.strip().toLowerCase(Locale.ROOT), preset);
            }
        });
        return Collections.unmodifiableMap(normalized);
    }

    /**
     * @param name name of the preset, in any case
     * @return the preset, empty when none has the name
     */
    public Optional<BoardPreset> preset(String name) {
        return Optional.ofNullable(presets().get(name.strip().toLowerCase(Locale.ROOT)));
    }
}
