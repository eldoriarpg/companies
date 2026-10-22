/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements;

/**
 * How notifications are kept, the {@code notifications} section of the config.
 */
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "CanBeFinal"})
public class NotificationSettings {
    private int retentionDays = 30;

    public NotificationSettings() {}

    /**
     * Days a notification stays in the inbox, read or not. At least one day.
     *
     * @return days to keep notifications
     */
    public int retentionDays() {
        return Math.max(1, retentionDays);
    }
}
