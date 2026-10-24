/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.gateway;

import de.eldoria.companies.web.api.handshake.DeprecatedPart;
import de.eldoria.companies.web.api.handshake.GatewayKind;
import de.eldoria.companies.web.api.handshake.VersionState;
import de.eldoria.companies.web.api.handshake.VersionStatus;
import de.eldoria.companies.web.api.handshake.Welcome;
import de.eldoria.companies.web.api.link.ErrorCode;
import de.eldoria.companies.web.api.link.ErrorMessage;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * What the gateway told this node: the welcome of the current connection and the last refusal.
 * <p>
 * A deprecated plugin version and deprecated protocol parts are logged once per start, a discontinued version once as
 * well. While the version is discontinued the node accepts nothing but status messages. The welcome stays known after
 * the connection closed, so a discontinued version keeps waiting six hours between attempts.
 */
public final class GatewayStatus {
    private final Logger logger;
    private final Set<String> logged = ConcurrentHashMap.newKeySet();
    private volatile @Nullable Welcome welcome;
    private volatile boolean connected;
    private volatile @Nullable String refusal;

    /**
     * @param logger logger of the plugin
     */
    public GatewayStatus(Logger logger) {
        this.logger = logger;
    }

    /**
     * Takes the gateway's answer to the hello.
     *
     * @param answer the welcome
     */
    public void welcome(Welcome answer) {
        welcome = answer;
        connected = true;
        refusal = null;
        var version = answer.version();
        if (version.status() == VersionState.DISCONTINUED) {
            once("discontinued:" + version.message(), () -> logger.severe(notice(version)));
        } else if (version.status() == VersionState.DEPRECATED) {
            once("deprecated:" + version.message(), () -> logger.warning(notice(version)));
        }
        for (var part : answer.deprecated()) {
            once("part:" + part.kind() + ":" + part.name(), () -> logger.warning(notice(part)));
        }
        if (answer.gateway() == GatewayKind.UNKNOWN) {
            once("gateway", () -> logger.info("The dashboard gateway is of a kind this version does not know."));
        }
        logger.info("Connected to the dashboard gateway, protocol " + answer.protocol() + ".");
    }

    /**
     * Takes an error the gateway sent outside of a request.
     *
     * @param error the error
     */
    public void error(ErrorMessage error) {
        if (error.code() == ErrorCode.UNAUTHORIZED) {
            var message = error.message() == null ? "no reason given" : error.message();
            refusal = message;
            once(
                    "unauthorized:" + message,
                    () -> logger.warning("The dashboard gateway refused the key: " + message
                            + ". Enter a new key with /companyadmin dashboard key."));
            return;
        }
        logger.fine("The dashboard gateway reported " + error.code() + ": " + error.message());
    }

    /**
     * Notes that the connection closed and reports why when the admin has to act: a refused key, another server with
     * the same node uid, or a message the gateway called invalid. Each reason is reported once.
     *
     * @param closed how the connection ended
     */
    public void disconnected(GatewayConnection.Closed closed) {
        connected = false;
        var reason = closed.reason().isBlank() ? "no reason given" : closed.reason();
        switch (closed.code()) {
            case GatewayConnection.CLOSE_UNAUTHORIZED -> {
                refusal = reason;
                once(
                        "unauthorized:" + reason,
                        () -> logger.warning(
                                "The dashboard gateway refused the key: " + reason
                                        + ". Enter a new key with /companyadmin dashboard key. The server tries again in an hour."));
            }
            case GatewayConnection.CLOSE_REPLACED ->
                once(
                        "replaced",
                        () -> logger.warning("Another connection with the nodeUid of this server replaced "
                                + "its dashboard connection. Give every server its own nodeUid in node.yml."));
            case GatewayConnection.CLOSE_INVALID ->
                once(
                        "invalid:" + reason,
                        () -> logger.warning(
                                "The dashboard gateway closed the connection because of an invalid message: "
                                        + reason));
            case GatewayConnection.CLOSE_RATE_LIMITED ->
                logger.fine("The dashboard gateway limited the rate of this server.");
            default -> {}
        }
    }

    /**
     * @return true while connected and welcomed
     */
    public boolean connected() {
        return connected && welcome != null;
    }

    /**
     * @return true when the gateway called the plugin version discontinued on the last connection
     */
    public boolean discontinued() {
        var current = welcome;
        return current != null && current.version().status() == VersionState.DISCONTINUED;
    }

    /**
     * @return the last welcome, empty before the first connection
     */
    public Optional<Welcome> lastWelcome() {
        return Optional.ofNullable(welcome);
    }

    /**
     * @return why the gateway refused the key the last time, empty while it was accepted
     */
    public Optional<String> refusal() {
        return Optional.ofNullable(refusal);
    }

    /**
     * The notices for admins: a deprecated or discontinued version and deprecated parts still in use.
     *
     * @return the notices, empty when there are none
     */
    public List<String> notices() {
        var current = welcome;
        List<String> notices = new ArrayList<>();
        if (current == null) return notices;
        var version = current.version();
        if (version.status() == VersionState.DEPRECATED || version.status() == VersionState.DISCONTINUED) {
            notices.add(notice(version));
        }
        for (var part : current.deprecated()) notices.add(notice(part));
        return notices;
    }

    /**
     * The sentence shown for a version status.
     *
     * @param version the status
     * @return the notice
     */
    public static String notice(VersionStatus version) {
        var text = new StringBuilder();
        if (version.status() == VersionState.DISCONTINUED) {
            text.append("This Companies version is discontinued on the dashboard");
            if (version.since() != null) text.append(" since ").append(version.since());
        } else {
            text.append("This Companies version is deprecated on the dashboard");
            if (version.discontinuedAt() != null) {
                text.append(" and stops working on ").append(version.discontinuedAt());
            }
        }
        text.append('.');
        if (version.updateTo() != null)
            text.append(" Update to ").append(version.updateTo()).append('.');
        if (version.message() != null) text.append(' ').append(version.message());
        return text.toString();
    }

    private static String notice(DeprecatedPart part) {
        var text = new StringBuilder("Update Companies before ")
                .append(part.removedAt())
                .append(", or ")
                .append(part.name())
                .append(" stops working on the dashboard.");
        if (part.message() != null) text.append(' ').append(part.message());
        return text.toString();
    }

    private void once(String key, Runnable log) {
        if (logged.add(key)) log.run();
    }
}
