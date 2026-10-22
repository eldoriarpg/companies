/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.notifications;

import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * The kinds of notifications sent through {@link NotificationService#send}.
 * <p>
 * Ids are stable and never reused; new types append. Ids below 20 are reserved for the notifications that are still
 * sent as plain messages. The arguments a type expects are named in its documentation and used as placeholders in its
 * message.
 */
public enum NotificationType {
    /**
     * A message that was rendered before it was stored. It has no locale key; the serialized message is its argument
     * {@link NotificationArgs#LEGACY_MESSAGE}.
     */
    LEGACY(0, "", NotificationCategory.COMPANY, false, null),
    /**
     * A player was invited. Arguments: {@code company_name}, {@code player_name} (inviter).
     */
    INVITE_RECEIVED(20, "notification.inviteReceived", NotificationCategory.INVITES, true, "/company invites"),
    /**
     * An invite was taken back. Arguments: {@code company_name}.
     */
    INVITE_REVOKED(21, "notification.inviteRevoked", NotificationCategory.INVITES, false, null),
    /**
     * The invited player joined. Arguments: {@code player_name} (invited player).
     */
    INVITE_ACCEPTED(22, "notification.inviteAccepted", NotificationCategory.INVITES, false, null),
    /**
     * The invited player declined. Arguments: {@code player_name} (invited player).
     */
    INVITE_DECLINED(23, "notification.inviteDeclined", NotificationCategory.INVITES, false, null),
    /**
     * An invite the player received expired. Arguments: {@code company_name}.
     */
    INVITE_EXPIRED_TARGET(24, "notification.inviteExpiredTarget", NotificationCategory.INVITES, false, null),
    /**
     * An invite the player sent expired. Arguments: {@code player_name} (invited player).
     */
    INVITE_EXPIRED_INVITER(25, "notification.inviteExpiredInviter", NotificationCategory.INVITES, false, null),
    /**
     * A player applied to the company. Arguments: {@code player_name} (applicant).
     */
    APPLICATION_RECEIVED(
            26, "notification.applicationReceived", NotificationCategory.INVITES, false, "/company applications"),
    /**
     * The player's application was accepted. Arguments: {@code company_name}.
     */
    APPLICATION_ACCEPTED(27, "notification.applicationAccepted", NotificationCategory.INVITES, true, null),
    /**
     * The player's application was denied. Arguments: {@code company_name}.
     */
    APPLICATION_DENIED(28, "notification.applicationDenied", NotificationCategory.INVITES, false, null),
    /**
     * The player's application expired. Arguments: {@code company_name}.
     */
    APPLICATION_EXPIRED(29, "notification.applicationExpired", NotificationCategory.INVITES, false, null),
    /**
     * A member changed the company profile. Arguments: {@code player_name} (member).
     */
    PROFILE_CHANGED(30, "notification.profileChanged", NotificationCategory.COMPANY, false, null);

    private final int id;
    private final String key;
    private final NotificationCategory category;
    private final boolean important;

    @Nullable
    private final String action;

    NotificationType(int id, String key, NotificationCategory category, boolean important, @Nullable String action) {
        this.id = id;
        this.key = key;
        this.category = category;
        this.important = important;
        this.action = action;
    }

    /**
     * The type with the given id.
     *
     * @param id stored id
     * @return type, or empty when no type has this id
     */
    public static Optional<NotificationType> byId(int id) {
        for (var type : values()) if (type.id == id) return Optional.of(type);
        return Optional.empty();
    }

    /**
     * The stable id of this type.
     *
     * @return id
     */
    public int id() {
        return id;
    }

    /**
     * The locale key of the message.
     *
     * @return locale key, empty for {@link #LEGACY}
     */
    public String key() {
        return key;
    }

    /**
     * The group of this type.
     *
     * @return category
     */
    public NotificationCategory category() {
        return category;
    }

    /**
     * Whether the notification needs the player's attention.
     *
     * @return true for important notifications
     */
    public boolean important() {
        return important;
    }

    /**
     * The command a button next to the message runs. Placeholders {@code <name>} are replaced by the arguments.
     *
     * @return command with leading slash, or empty when the message has no button
     */
    public Optional<String> action() {
        return Optional.ofNullable(action);
    }
}
