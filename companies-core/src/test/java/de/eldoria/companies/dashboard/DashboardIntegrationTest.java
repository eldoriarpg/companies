/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.configuration.elements.DashboardSettings;
import de.eldoria.companies.dashboard.feed.PatchFeed;
import de.eldoria.companies.dashboard.gateway.FakeGateway;
import de.eldoria.companies.dashboard.gateway.GatewayConnection;
import de.eldoria.companies.dashboard.gateway.GatewayStatus;
import de.eldoria.companies.dashboard.gateway.Handshake;
import de.eldoria.companies.dashboard.keys.DashboardKeys;
import de.eldoria.companies.dashboard.sessions.DashboardSessions;
import de.eldoria.companies.web.api.Actions;
import de.eldoria.companies.web.api.Protocol;
import de.eldoria.companies.web.api.action.ActionRequest;
import de.eldoria.companies.web.api.action.ActionResult;
import de.eldoria.companies.web.api.handshake.Branding;
import de.eldoria.companies.web.api.handshake.BrandingUpdate;
import de.eldoria.companies.web.api.handshake.GatewayKind;
import de.eldoria.companies.web.api.handshake.Hello;
import de.eldoria.companies.web.api.handshake.NodeInfo;
import de.eldoria.companies.web.api.handshake.NodeRole;
import de.eldoria.companies.web.api.handshake.VersionState;
import de.eldoria.companies.web.api.handshake.VersionStatus;
import de.eldoria.companies.web.api.handshake.Welcome;
import de.eldoria.companies.web.api.link.ErrorCode;
import de.eldoria.companies.web.api.link.ErrorMessage;
import de.eldoria.companies.web.api.session.CloseReason;
import de.eldoria.companies.web.api.session.CreateSession;
import de.eldoria.companies.web.api.session.PlayerRef;
import de.eldoria.companies.web.api.session.SessionConfirmed;
import de.eldoria.companies.web.api.session.SessionCreated;
import de.eldoria.companies.web.api.session.SessionOpened;
import de.eldoria.companies.web.api.session.SessionsRevoked;
import de.eldoria.companies.web.api.snapshot.Snapshot;
import de.eldoria.companies.web.api.snapshot.SnapshotRequest;
import de.eldoria.companies.web.api.update.Unsubscribe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A node with its dashboard against a gateway on a local port: handshake, login, snapshot, actions and logout.
 */
class DashboardIntegrationTest {
    private static final Duration WAIT = Duration.ofSeconds(5);
    private static final UUID NODE = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final String TOKEN = "cst_test-token";
    private static final Instant EXPIRES = Instant.now().plus(Duration.ofMinutes(5));

    @TempDir
    Path directory;

    private final List<String> opened = new CopyOnWriteArrayList<>();
    private final Set<String> disabled = ConcurrentHashMap.newKeySet();
    private final AtomicInteger disconnects = new AtomicInteger();
    private DashboardWorld world;
    private FakeGateway gateway;
    private Dashboard dashboard;
    private ScheduledExecutorService timer;
    private UUID wren;
    private Handshake handshake;
    private DashboardKeys keys;
    private GatewayStatus status;
    private int miners;

    @BeforeEach
    void setUp() throws Exception {
        world = DashboardWorld.create(directory);
        gateway = FakeGateway.start();
        timer = Executors.newSingleThreadScheduledExecutor();
        wren = world.player("Wren");
        miners = world.company("Miners", Map.of(wren, List.of(CompanyPermission.OWNER)));

        var logger = Logger.getAnonymousLogger();
        var clock = Clock.systemUTC();
        var settings = new DashboardSettings(true, gateway.uri().toString());
        keys = new DashboardKeys(
                world.dashboard,
                key -> {
                    throw new IOException("Lyna is not asked in this test");
                },
                clock,
                logger,
                true);
        var sessions = new DashboardSessions(world.dashboard, () -> settings, NODE, clock);
        status = new GatewayStatus(logger);
        handshake = new Handshake(
                new Handshake.Identity(
                        "2.1.0-test", "1.11", new NodeInfo(NODE, NodeRole.SECONDARY), "Companies/2.1.0-test", 50),
                keys::current,
                () -> List.of("orders.browse"),
                () -> Optional.of(new Branding("Test Server", "aWNvbg==", "abc123")));
        var link = new LinkProxy();
        var feed = new PatchFeed(world.views, link, NODE, Runnable::run, logger);
        var router = new DashboardRouter(new DashboardRouter.Parts(
                link,
                status,
                handshake,
                sessions,
                feed,
                world.views,
                world.reads,
                world.writes,
                new ActionLimit(20, Duration.ofSeconds(10), clock),
                feature -> !disabled.contains(feature),
                key -> "text of " + key,
                (player, device) -> opened.add(player + " " + device.orElse("?")),
                () -> disconnects.incrementAndGet(),
                NODE,
                world.executor,
                logger));
        dashboard = new Dashboard(new Dashboard.Parts(
                () -> settings,
                () -> TOKEN,
                keys,
                sessions,
                status,
                handshake,
                feed,
                router::accept,
                () -> {},
                NODE,
                true,
                "Companies/2.1.0-test",
                world.executor,
                clock,
                logger));
        link.use(dashboard);
        dashboard.start(timer);
    }

