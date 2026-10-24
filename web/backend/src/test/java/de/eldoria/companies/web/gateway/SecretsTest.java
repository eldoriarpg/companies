/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway;

import de.eldoria.companies.web.gateway.session.Devices;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretsTest {
    @Test
    void shortIdsAndCodesAvoidLookAlikes() {
        for (String lookAlike : new String[] {"0", "1", "I", "L", "O"}) {
            assertFalse(Secrets.SHORT_ID_ALPHABET.contains(lookAlike), lookAlike);
            assertFalse(Secrets.CODE_ALPHABET.contains(lookAlike), lookAlike);
        }
        for (int i = 0; i < 200; i++) {
            assertTrue(Secrets.shortId().matches("[" + Secrets.SHORT_ID_ALPHABET + "]{6}"));
            assertTrue(Secrets.code().matches("[" + Secrets.CODE_ALPHABET + "]{6}"));
        }
    }

    @Test
    void readsWhatPlayersType() {
        assertEquals("HXRTWA", Secrets.normalise(" hxr-twa "));
        assertEquals("HXRTWA", Secrets.normalise("H X R T W A"));
    }

    @Test
    void comparesAgainstTheStoredHash() {
        String hash = Secrets.hash("HXRTWA");
        assertTrue(Secrets.matches("HXRTWA", hash));
        assertFalse(Secrets.matches("HXRTWB", hash));
    }

    @Test
    void describesBrowsers() {
        assertEquals("Firefox on Android", Devices.describe(FakeBrowser.FIREFOX_ON_ANDROID));
        assertEquals(
                "Safari on iPhone",
                Devices.describe("Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 "
                        + "(KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1"));
        assertEquals(
                "Chrome on Windows",
                Devices.describe("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                        + "Chrome/130.0.0.0 Safari/537.36"));
        assertNull(Devices.describe("curl/8.0"));
        assertNull(Devices.describe(null));
    }
}
