/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientAddressTest {
    private static final ClientAddress NONE = ClientAddress.trusting(List.of(), false);
    private static final ClientAddress PRIVATE = ClientAddress.trusting(List.of("172.16.0.0/12", "::1"), false);
    private static final ClientAddress CLOUDFLARE = ClientAddress.trusting(List.of("172.16.0.0/12"), true);

    @Test
    void trustsNothingUnlessConfigured() {
        assertEquals("172.18.0.5", NONE.of("172.18.0.5", "203.0.113.7", "203.0.113.8"));
    }

    @Test
    void readsTheChainFromTheRight() {
        assertEquals("203.0.113.7", PRIVATE.of("172.18.0.5", "203.0.113.7", null));
        assertEquals("203.0.113.7", PRIVATE.of("172.18.0.5", "1.2.3.4, 203.0.113.7, 172.18.0.9", null));
        assertEquals("2001:db8::1", PRIVATE.of("::1", "2001:db8::1", null));
    }

    @Test
    void believesCloudflareOnlyFromTrustedProxies() {
        assertEquals("203.0.113.8", CLOUDFLARE.of("172.18.0.5", "198.51.100.1", "203.0.113.8"));
        assertEquals("198.51.100.2", CLOUDFLARE.of("198.51.100.2", null, "203.0.113.8"));
        assertEquals("198.51.100.1", CLOUDFLARE.of("172.18.0.5", "198.51.100.1", "not an address"));
        assertEquals("198.51.100.1", PRIVATE.of("172.18.0.5", "198.51.100.1", "203.0.113.8"));
    }

    @Test
    void refusesBadRanges() {
        assertThrows(IllegalArgumentException.class, () -> ClientAddress.trusting(List.of("example.com"), false));
        assertThrows(IllegalArgumentException.class, () -> ClientAddress.trusting(List.of("10.0.0.0/33"), false));
    }
}
