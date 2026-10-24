/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.notifications;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The group a notification belongs to. Players can mute the mutable categories; the muted ones are stored as a bit
 * mask, so the bit of a category never changes.
 */
public enum NotificationCategory {
    COMPANY(0, false),
    MEMBERS(1, true),
    ORDERS(2, true),
    MY_ORDERS(3, false),
    PAYMENTS(4, true),
    INVITES(5, false),
    LEVEL(6, true),
    ALERTS(7, true);

    private final int bitIndex;
    private final boolean mutable;

    NotificationCategory(int bitIndex, boolean mutable) {
        this.bitIndex = bitIndex;
        this.mutable = mutable;
    }

    /**
     * The mask of several categories.
     *
     * @param categories categories to combine
     * @return mask with the bit of every category set
     */
    public static long mask(Collection<NotificationCategory> categories) {
        var mask = 0L;
        for (var category : categories) mask |= category.bit();
        return mask;
    }

    /**
     * The categories players can mute, in declaration order.
     *
     * @return mutable categories
     */
    public static List<NotificationCategory> mutables() {
        return Arrays.stream(values()).filter(NotificationCategory::mutable).toList();
    }

    /**
     * The mutable category of a name, ignoring case.
     *
     * @param name name of the category
     * @return the category, or empty when no mutable category has this name
     */
    public static Optional<NotificationCategory> mutable(String name) {
        return mutables().stream()
                .filter(category -> category.name().equalsIgnoreCase(name))
                .findFirst();
    }

    /**
     * Whether a mask mutes this category. A set bit of a category that cannot be muted is ignored.
     *
     * @param mask mute mask
     * @return true when this category is mutable and its bit is set
     */
    public boolean mutedBy(long mask) {
        return mutable && in(mask);
    }

    /**
     * The locale key of the category's name.
     *
     * @return locale key
     */
    public String key() {
        return "enums.notificationCategory." + name().toLowerCase(Locale.ROOT);
    }

    /**
     * The bit of this category in a mute mask.
     *
     * @return bit
     */
    public long bit() {
        return 1L << bitIndex;
    }

    /**
     * Whether this category is muted in a mask.
     *
     * @param mask mute mask
     * @return true when the bit of this category is set
     */
    public boolean in(long mask) {
        return (mask & bit()) != 0;
    }

    /**
     * Whether players can mute this category.
     *
     * @return true for mutable categories
     */
    public boolean mutable() {
        return mutable;
    }
}
