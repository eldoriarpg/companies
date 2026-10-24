/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway.node;

import de.eldoria.companies.web.api.link.ErrorCode;
import org.jspecify.annotations.Nullable;

/**
 * A request to a node failed: the node refused it with an error, went away, or did not answer in time.
 */
public final class NodeRequestException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final transient @Nullable ErrorCode code;

    /**
     * @param code the node's error code; none when the node went away or did not answer
     * @param message what happened
     */
    public NodeRequestException(@Nullable ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * @return the node's error code; none when the node went away or did not answer
     */
    public @Nullable ErrorCode code() {
        return code;
    }

    /**
     * Finds the request failure behind a failed future.
     *
     * @param error what the future failed with
     * @return the failure, a timeout for anything else
     */
    public static NodeRequestException of(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && !(cause instanceof NodeRequestException)) cause = cause.getCause();
        if (cause instanceof NodeRequestException failure) return failure;
        return new NodeRequestException(null, "The server did not answer in time");
    }
}
