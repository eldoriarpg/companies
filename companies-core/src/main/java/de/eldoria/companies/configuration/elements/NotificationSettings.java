/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements;

import java.util.Optional;

/**
 * How notifications are delivered and kept, the {@code notifications} section of the config.
 */
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "CanBeFinal"})
public class NotificationSettings {
    private static final int MAX_PAGE_SIZE = 50;

    private int pollSeconds = 10;
    private int retentionDays = 30;
    private int pageSize = 10;
    private boolean actionBar = true;
    private String sound = "minecraft:entity.experience_orb.pickup";

    public NotificationSettings() {}

    /**
     * Seconds between two looks for notifications sent to the players online here from another server. At least one
     * second.
     *
     * @return seconds between two polls
     */
    public int pollSeconds() {
        return Math.max(1, pollSeconds);
    }

    /**
     * Days a notification stays in the inbox, read or not. At least one day.
     *
     * @return days to keep notifications
     */
    public int retentionDays() {
        return Math.max(1, retentionDays);
    }

    /**
     * Notifications on one page of the inbox, between 1 and 50.
     *
     * @return entries per page
     */
    public int pageSize() {
        return Math.clamp(pageSize, 1, MAX_PAGE_SIZE);
    }

    /**
     * Whether important notifications are also shown on the action bar.
     *
     * @return true to use the action bar
     */
    public boolean actionBar() {
        return actionBar;
    }

    /**
     * The sound played with an important notification.
     *
     * @return key of the sound, or empty when no sound is played
     */
    public Optional<String> sound() {
        return sound == null || sound.isBlank() ? Optional.empty() : Optional.of(sound.trim());
    }
}