    @AfterEach
    void tearDown() throws Exception {
        dashboard.stop();
        timer.shutdownNow();
        gateway.close();
        world.close();
    }

    private Welcome welcome(VersionStatus version, boolean iconCached) {
        return new Welcome(
                null,
                Protocol.VERSION,
                Protocol.VERSION,
                GatewayKind.HOSTED,
                List.of(),
                version,
                List.of(),
                iconCached);
    }

    private void connect(VersionStatus version) throws Exception {
        gateway.nextConnection(WAIT);
        var hello = gateway.next(Hello.class, WAIT);
        assertEquals(TOKEN, hello.credentials().key());
        gateway.send(welcome(version, false));
        var end = System.nanoTime() + WAIT.toNanos();
        while (dashboard.status().get().welcome().isEmpty() && System.nanoTime() < end) {
            TimeUnit.MILLISECONDS.sleep(20);
        }
    }

    private static VersionStatus supported() {
        return new VersionStatus(VersionState.SUPPORTED, null, null, null, null);
    }

    private String login() throws Exception {
        var opening = dashboard.open(new PlayerRef(wren, "Wren"), Set.of("companies.dashboard.use"));
        var create = gateway.next(CreateSession.class, WAIT);
        assertEquals(wren, create.player().uuid());
        assertEquals(NODE, create.node());
        gateway.send(new SessionCreated(
                create.id(),
                Protocol.VERSION,
                "session-1",
                "AB12CD",
                "KPMWQR",
                "https://dash.example/AB12CD",
                EXPIRES));
        var created = assertInstanceOf(Dashboard.Opening.Created.class, opening.get(5, TimeUnit.SECONDS));
        assertEquals("KPMWQR", created.session().code());
        return created.session().session();
    }

    @Test
    void theIconFollowsWhenTheGatewayDoesNotHaveIt() throws Exception {
        connect(supported());

        var update = gateway.next(BrandingUpdate.class, WAIT);

        assertEquals("aWNvbg==", update.branding().icon());
        assertEquals("abc123", update.branding().iconHash());
    }

    @Test
    void aPlayerLogsInWithTheCodeAndGetsASnapshot() throws Exception {
        connect(supported());
        var session = login();
        assertEquals(1, world.dashboard.sessionsOf(wren).size());

        gateway.send(new SnapshotRequest("early", Protocol.VERSION, session, wren));
        assertEquals(
                ErrorCode.UNKNOWN_SESSION,
                gateway.next(ErrorMessage.class, WAIT).code());

        gateway.send(new SessionOpened("open-1", Protocol.VERSION, session, UUID.randomUUID(), "Firefox on Android"));
        assertEquals(
                ErrorCode.UNKNOWN_SESSION,
                gateway.next(ErrorMessage.class, WAIT).code());

        gateway.send(new SessionOpened("open-2", Protocol.VERSION, session, wren, "Firefox on Android"));
        var confirmed = gateway.next(SessionConfirmed.class, WAIT);
        assertEquals("open-2", confirmed.id());
        assertEquals(session, confirmed.session());
        assertEquals(Boolean.FALSE, confirmed.admin());
        assertEquals(List.of(wren + " Firefox on Android"), opened);

        gateway.send(new SnapshotRequest("snap-1", Protocol.VERSION, session, wren));
        var snapshot = gateway.next(Snapshot.class, WAIT);
        assertEquals("snap-1", snapshot.id());
        assertEquals(session, snapshot.session());
        assertEquals(NODE, snapshot.node());
        assertNotNull(snapshot.company());
        assertEquals(miners, snapshot.company().id());
        assertEquals("Wren", snapshot.player().name());
    }

