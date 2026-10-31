/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.notifications;

import org.jetbrains.annotations.Nullable;

import java.util.List;
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
     * An admin took an order away from the company; the delivered items went back to the workers. Arguments:
     * {@code order_name}.
     */
    ORDER_UNCLAIMED_BY_ADMIN(31, "notification.orderUnclaimedByAdmin", NotificationCategory.ORDERS, true, null),
    /**
     * An admin deleted an order the company worked on; the delivered items went back to the workers. Arguments:
     * {@code order_name}.
     */
    ORDER_CANCELLED_BY_ADMIN(32, "notification.orderCancelledByAdmin", NotificationCategory.ORDERS, true, null),
    /**
     * An admin deleted the player's order and refunded it. Arguments: {@code order_name}, {@code amount}.
     */
    MY_ORDER_CANCELLED_BY_ADMIN(
            33, "notification.ownerOrderCancelledByAdmin", NotificationCategory.MY_ORDERS, true, null),
    /**
     * An admin deleted the player's order without a refund. Arguments: {@code order_name}.
     */
    MY_ORDER_CANCELLED_BY_ADMIN_KEPT(
            34, "notification.ownerOrderCancelledByAdminKept", NotificationCategory.MY_ORDERS, true, null),
    /**
     * An admin marked the player's partly delivered order as delivered and refunded the missing items. Arguments:
     * {@code order_name}, {@code amount}.
     */
    MY_ORDER_PARTLY_REFUNDED(35, "notification.ownerOrderPartlyRefunded", NotificationCategory.MY_ORDERS, true, null),
    /**
     * An admin gave the player items to collect. Arguments: {@code amount}.
     */
    ITEMS_GRANTED(
            36,
            "notification.itemsGranted",
            NotificationCategory.MY_ORDERS,
            true,
            new Action("/order collect", "words.collect")),
    /**
     * An admin gave the player money. Arguments: {@code amount}.
     */
    MONEY_GRANTED(37, "notification.moneyGranted", NotificationCategory.PAYMENTS, false, null),
    /**
     * The player was paid for an order after tax and the company's treasury share. Arguments: {@code amount},
     * {@code order_name}, {@code gross}, {@code tax}, {@code share}.
     */
    PAYMENT_SHARED(40, "notification.orderPaymentShared", NotificationCategory.PAYMENTS, false, null),
    /**
     * A member changed the share of payouts that goes to the treasury. Arguments: {@code player_name} (member),
     * {@code percent}.
     */
    TREASURY_SHARE_CHANGED(41, "notification.treasuryShareChanged", NotificationCategory.COMPANY, false, null),
    /**
     * The treasury of the player's disbanded or deleted company was paid out to them. Arguments: {@code amount},
     * {@code company_name}.
     */
    TREASURY_DISBAND_PAYOUT(42, "notification.treasuryDisbandPayout", NotificationCategory.COMPANY, true, null),
    /**
     * A repeating order posted a copy. Arguments: {@code series_name}, {@code amount} (charged), {@code copy} (number
     * of the copy, with the count limit when there is one, such as {@code 3/5}), {@code order_id}.
     */
    SERIES_POSTED(
            43,
            "notification.seriesPosted",
            NotificationCategory.MY_ORDERS,
            false,
            new Action("/order info <order_id>", "words.info")),
    /**
     * A repeating order holds its next copy back because the player reached the order limit. Arguments:
     * {@code series_name}, {@code series_id}.
     */
    SERIES_WAITING_LIMIT(
            44,
            "notification.seriesWaitingLimit",
            NotificationCategory.MY_ORDERS,
            false,
            new Action("/order series info <series_id>", "words.info")),
    /**
     * A repeating order stopped; its info shows why. Arguments: {@code series_name}, {@code series_id}.
     */
    SERIES_STOPPED(
            45,
            "notification.seriesStopped",
            NotificationCategory.MY_ORDERS,
            true,
            new Action("/order series info <series_id>", "words.info")),
    /**
     * A repeating order posted its last copy. Arguments: {@code series_name}.
     */
    SERIES_FINISHED(46, "notification.seriesFinished", NotificationCategory.MY_ORDERS, false, null),
    /**
     * A new order matches saved alerts of the company. Arguments: {@code alert_name} (names of the matching alerts),
     * {@code order_name}, {@code order_id}, {@code contents}, {@code amount} (price), {@code net} (after the company's
     * tax).
     */
    ORDER_ALERT(
            47,
            "notification.orderAlert",
            NotificationCategory.ALERTS,
            false,
            new Action("/company order info <order_id>", "words.view"),
            new Action("/company order accept <order_id>", "words.accept")),
    /**
     * More orders matched an alert while it was rate limited. Arguments: {@code alert_name}, {@code count}.
     */
    ORDER_ALERT_DIGEST(
            48,
            "notification.orderAlertDigest",
            NotificationCategory.ALERTS,
            false,
            new Action("/company alerts show <alert_name>", "words.show")),
    /**
     * A player claimed the player's order to work on it alone. The worker is not named. Arguments:
     * {@code order_name}, {@code order_id}.
     */
    MY_ORDER_TAKEN(
            49,
            "notification.ownerOrderTaken",
            NotificationCategory.MY_ORDERS,
            false,
            new Action("/order info <order_id>", "words.info")),
    /**
     * An order the player worked on alone expired; the delivered items went back to the player. Arguments:
     * {@code order_name}.
     */
    SOLO_ORDER_EXPIRED(50, "notification.soloOrderExpired", NotificationCategory.ORDERS, true, null),
    /**
     * An admin took an order away from the player who worked on it alone, or deleted it; the delivered items went
     * back to the player. Arguments: {@code order_name}.
     */
    SOLO_ORDER_RELEASED_BY_ADMIN(51, "notification.soloOrderReleasedByAdmin", NotificationCategory.ORDERS, true, null),
    /**
     * A server event runs, told once to a player who joins while it runs. Arguments: {@code event_name},
     * {@code effect}, {@code ends}, {@code offers} (a sentence naming the event's offers, empty without).
     */
    SERVER_EVENT_RUNNING(52, "notification.serverEventRunning", NotificationCategory.ORDERS, false, null),
    /**
     * A company took the player's job. The company is not named. Arguments: {@code job_title}, {@code job_id},
     * {@code due}.
     */
    MY_JOB_TAKEN(
            53,
            "notification.myJobTaken",
            NotificationCategory.MY_ORDERS,
            false,
            new Action("/order job info <job_id>", "words.info")),
    /**
     * The company working on the player's job gave it back, or its time ran out; the job is open again. Arguments:
     * {@code job_title}, {@code job_id}.
     */
    MY_JOB_RELEASED(54, "notification.myJobReleased", NotificationCategory.MY_ORDERS, true, null),
    /**
     * The player's job checked out and the company was paid. Arguments: {@code job_title}, {@code job_id}.
     */
    MY_JOB_DONE(55, "notification.myJobDone", NotificationCategory.MY_ORDERS, true, null),
    /**
     * Nobody took the player's job in time; its price and fee went back. Arguments: {@code job_title},
     * {@code amount}.
     */
    MY_JOB_EXPIRED(56, "notification.myJobExpired", NotificationCategory.MY_ORDERS, true, null),
    /**
     * The time of a job the company worked on ran out; the job is open again and the access is gone. Arguments:
     * {@code job_title}, {@code job_id}.
     */
    JOB_OVERDUE(57, "notification.jobOverdue", NotificationCategory.ORDERS, true, null),
    /**
     * A job the company worked on checked out and its workers were paid. Arguments: {@code job_title},
     * {@code amount} (paid in all).
     */
    JOB_PAID(58, "notification.jobPaid", NotificationCategory.PAYMENTS, false, null),
    /**
     * The land of the player's job changed hands, such as a plot that was sold, so the job was cancelled; its price
     * went back. Arguments: {@code job_title}, {@code job_id}.
     */
    MY_JOB_LAND_LOST(59, "notification.myJobLandLost", NotificationCategory.MY_ORDERS, true, null),
    /**
     * The land of a job the company worked on changed hands, so the job was cancelled and the access is gone; nothing
     * is paid and no penalty applies. Arguments: {@code job_title}, {@code job_id}.
     */
    JOB_LAND_LOST(60, "notification.jobLandLost", NotificationCategory.ORDERS, true, null),
    /**
     * The company working on the player's contract says it is done; the player should approve or reject it.
     * Arguments: {@code job_title}, {@code job_id}, {@code time} (when it is approved without an answer).
     */
    MY_JOB_AWAITING(
            61,
            "notification.myJobAwaiting",
            NotificationCategory.MY_ORDERS,
            true,
            new Action("/order job approve <job_id>", "words.accept"),
            new Action("/order job info <job_id>", "words.info")),
    /**
     * The player did not answer in time, so their job was approved and the company paid. Arguments:
     * {@code job_title}, {@code job_id}.
     */
    MY_JOB_AUTO_APPROVED(62, "notification.myJobAutoApproved", NotificationCategory.MY_ORDERS, true, null),
    /**
     * A dispute was opened about the player's job; an admin settles it. Arguments: {@code job_title},
     * {@code job_id}, {@code reason}.
     */
    MY_JOB_DISPUTED(63, "notification.myJobDisputed", NotificationCategory.MY_ORDERS, true, null),
    /**
     * An admin settled the dispute about the player's job. Arguments: {@code job_title}, {@code job_id},
     * {@code amount} (what went back to the player).
     */
    MY_JOB_SETTLED(64, "notification.myJobSettled", NotificationCategory.MY_ORDERS, true, null),
    /**
     * The owner rejected the work on a job the company said is done; it is the company's again. Arguments:
     * {@code job_title}, {@code job_id}, {@code reason}.
     */
    JOB_REJECTED(
            65,
            "notification.jobRejected",
            NotificationCategory.ORDERS,
            true,
            new Action("/company job info <job_id>", "words.info")),
    /**
     * A dispute was opened about a job the company holds; access is frozen until an admin settles it. Arguments:
     * {@code job_title}, {@code job_id}, {@code reason}.
     */
    JOB_DISPUTED(66, "notification.jobDisputed", NotificationCategory.ORDERS, true, null),
    /**
     * An admin settled the dispute about a job the company held. Arguments: {@code job_title}, {@code job_id},
     * {@code amount} (paid to the workers in all).
     */
    JOB_SETTLED(67, "notification.jobSettled", NotificationCategory.ORDERS, true, null),
    /**
     * The company withdrew its dispute about the player's job; it works on it again. Arguments: {@code job_title},
     * {@code job_id}.
     */
    MY_JOB_WITHDRAWN(68, "notification.myJobWithdrawn", NotificationCategory.MY_ORDERS, false, null),
    /**
     * The owner withdrew their dispute about a job the company holds; the company works on it again and its time moved
     * on by the time the dispute lasted. Arguments: {@code job_title}, {@code job_id}.
     */
    JOB_WITHDRAWN(69, "notification.jobWithdrawn", NotificationCategory.ORDERS, true, null),
    /**
     * The player's harvest job ended: its time or budget ran out, or a side ended it. Arguments: {@code job_title},
     * {@code job_id}, {@code amount} (the rest of the budget given back), {@code items} (items waiting in the stock).
     */
    MY_JOB_HARVEST_ENDED(
            70,
            "notification.myJobHarvestEnded",
            NotificationCategory.MY_ORDERS,
            true,
            new Action("/order job collect <job_id>", "words.collect")),
    /**
     * The rent of an area of the company is due soon. Arguments: {@code area}, {@code amount}, {@code duration}.
     */
    AREA_RENT_DUE(71, "notification.areaRentDue", NotificationCategory.COMPANY, false, null),
    /**
     * The treasury paid the rent of an area for another period. Arguments: {@code area}, {@code amount},
     * {@code duration} (the time paid for).
     */
    AREA_RENT_RENEWED(72, "notification.areaRentRenewed", NotificationCategory.COMPANY, false, null),
    /**
     * The treasury could not pay the rent of an area, so the rent ended. Arguments: {@code area}, {@code amount}.
     */
    AREA_RENT_UNPAID(73, "notification.areaRentUnpaid", NotificationCategory.COMPANY, true, null),
    /**
     * The rent of an area ran out after it was cancelled. Arguments: {@code area}.
     */
    AREA_RENT_ENDED(74, "notification.areaRentEnded", NotificationCategory.COMPANY, false, null);

    private final int id;
    private final String key;
    private final NotificationCategory category;
    private final boolean important;

    private final List<Action> actions;

    NotificationType(int id, String key, NotificationCategory category, boolean important, @Nullable Action action) {
        this.id = id;
        this.key = key;
        this.category = category;
        this.important = important;
        this.actions = action == null ? List.of() : List.of(action);
    }

    NotificationType(
            int id, String key, NotificationCategory category, boolean important, Action first, Action second) {
        this.id = id;
        this.key = key;
        this.category = category;
        this.important = important;
        this.actions = List.of(first, second);
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
     * The buttons next to the message, in the order they are shown.
     *
     * @return the buttons, empty when the message has none
     */
    public List<Action> actions() {
        return actions;
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
