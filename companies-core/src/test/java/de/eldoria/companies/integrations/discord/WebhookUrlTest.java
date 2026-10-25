/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.integrations.discord;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookUrlTest {
    private static final String URL = "https://discord.com/api/webhooks/1/secretToken";
    private final List<String> notices = new ArrayList<>();

    private Optional<URI> resolve(String configured) {
        return WebhookUrl.resolve(configured, notices::add);
    }

    @Test
    void plainUrlIsUsed() {
        assertEquals(Optional.of(URI.create(URL)), resolve(" " + URL + " "));
        assertEquals(List.of(), notices);
    }

    @Test
    void emptyOrInsecureUrlIsRefusedWithoutShowingIt() {
        assertEquals(Optional.empty(), resolve(""));
        assertEquals(Optional.empty(), resolve("http://discord.com/api/webhooks/1/secretToken"));
        assertEquals(Optional.empty(), resolve("not a url"));
        assertEquals(3, notices.size());
        assertTrue(notices.stream().noneMatch(notice -> notice.contains("secretToken")));
    }

    @Test
    void relayHostIsAllowedWithNotice() {
        var relay = "https://relay.example.org/hook/secretToken";

        assertEquals(Optional.of(URI.create(relay)), resolve(relay));
        assertEquals(1, notices.size());
        assertFalse(notices.getFirst().contains("secretToken"));
    }
}
