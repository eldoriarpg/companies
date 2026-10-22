/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.notifications;

import java.util.Collection;

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
    LEVEL(6, true);

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
