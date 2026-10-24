/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway.browser;

import de.eldoria.companies.web.api.session.CloseReason;
import de.eldoria.companies.web.api.update.Patch;
import de.eldoria.companies.web.api.update.PatchOp;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * What the gateway pushes to a browser on {@code /api/v1/me/stream}.
 * <ul>
 *     <li>{@code patch}: a change of the snapshot with its new {@code revision}, see {@link Patch}. Revisions count
 *     up by one; a browser that sees a gap loads the snapshot again.</li>
 *     <li>{@code reload}: the snapshot was replaced; load it again.</li>
 *     <li>{@code server}: the server went {@code online} or offline.</li>
 *     <li>{@code closed}: the session ended for {@code reason}; the socket closes.</li>
 *     <li>{@code pong}: the answer to the browser's {@code {"type":"ping"}}.</li>
 * </ul>
 *
 * @param type the event
 * @param revision the snapshot's revision after a patch or reload
 * @param path the patch's path
 * @param op the patch's operation
 * @param key the patch's list key
 * @param value the patch's value
 * @param reason why the session ended
 * @param online whether the server is online
 */
public record StreamEvent(
        String type,
        @Nullable Long revision,
        @Nullable String path,
        @Nullable PatchOp op,
        @Nullable String key,
        @Nullable JsonNode value,
        @Nullable CloseReason reason,
        @Nullable Boolean online) {

    /**
     * @param revision the snapshot's revision after the patch
     * @param patch the patch
     * @return the event
     */
    public static StreamEvent patch(long revision, Patch patch) {
        return new StreamEvent("patch", revision, patch.path(), patch.op(), patch.key(), patch.value(), null, null);
    }

    /**
     * @param revision the replaced snapshot's revision
     * @return the event
     */
    public static StreamEvent reload(long revision) {
        return new StreamEvent("reload", revision, null, null, null, null, null, null);
    }

    /**
     * @param online whether the server is online
     * @return the event
     */
    public static StreamEvent server(boolean online) {
        return new StreamEvent("server", null, null, null, null, null, null, online);
    }

    /**
     * @param reason why the session ended
     * @return the event
     */
    public static StreamEvent closed(CloseReason reason) {
        return new StreamEvent("closed", null, null, null, null, null, reason, null);
    }

    /**
     * @return the answer to a ping
     */
    public static StreamEvent pong() {
        return new StreamEvent("pong", null, null, null, null, null, null, null);
    }
}
