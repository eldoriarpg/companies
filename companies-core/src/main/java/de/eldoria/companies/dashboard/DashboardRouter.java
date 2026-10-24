/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard;

import de.eldoria.companies.components.company.ICompanyProfile;
import de.eldoria.companies.dashboard.actions.DashboardWrites;
import de.eldoria.companies.dashboard.feed.PatchFeed;
import de.eldoria.companies.dashboard.gateway.GatewayLink;
import de.eldoria.companies.dashboard.gateway.GatewayStatus;
import de.eldoria.companies.dashboard.gateway.Handshake;
import de.eldoria.companies.dashboard.sessions.DashboardSessions;
import de.eldoria.companies.dashboard.sessions.SessionPermissions;
import de.eldoria.companies.dashboard.views.DashboardReads;
import de.eldoria.companies.dashboard.views.Views;
import de.eldoria.companies.data.wrapper.dashboard.StoredSession;
import de.eldoria.companies.web.api.Actions;
import de.eldoria.companies.web.api.Message;
import de.eldoria.companies.web.api.Protocol;
import de.eldoria.companies.web.api.action.ActionRequest;
import de.eldoria.companies.web.api.action.ActionResult;
import de.eldoria.companies.web.api.handshake.GatewayKind;
import de.eldoria.companies.web.api.handshake.KeyKind;
import de.eldoria.companies.web.api.handshake.Welcome;
import de.eldoria.companies.web.api.link.ErrorCode;
import de.eldoria.companies.web.api.link.ErrorMessage;
import de.eldoria.companies.web.api.link.Ping;
import de.eldoria.companies.web.api.link.Pong;
import de.eldoria.companies.web.api.link.UnknownMessage;
import de.eldoria.companies.web.api.session.SessionClosed;
import de.eldoria.companies.web.api.session.SessionConfirmed;
import de.eldoria.companies.web.api.session.SessionOpened;
import de.eldoria.companies.web.api.snapshot.SnapshotRequest;
import de.eldoria.companies.web.api.update.Subscribe;
import de.eldoria.companies.web.api.update.Unsubscribe;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Answers the gateway's messages.
 * <p>
 * Every request about a session is checked against the plugin's own sessions first: a session the plugin did not
 * create, that ended or that belongs to another player is answered with {@link ErrorCode#UNKNOWN_SESSION}, so a
 * compromised gateway cannot invent a player. A snapshot request subscribes the session to patches. While the plugin
 * version is discontinued only pings, the welcome and unsubscribes are handled, and a discontinued welcome drops every
 * subscription; everything else is refused with {@link ErrorCode#DISCONTINUED}. Actions that change something go to
 * {@link DashboardWrites}, reading ones to {@link DashboardReads}; both are checked against the switched on features and
 * the per-player action limit first. Snapshots and actions are built on the work executor, everything else in order of
 * arrival.
 */
public final class DashboardRouter {
    private final Parts parts;

    /**
     * @param parts what the router answers with
     */
    public DashboardRouter(Parts parts) {
        this.parts = parts;
    }

    /**
     * Handles one message of the gateway.
     *
     * @param message the message
     */
    public void accept(Message message) {
        switch (message) {
            case Welcome welcome -> welcome(welcome);
            case Ping ping -> parts.link().send(new Pong(ping.id(), Protocol.VERSION, ping.time()));
            case Pong ignored -> {}
            case ErrorMessage error -> parts.status().error(error);
            case Unsubscribe unsubscribe -> parts.feed().unsubscribe(unsubscribe.session());
            default -> {
                if (parts.status().discontinued()) {
                    refuse(message, ErrorCode.DISCONTINUED, "This plugin version is discontinued.");
                    return;
                }
                request(message);
            }
        }
    }

    private void request(Message message) {
        switch (message) {
            case SessionOpened opened -> opened(opened);
            case SessionClosed closed -> closed(closed);
            case SnapshotRequest request -> snapshot(request);
            case Subscribe subscribe -> subscribe(subscribe);
            case ActionRequest action -> action(action);
            case UnknownMessage unknown -> refuse(unknown, ErrorCode.UNKNOWN_TYPE, "Unknown message type.");
            default -> refuse(message, ErrorCode.INVALID, "Not expected from the gateway.");
        }
    }

    private void welcome(Welcome welcome) {
        if (welcome.gateway() == GatewayKind.SELF_HOSTED
                && parts.handshake()
                        .sentKind()
                        .filter(KeyKind.DASHBOARD_KEY::equals)
                        .isPresent()) {
            parts.status()
                    .withholdLynaKey(
                            "the gateway calls itself self-hosted and must not receive a Lyna dashboard key; enter "
                                    + "the gateway's server token or point dashboard.gateway at the hosted gateway",
                            true);
            parts.disconnect().run();
            return;
        }
        parts.status().welcome(welcome);
        if (parts.status().discontinued()) {
            parts.feed().clear();
            return;
        }
        parts.handshake().iconFor(welcome).ifPresent(parts.link()::send);
        parts.reportPresence().run();
    }

    private void opened(SessionOpened opened) {
        var session = parts.sessions().open(opened.session(), opened.player(), opened.device());
        if (session.isEmpty()) {
            unknownSession(opened);
            return;
        }
        parts.link()
                .send(new SessionConfirmed(
                        opened.id(),
                        Protocol.VERSION,
                        opened.session(),
                        SessionPermissions.admin(session.get().permissions())));
        parts.opened().accept(session.get().player(), session.get().deviceName());
    }

    private void closed(SessionClosed closed) {
        parts.feed().unsubscribe(closed.session());
        parts.sessions().forget(closed.session());
    }

    private void snapshot(SnapshotRequest request) {
        var session = active(request, request.session(), request.player());
        if (session.isEmpty()) return;
        work(request, () -> {
            var company = parts.views().companyOf(request.player()).map(ICompanyProfile::id);
            parts.feed().subscribe(request.session(), request.player(), company);
            var seq = parts.feed().sequence();
            parts.link()
                    .send(parts.views().snapshot(request.id(), request.session(), request.player(), parts.node(), seq));
        });
    }

    private void subscribe(Subscribe subscribe) {
        var session = active(subscribe, subscribe.session(), subscribe.player());
        if (session.isEmpty()) return;
        work(subscribe, () -> {
            var company = parts.views().companyOf(subscribe.player()).map(ICompanyProfile::id);
            parts.feed().subscribe(subscribe.session(), subscribe.player(), company);
        });
    }

    private void action(ActionRequest action) {
        var session = active(action, action.session(), action.player());
        if (session.isEmpty()) return;
        if (!parts.limit().take(action.player())) {
            parts.link()
                    .send(new ActionResult(
                            action.id(),
                            Protocol.VERSION,
                            false,
                            "dashboard.rateLimited",
                            parts.refusal().apply("dashboard.rateLimited"),
                            null));
            return;
        }
        if (DashboardWrites.performs(action.name())) {
            var feature = Actions.feature(action.name());
            if (feature == null || !parts.features().test(feature)) {
                parts.link()
                        .send(new ActionResult(
                                action.id(),
                                Protocol.VERSION,
                                false,
                                "dashboard.featureDisabled",
                                parts.refusal().apply("dashboard.featureDisabled"),
                                null));
                return;
            }
            work(action, () -> parts.link().send(parts.writes().perform(action, session.get())));
            return;
        }
        work(action, () -> parts.link().send(parts.reads().answer(action, session.get(), parts.features())));
    }

    private Optional<StoredSession> active(Message message, String session, UUID player) {
        var found = parts.sessions().active(session, player);
        if (found.isEmpty()) unknownSession(message);
        return found;
    }

    private void unknownSession(Message message) {
        refuse(message, ErrorCode.UNKNOWN_SESSION, "The plugin knows no open session with this id.");
    }

    private void refuse(Message message, ErrorCode code, String reason) {
        parts.link().send(new ErrorMessage(message.id(), Protocol.VERSION, code, reason));
    }

    private void work(Message message, Runnable task) {
        parts.work().execute(() -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                parts.logger().log(Level.WARNING, "Could not answer a dashboard request.", e);
                refuse(message, ErrorCode.INTERNAL, "The plugin failed to answer.");
            }
        });
    }

    /**
     * What the router answers with.
     *
     * @param link      sends to the gateway
     * @param status    keeps what the gateway said
     * @param handshake sends the icon the gateway asks for
     * @param sessions  the plugin's sessions
     * @param feed      the subscriptions and patches
     * @param views     builds snapshots
     * @param reads     answers read actions
     * @param writes    performs the actions that change something
     * @param limit     limits the actions per player
     * @param features  whether a feature is switched on
     * @param refusal   renders a refusal key as plain text
     * @param opened    tells a player that a browser logged in, with the browser's name
     * @param disconnect  closes the connection without sending the key again
     * @param node      this node's uid
     * @param reportPresence tells the gateway which players with a session are online here
     * @param work      builds snapshots and answers actions off the inbound thread
     * @param logger    logger of the plugin
     */
    public record Parts(
            GatewayLink link,
            GatewayStatus status,
            Handshake handshake,
            DashboardSessions sessions,
            PatchFeed feed,
            Views views,
            DashboardReads reads,
            DashboardWrites writes,
            ActionLimit limit,
            Predicate<String> features,
            Function<String, String> refusal,
            BiConsumer<UUID, Optional<String>> opened,
            Runnable disconnect,
            UUID node,
            Runnable reportPresence,
            Executor work,
            Logger logger) {}
}
