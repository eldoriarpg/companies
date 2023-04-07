/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements;

@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal"})
public class GeneralSettings {
    private String language = "en_US";
    private int orderCheckInterval = 60;

    public GeneralSettings() {
    }

    public int orderCheckInterval() {
        return orderCheckInterval;
    }

    public String language() {
        return language;
    }
}
