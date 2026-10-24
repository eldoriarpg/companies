/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api.handshake;

/**
 * What a server authenticates with. The key is never logged by either side.
 *
 * @param kind whether it is a Lyna dashboard key or a self-hosted gateway's server token
 * @param key the key or token itself
 */
public record Credentials(KeyKind kind, String key) {
    /**
     * Names the kind only, so the key never reaches a log line through this record.
     *
     * @return the kind and a masked key
     */
    @Override
    public String toString() {
        return "Credentials[kind=" + kind + ", key=*****]";
    }
}