    @Test
    void actionsAreAnsweredForKnownSessionsOnly() throws Exception {
        connect(supported());
        var session = login();
        gateway.send(new SessionOpened("open", Protocol.VERSION, session, wren, null));
        gateway.next(SessionConfirmed.class, WAIT);

        gateway.send(new ActionRequest(
                "act-1",
                Protocol.VERSION,
                session,
                wren,
                Actions.INBOX_PAGE,
                Protocol.mapper().readTree("{}")));
        var result = gateway.next(ActionResult.class, WAIT);
        assertEquals("act-1", result.id());
        assertTrue(result.ok());

        gateway.send(new ActionRequest(
                "act-2",
                Protocol.VERSION,
                "invented",
                wren,
                Actions.INBOX_PAGE,
                Protocol.mapper().readTree("{}")));
        var refused = gateway.next(ErrorMessage.class, WAIT);
        assertEquals("act-2", refused.id());
        assertEquals(ErrorCode.UNKNOWN_SESSION, refused.code());
    }

    @Test
    void writesAreRefusedForSwitchedOffFeaturesAndBeyondTheLimit() throws Exception {
        connect(supported());
        var session = login();
        gateway.send(new SessionOpened("open", Protocol.VERSION, session, wren, null));
        gateway.next(SessionConfirmed.class, WAIT);

        disabled.add("inbox");
        gateway.send(new ActionRequest(
                "off",
                Protocol.VERSION,
                session,
                wren,
                Actions.INBOX_READ_ALL,
                Protocol.mapper().readTree("{}")));
        var off = gateway.next(ActionResult.class, WAIT);
        assertFalse(off.ok());
        assertEquals("dashboard.featureDisabled", off.reason());

        disabled.clear();
        ActionResult last = null;
        for (int i = 0; i < 20; i++) {
            gateway.send(new ActionRequest(
                    "w" + i,
                    Protocol.VERSION,
                    session,
                    wren,
                    Actions.INBOX_READ_ALL,
                    Protocol.mapper().readTree("{}")));
            last = gateway.next(ActionResult.class, WAIT);
        }
        assertNotNull(last);
        gateway.send(new ActionRequest(
                "over",
                Protocol.VERSION,
                session,
                wren,
                Actions.INBOX_READ_ALL,
                Protocol.mapper().readTree("{}")));
        var over = gateway.next(ActionResult.class, WAIT);
        assertFalse(over.ok());
        assertEquals("dashboard.rateLimited", over.reason());
    }

    @Test
    void logoutEndsEverySessionAndTellsTheGateway() throws Exception {
        connect(supported());
        var session = login();
        gateway.send(new SessionOpened("open", Protocol.VERSION, session, wren, null));
        gateway.next(SessionConfirmed.class, WAIT);

        assertEquals(1, dashboard.endSessions(wren, CloseReason.LOGOUT).get(5, TimeUnit.SECONDS));

        var revoked = gateway.next(SessionsRevoked.class, WAIT);
        assertEquals(wren, revoked.player());
        assertEquals(CloseReason.LOGOUT, revoked.reason());
        assertTrue(world.dashboard.sessionsOf(wren).isEmpty());
        gateway.send(new SnapshotRequest("late", Protocol.VERSION, session, wren));
        assertEquals(
                ErrorCode.UNKNOWN_SESSION,
                gateway.next(ErrorMessage.class, WAIT).code());
    }

    @Test
    void aLynaKeyIsNeverSentToAGatewayOtherThanTheHostedOne() throws Exception {
        connect(supported());

        dashboard.enterKey("lyd_test-key").get(5, TimeUnit.SECONDS);
        TimeUnit.SECONDS.sleep(2);

        assertEquals(1, gateway.connections().size());
        assertTrue(dashboard.status().get().problem().orElseThrow().contains("Lyna dashboard key"));
        assertTrue(Dashboard.hosted(URI.create("wss://companies.eldoria.de/api/v1/servers/connect")));
        assertFalse(Dashboard.hosted(URI.create("ws://companies.eldoria.de/api/v1/servers/connect")));
        assertFalse(Dashboard.hosted(URI.create("wss://dashboard.example.org/api/v1/servers/connect")));
    }

