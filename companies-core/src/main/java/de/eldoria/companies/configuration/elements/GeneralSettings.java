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
