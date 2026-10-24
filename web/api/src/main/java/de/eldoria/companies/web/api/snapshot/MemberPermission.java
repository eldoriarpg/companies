/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api.snapshot;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

/**
 * A permission of a member inside their company.
 * <p>
 * Open: a value added by a newer peer reads as {@link #UNKNOWN}. Written as the constant's name.
 */
public enum MemberPermission {
    /**
     * Owns the company and holds every permission.
     */
    OWNER,
    /**
     * Invites players and handles applications.
     */
    INVITE,
    /**
     * Removes members.
     */
    KICK,
    /**
     * Accepts and aborts orders for the company.
     */
    MANAGE_ORDERS,
    /**
     * Changes the permissions of other members.
     */
    MANAGE_PERMISSIONS,
    /**
     * Edits the description, tag and colour.
     */
    EDIT_PROFILE,
    /**
     * Withdraws from the treasury and changes the share.
     */
    TREASURY,
    /**
     * A value this version does not know, sent by a newer peer.
     */
    @JsonEnumDefaultValue
    UNKNOWN
}
