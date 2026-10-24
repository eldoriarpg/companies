/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.util;

import de.eldoria.companies.components.ranking.TimeRange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Stored times are UTC; the server's zone decides how they are shown and where a day starts.
 */
class TimesTest {
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final Pattern MACHINE_TIME =
            Pattern.compile("LocalDateTime\\.now\\(\\)|LocalDate\\.now\\(\\)|Clock\\.systemDefaultZone\\(\\)");

    @BeforeEach
    void useBerlin() {
        Times.useZone(() -> BERLIN);
    }

    @AfterEach
    void useMachineZone() {
        Times.useZone(ZoneId::systemDefault);
    }

    @Test
    void aStoredTimeIsShownInTheServersZoneWithSummerTime() {
        assertEquals(
                "2026-07-01T14:00",
                Times.local(LocalDateTime.of(2026, 7, 1, 12, 0))
                        .toLocalDateTime()
                        .toString());
        assertEquals(
                "2026-01-01T13:00",
                Times.local(LocalDateTime.of(2026, 1, 1, 12, 0))
                        .toLocalDateTime()
                        .toString());
    }

    @Test
    void aDayStartsAtMidnightOfTheServersZone() {
        assertEquals(LocalDateTime.of(2026, 6, 30, 22, 0), Times.startOfDay(LocalDate.of(2026, 7, 1)));
    }

    @Test
    void aRangeCoversTheServersDayAndTheDaysBefore() {
        var lateEvening = LocalDateTime.of(2026, 7, 1, 23, 30);

        assertEquals(
                Optional.of(LocalDateTime.of(2026, 6, 25, 22, 0)), Times.start(TimeRange.LAST_7_DAYS, lateEvening));
        assertEquals(Optional.empty(), Times.start(TimeRange.ALL, lateEvening));
    }

    @Test
    void mainCodeTakesItsTimeFromTheUtcClock() throws IOException {
        var root = Path.of("src/main/java");
        List<String> offenders;
        try (var files = Files.walk(root)) {
            offenders = files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.endsWith("Times.java"))
                    .filter(path -> {
                        try {
                            return MACHINE_TIME.matcher(Files.readString(path)).find();
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .map(path -> root.relativize(path).toString())
                    .toList();
        }
        assertEquals(List.of(), offenders);
    }
}