    @Test
    void aSelfHostedGatewayThatGotALynaKeyIsLeft() throws Exception {
        connect(supported());
        keys.enter("lyd_test-key", Optional.empty());
        handshake.hello();

        gateway.send(new Welcome(
                null,
                Protocol.VERSION,
                Protocol.VERSION,
                GatewayKind.SELF_HOSTED,
                List.of(),
                supported(),
                List.of(),
                true));

        var end = System.nanoTime() + WAIT.toNanos();
        while (disconnects.get() == 0 && System.nanoTime() < end) TimeUnit.MILLISECONDS.sleep(20);
        assertEquals(1, disconnects.get());
        assertTrue(status.lynaKeyWithheld());
        assertTrue(status.refusal().orElseThrow().contains("self-hosted"));
    }

    @Test
    void aDiscontinuedWelcomeOnAnOpenConnectionStopsThePatches() throws Exception {
        connect(supported());
        var session = login();
        gateway.send(new SessionOpened("open", Protocol.VERSION, session, wren, null));
        gateway.next(SessionConfirmed.class, WAIT);
        gateway.send(new SnapshotRequest("snap", Protocol.VERSION, session, wren));
        gateway.next(Snapshot.class, WAIT);
        assertEquals(1, dashboard.status().get().subscriptions());

        gateway.send(welcome(
                new VersionStatus(VersionState.DISCONTINUED, LocalDate.of(2026, 10, 1), null, "2.4.0", null), true));

        var end = System.nanoTime() + WAIT.toNanos();
        while (dashboard.status().get().subscriptions() > 0 && System.nanoTime() < end) {
            TimeUnit.MILLISECONDS.sleep(20);
        }
        assertEquals(0, dashboard.status().get().subscriptions());

        gateway.send(new Unsubscribe("unsub", Protocol.VERSION, session));
        gateway.send(new SnapshotRequest("late", Protocol.VERSION, session, wren));
        var refused = gateway.next(ErrorMessage.class, WAIT);
        assertEquals("late", refused.id());
        assertEquals(ErrorCode.DISCONTINUED, refused.code());
    }

    @Test
    void aRestartAfterStoppingOpensNoConnection() throws Exception {
        connect(supported());

        dashboard.stop();
        dashboard.restart();
        TimeUnit.SECONDS.sleep(1);

        assertEquals(1, gateway.connections().size());
        assertEquals(GatewayConnection.State.STOPPED, dashboard.status().get().state());
    }

    @Test
    void anUnknownMessageTypeIsAnsweredWithAnError() throws Exception {
        connect(supported());

        gateway.connections().getLast().send("{\"type\":\"future.thing\",\"id\":\"f-1\",\"v\":1}");

        var error = gateway.next(ErrorMessage.class, WAIT);
        assertEquals("f-1", error.id());
        assertEquals(ErrorCode.UNKNOWN_TYPE, error.code());
    }

    @Test
    void aDiscontinuedVersionOpensNoSessionAndAnswersNoRequest() throws Exception {
        connect(new VersionStatus(
                VersionState.DISCONTINUED,
                LocalDate.of(2026, 10, 1),
                null,
                "2.4.0",
                "Companies 2.1 uses an old order format the dashboard no longer reads."));

        var opening = dashboard.open(new PlayerRef(wren, "Wren"), Set.of()).get(5, TimeUnit.SECONDS);
        gateway.send(new SnapshotRequest("snap", Protocol.VERSION, "session", wren));

        var refused = assertInstanceOf(Dashboard.Opening.Refused.class, opening);
        assertEquals("dashboard.discontinued", refused.reason());
        assertTrue(refused.message().contains("2.4.0"));
        assertTrue(refused.message().contains("old order format"));
        assertEquals(
                ErrorCode.DISCONTINUED, gateway.next(ErrorMessage.class, WAIT).code());
        assertFalse(dashboard.notices().isEmpty());
    }
}
