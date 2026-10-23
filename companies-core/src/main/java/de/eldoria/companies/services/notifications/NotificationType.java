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
 * Ids are stable and never reused; new types append. The arguments a type expects are named in its documentation and
 * used as placeholders in its message and action.
 */
public enum NotificationType {
    /**
     * A message that was rendered before it was stored. It has no locale key; the serialized message is its argument
     * {@link NotificationArgs#LEGACY_MESSAGE}.
     */
    LEGACY(0, "", NotificationCategory.COMPANY, false, null),
    /**
     * The player's company was disbanded. No arguments.
     */
    COMPANY_DISBANDED(1, "notification.companyDisband", NotificationCategory.COMPANY, true, null),
    /**
     * A player joined the company. Arguments: {@code player_name}.
     */
    MEMBER_JOINED(2, "notification.companyJoin", NotificationCategory.MEMBERS, false, null),
    /**
     * A member left the company. Arguments: {@code player_name}.
     */
    MEMBER_LEFT(3, "notification.companyLeave", NotificationCategory.MEMBERS, false, null),
    /**
     * A member was kicked from the company. Arguments: {@code player_name}.
     */
    MEMBER_KICKED(4, "notification.kicked", NotificationCategory.MEMBERS, false, null),
    /**
     * The player was kicked from their company. No arguments.
     */
    KICKED(5, "notification.kickedTarget", NotificationCategory.COMPANY, true, null),
    /**
     * The company accepted an order. Arguments: {@code order_name}, {@code order_id}.
     */
    ORDER_ACCEPTED(
            6,
            "notification.orderAccepted",
            NotificationCategory.ORDERS,
            false,
            new Action("/company order info <order_id>", "words.info")),
    /**
     * The company gave an order up. Arguments: {@code order_name}.
     */
    ORDER_ABORTED(7, "notification.orderCanceled", NotificationCategory.ORDERS, false, null),
    /**
     * An order of the company expired. Arguments: {@code order_name}.
     */
    ORDER_EXPIRED(8, "notification.companyOrderExpired", NotificationCategory.ORDERS, true, null),
    /**
     * The company delivered an order. Arguments: {@code order_name}.
     */
    ORDER_DELIVERED(9, "notification.orderDelivered", NotificationCategory.ORDERS, false, null),
    /**
     * The player was paid for an order. Arguments: {@code amount}, {@code order_name}.
     */
    PAYMENT(10, "notification.orderPayment", NotificationCategory.PAYMENTS, false, null),
    /**
     * The player was paid for an order after tax. Arguments: {@code amount}, {@code order_name}, {@code gross},
     * {@code tax}.
     */
    PAYMENT_TAXED(11, "notification.orderPaymentTaxed", NotificationCategory.PAYMENTS, false, null),
    /**
     * The company reached a higher level. Arguments: {@code new_level}.
     */
    LEVEL_UP(12, "notification.companyLevelUp", NotificationCategory.LEVEL, false, null),
    /**
     * The company fell to a lower level. Arguments: {@code new_level}.
     */
    LEVEL_DOWN(13, "notification.companyLevelDown", NotificationCategory.LEVEL, false, null),
    /**
     * The player's order was delivered. Arguments: {@code order_name}, {@code order_id}.
     */
    MY_ORDER_DONE(
            14,
            "notification.orderDone",
            NotificationCategory.MY_ORDERS,
            true,
            new Action("/order receive <order_id>", "notification.receiveItems")),
    /**
     * The player's order was removed because nobody accepted it. Arguments: {@code order_name}.
     */
    MY_ORDER_REMOVED(15, "notification.orderRemoved", NotificationCategory.MY_ORDERS, true, null),
    /**
     * A company accepted the player's order. Arguments: {@code order_name}, {@code order_id}.
     */
    MY_ORDER_ACCEPTED(
            16,
            "notification.ownerOrderAccepted",
            NotificationCategory.MY_ORDERS,
            false,
            new Action("/order info <order_id>", "words.info")),
    /**
     * The player's order is open again. Arguments: {@code order_name}, {@code order_id}.
     */
    MY_ORDER_REOPENED(
            17,
            "notification.ownerOrderOpen",
            NotificationCategory.MY_ORDERS,
            false,
            new Action("/order info <order_id>", "words.info")),
    /**
     * The player's order is removed soon unless somebody accepts it. Arguments: {@code order_name}, {@code duration},
     * {@code order_id}.
     */
    MY_ORDER_PURGE_WARNING(
            18,
            "notification.orderPurgeWarning",
            NotificationCategory.MY_ORDERS,
            true,
            new Action("/order info <order_id>", "words.info")),
    /**
     * The player became the owner of their company. Arguments: {@code company}.
     */
    OWNERSHIP_TRANSFERRED(19, "notification.ownerTransferred", NotificationCategory.COMPANY, true, null),
    /**
     * A player was invited. Arguments: {@code company_name}, {@code player_name} (inviter).
     */
    INVITE_RECEIVED(
            20,
            "notification.inviteReceived",
            NotificationCategory.INVITES,
            true,
            new Action("/company invites", "words.open")),
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
            26,
            "notification.applicationReceived",
            NotificationCategory.INVITES,
            false,
            new Action("/company applications", "words.open")),
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
    PROFILE_CHANGED(30, "notification.profileChanged", NotificationCategory.COMPANY, false, null),
    /**
     * The player was paid for an order after tax and the company's treasury share. Arguments: {@code amount},
     * {@code order_name}, {@code gross}, {@code tax}, {@code share}.
     */
    PAYMENT_SHARED(31, "notification.orderPaymentShared", NotificationCategory.PAYMENTS, false, null),
    /**
     * A member changed the share of payouts that goes to the treasury. Arguments: {@code player_name} (member),
     * {@code percent}.
     */
    TREASURY_SHARE_CHANGED(32, "notification.treasuryShareChanged", NotificationCategory.COMPANY, false, null),
    /**
     * The treasury of the player's disbanded or deleted company was paid out to them. Arguments: {@code amount},
     * {@code company_name}.
     */
    TREASURY_DISBAND_PAYOUT(33, "notification.treasuryDisbandPayout", NotificationCategory.COMPANY, true, null),
    /**
     * A repeating order posted a copy. Arguments: {@code series_name}, {@code amount} (charged), {@code copy} (number
     * of the copy, with the count limit when there is one, such as {@code 3/5}), {@code order_id}.
     */
    SERIES_POSTED(
            34,
            "notification.seriesPosted",
            NotificationCategory.MY_ORDERS,
            false,
            new Action("/order info <order_id>", "words.info")),
    /**
     * A repeating order holds its next copy back because the player reached the order limit. Arguments:
     * {@code series_name}, {@code series_id}.
     */
    SERIES_WAITING_LIMIT(
            35,
            "notification.seriesWaitingLimit",
            NotificationCategory.MY_ORDERS,
            false,
            new Action("/order series info <series_id>", "words.info")),
    /**
     * A repeating order stopped; its info shows why. Arguments: {@code series_name}, {@code series_id}.
     */
    SERIES_STOPPED(
            36,
            "notification.seriesStopped",
            NotificationCategory.MY_ORDERS,
            true,
            new Action("/order series info <series_id>", "words.info")),
    /**
     * A repeating order posted its last copy. Arguments: {@code series_name}.
     */
    SERIES_FINISHED(37, "notification.seriesFinished", NotificationCategory.MY_ORDERS, false, null),
    /**
     * A new order matches saved alerts of the company. Arguments: {@code alert_name} (names of the matching alerts),
     * {@code order_name}, {@code contents}, {@code amount} (price), {@code net} (after the company's tax).
     */
    ORDER_ALERT(
            38,
            "notification.orderAlert",
            NotificationCategory.ALERTS,
            false,
            new Action("/company alerts show <alert_name>", "words.show")),
    /**
     * More orders matched an alert while it was rate limited. Arguments: {@code alert_name}, {@code count}.
     */
    ORDER_ALERT_DIGEST(
            39,
            "notification.orderAlertDigest",
            NotificationCategory.ALERTS,
            false,
            new Action("/company alerts show <alert_name>", "words.show"));

    private final int id;
    private final String key;
    private final NotificationCategory category;
    private final boolean important;

    @Nullable
    private final Action action;

    NotificationType(int id, String key, NotificationCategory category, boolean important, @Nullable Action action) {
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
     * The button next to the message.
     *
     * @return the button, or empty when the message has none
     */
    public Optional<Action> action() {
        return Optional.ofNullable(action);
    }

    /**
     * A button running a real command, so it still works when the notification is read days later on another
     * server.
     *
     * @param command command with leading slash; placeholders {@code <name>} are replaced by the arguments
     * @param label   locale key of the button text
     */
    public record Action(String command, String label) {}
}
