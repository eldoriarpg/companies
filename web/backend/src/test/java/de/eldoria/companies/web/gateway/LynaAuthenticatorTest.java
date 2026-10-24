/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway;

import de.eldoria.companies.web.api.handshake.Credentials;
import de.eldoria.companies.web.api.handshake.KeyKind;
import de.eldoria.companies.web.gateway.node.Authentication;
import de.eldoria.companies.web.gateway.node.LynaAuthenticator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class LynaAuthenticatorTest {
    private static FakeLyna lyna;

    @BeforeAll
    static void start() {
        lyna = new FakeLyna();
    }

    @AfterAll
    static void stop() {
        lyna.close();
    }

    private static LynaAuthenticator authenticator(String secret) {
        return new LynaAuthenticator(
                new ServiceConfig.LynaConfig(URI.create(lyna.url()), secret, 2, "dashboard", Duration.ofMinutes(5)),
                HttpClient.newHttpClient(),
                Clock.systemUTC());
    }

    @Test
    void acceptsARotatedKeyInsideItsGraceAndCountsIt() {
        lyna.rotated("lyd_old", "key-rotating");
        LynaAuthenticator authenticator = authenticator(FakeLyna.SECRET);
        Authentication result = authenticator.authenticate(new Credentials(KeyKind.DASHBOARD_KEY, "lyd_old"));
        assertEquals(
                "lyna:key-rotating",
                assertInstanceOf(Authentication.Accepted.class, result).serverId());
        assertEquals(1, authenticator.rotatedConnections());
    }

    @Test
    void refusesEverythingWithAWrongGatewaySecret() {
        lyna.valid("lyd_fine", "key-fine", 2, "TRIAL");
        Authentication result =
                authenticator("not-the-secret").authenticate(new Credentials(KeyKind.DASHBOARD_KEY, "lyd_fine"));
        assertInstanceOf(Authentication.Refused.class, result);
        Authentication.Accepted trial = assertInstanceOf(
                Authentication.Accepted.class,
                authenticator(FakeLyna.SECRET).authenticate(new Credentials(KeyKind.DASHBOARD_KEY, "lyd_fine")));
        assertEquals("TRIAL", trial.keyKind());
    }

    @Test
    void refusesAKeyOfAnotherKeyType() {
        lyna.ofType("lyd_other", "key-other-type", "updates");
        Authentication result =
                authenticator(FakeLyna.SECRET).authenticate(new Credentials(KeyKind.DASHBOARD_KEY, "lyd_other"));
        assertEquals(
                "The key is not a Companies dashboard key",
                assertInstanceOf(Authentication.Refused.class, result).reason());
    }
}
