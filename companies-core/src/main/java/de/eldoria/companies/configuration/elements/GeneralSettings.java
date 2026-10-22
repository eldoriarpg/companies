/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements;

import com.fasterxml.jackson.annotation.JsonSetter;
import de.eldoria.companies.configuration.elements.generalsettings.Hooks;
import de.eldoria.companies.configuration.elements.generalsettings.UpdateSettings;

@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "RedundantNoArgConstructor", "CanBeFinal"})
public class GeneralSettings {
    private String language = "en_US";
    private int orderCheckInterval = 60;
    private UpdateSettings updates = new UpdateSettings();
    private boolean messageBlocking = true;
    private Hooks hooks = new Hooks();
    private int historyDays = 90;

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
        return hooks;
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
