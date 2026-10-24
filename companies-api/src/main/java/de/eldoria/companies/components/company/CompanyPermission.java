/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.company;

import net.kyori.adventure.translation.Translatable;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A permission of a member within a company. Permissions are stored as a bit mask.
 */
public enum CompanyPermission implements Translatable {
    /**
     * Owns the company. The owner has every permission and can not be kicked.
     */
    OWNER(0),
    /**
     * Invites players and accepts or denies applications.
     */
    INVITE(1),
    /**
     * Removes members from the company.
     */
    KICK(2),
    /**
     * Accepts and aborts orders for the company.
     */
    MANAGE_ORDERS(3),
    /**
     * Grants and revokes the permissions of other members.
     */
    MANAGE_PERMISSIONS(4),
    /**
     * Allows changing the description, tag, colour and join mode of the company.
     */
    EDIT_PROFILE(5),
    /**
     * Withdraws money from the company treasury and sets the share of payouts that goes to it.
     */
    TREASURY(6);

    private final long mask;
    private final int rawMask;

    CompanyPermission(int mask) {
        this.mask = toBitMask(mask);
        rawMask = mask;
    }

    private long toBitMask(int mask) {
        return 1L << mask;
    }

    /**
     * Combines permissions into a bit mask.
     *
     * @param permissions permissions to combine
     * @return mask with the bits of the permissions
     */
    public static long composePermissions(CompanyPermission... permissions) {
        return Arrays.stream(permissions).mapToLong(CompanyPermission::mask).sum();
    }

    /**
     * The bit of this permission in a permission mask.
     *
     * @return mask with only this permission
     */
    public long mask() {
        return mask;
    }

    /**
     * The permissions contained in a mask.
     *
     * @param mask permission mask
     * @return permissions
     */
    public static Set<CompanyPermission> getPermissions(long mask) {
        return Arrays.stream(values()).filter(p -> p.hasPermission(mask)).collect(Collectors.toSet());
    }

    /**
     * Whether a mask contains this permission.
     *
     * @param mask permission mask
     * @return true when the bit is set
     */
    public boolean hasPermission(long mask) {
        return (mask & (mask())) != 0L;
    }

    /**
     * Whether a mask contains at least one of the permissions.
     *
     * @param mask        permission mask
     * @param permissions permissions to check
     * @return true when one bit is set
     */
    public static boolean hasAnyPermission(long mask, CompanyPermission... permissions) {
        for (var permission : permissions) if (permission.hasPermission(mask)) return true;
        return false;
    }

    /**
     * Whether a mask contains all of the permissions.
     *
     * @param mask        permission mask
     * @param permissions permissions to check
     * @return true when every bit is set
     */
    public static boolean hasPermission(long mask, CompanyPermission... permissions) {
        for (var permission : permissions) if (!permission.hasPermission(mask)) return false;
        return true;
    }

    /**
     * The index of the bit of this permission, as stored in the database.
     *
     * @return bit index
     */
    public int rawMask() {
        return rawMask;
    }

    @Override
    public @NotNull String translationKey() {
        return "enums.companyPermission." + name().toLowerCase(Locale.ROOT);
    }
}
