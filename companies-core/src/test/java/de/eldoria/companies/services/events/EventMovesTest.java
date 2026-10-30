/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.events;

import de.eldoria.companies.components.event.EventKind;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteServerEventData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.event.ServerEvent;
import de.eldoria.companies.nodes.NodeMessage.ServerEventChanged;
import de.eldoria.companies.services.events.ServerEventService.Change;
import de.eldoria.companies.util.Times;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventMovesTest {
    @TempDir
    Path directory;

    private final List<String> primaryPosts = new CopyOnWriteArrayList<>();
    private final List<String> secondaryPosts = new CopyOnWriteArrayList<>();
    private ServerEventService primary;
    private ServerEventService secondary;

    @BeforeEach
    void setUp() throws Exception {
        SqLiteTestDatabase.create(directory);
        var logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        primary = node(primaryPosts, logger);
        secondary = node(secondaryPosts, logger);
    }

    @Test
    void onlyTheNodeThatWonAMovePostsIt() {
        var now = LocalDateTime.now(Times.UTC);
        var id = primary.create(ServerEvent.draft(
                        "Tax holiday",
                        EventKind.TAX_REDUCTION,
                        5,
                        List.of(),
                        List.of(),
                        now,
                        now.plusHours(2),
                        null,
                        null,
                        null,
                        null,
                        "Nora",
                        now))
                .id();
        primary.run();

        assertEquals(Change.DONE, secondary.end(id, "Nora"));
        assertEquals(Change.CLOSED, primary.end(id, "Ada"));

        assertEquals(List.of("SCHEDULED:Tax holiday", "ACTIVE:Tax holiday"), primaryPosts);
        assertEquals(List.of("ENDED:Tax holiday"), secondaryPosts);
    }

    @Test
    void aMoveOfAnEventThatIsGoneIsNotPosted() {
        var posts = new CopyOnWriteArrayList<String>();
        var logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        var moves = new EventMoves(new SqLiteServerEventData(), (event, change) -> posts.add(change), logger);

        moves.accept(new ServerEventChanged(404, "ACTIVE"));

        assertTrue(posts.isEmpty());
    }

    private static ServerEventService node(List<String> posts, Logger logger) {
        var data = new SqLiteServerEventData();
        var moves = new EventMoves(data, (event, change) -> posts.add(change + ":" + event.name()), logger);
        return new ServerEventService(data, name -> name, moves, Times.UTC, logger);
    }
}
