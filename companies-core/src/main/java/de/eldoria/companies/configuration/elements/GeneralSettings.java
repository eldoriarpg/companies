/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements;

import com.fasterxml.jackson.annotation.JsonSetter;
import de.eldoria.companies.configuration.elements.generalsettings.Hooks;
import de.eldoria.companies.configuration.elements.generalsettings.UpdateSettings;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Optional;

@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "RedundantNoArgConstructor", "CanBeFinal"})
public class GeneralSettings {
    private String language = "en_US";
    private int orderCheckInterval = 60;
    private UpdateSettings updates = new UpdateSettings();
    private boolean messageBlocking = true;
    private Hooks hooks = new Hooks();
    private int historyDays = 90;
    private String timezone = "";

    public GeneralSettings() {}

    public int orderCheckInterval() {
        return orderCheckInterval;
    }

    public String language() {
        return language;
    }

    public UpdateSettings updates() {
        return updates;
    }

    public Hooks hooks() {
        return hooks == null ? new Hooks() : hooks;
    }

    public boolean messageBlocking() {
        return messageBlocking;
    }

    /**
     * Days the order history keeps an entry after the order ended. At least one day.
     *
     * @return days to keep history entries
     */
    public int historyDays() {
        return Math.max(1, historyDays);
    }

    /**
     * The server's time zone, an IANA name such as {@code Europe/Berlin}. It decides how times are shown and where a
     * day starts; stored times are UTC either way. Empty uses the machine's zone.
     *
     * @return the zone, empty when none is set or the name is no zone
     */
    public Optional<ZoneId> timezone() {
        if (timezone == null || timezone.isBlank()) return Optional.empty();
        try {
            return Optional.of(ZoneId.of(timezone.strip()));
        } catch (DateTimeException e) {
            return Optional.empty();
        }
    }

    /**
     * @return the configured name of the zone when it names no zone, for a warning
     */
    public Optional<String> invalidTimezone() {
        if (timezone == null || timezone.isBlank() || timezone().isPresent()) return Optional.empty();
        return Optional.of(timezone);
    }

    /**
     * Reads the update switch of configs written before the update settings existed. It becomes the start value of
     * {@link UpdateSettings#notice()} and is not written again.
     *
     * @param checkUpdates whether the old config looked for new versions
     */
    @JsonSetter("checkUpdates")
    private void legacyCheckUpdates(boolean checkUpdates) {
        updates.notice(checkUpdates);
    }
}
