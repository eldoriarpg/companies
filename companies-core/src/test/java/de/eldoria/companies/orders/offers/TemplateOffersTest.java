/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders.offers;

import de.eldoria.companies.components.event.EventKind;
import de.eldoria.companies.components.event.EventState;
import de.eldoria.companies.configuration.elements.events.EventSettings;
import de.eldoria.companies.configuration.elements.events.EventTemplate;
import de.eldoria.companies.configuration.elements.events.OfferTemplate;
import de.eldoria.companies.configuration.elements.systemorders.MoneySource;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteOfferData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.event.ServerEvent;
import de.eldoria.companies.data.wrapper.offer.EventOffer;
import de.eldoria.companies.data.wrapper.offer.OfferKind;
import de.eldoria.companies.data.wrapper.offer.OfferState;
import de.eldoria.companies.services.events.EventSchedule;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The goals and contests of a template are created with its event: checked as the commands check them, on the event
 * with its times, named after the event unless they have a name, and once per event; a refused one is logged with
 * each event and left out.
 */
class TemplateOffersTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 12, 0);

    @TempDir
    Path directory;

    private SqLiteOfferData offers;
    private final EventSettings settings = new EventSettings();
    private final AtomicInteger checks = new AtomicInteger();
    private final List<String> warnings = new ArrayList<>();
    private TemplateOffers templateOffers;

    @BeforeEach
    void setUp() throws Exception {
        MockBukkit.mock();
        SqLiteTestDatabase.create(directory);
        offers = new SqLiteOfferData(SqLiteTestDatabase.mapper());
        var logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                warnings.add(record.getMessage());
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        });
        var harvest = new EventTemplate("harvest", EventKind.TAX_REDUCTION, 5);
        harvest.offers(List.of(
                offer(OfferKind.GOAL, "", "oak_log", "", 500),
                offer(OfferKind.CONTEST, "Log race", "", "#logs", 300),
                offer(OfferKind.GOAL, "Broken", "not_an_item", "", 10)));
        var quiet = new EventTemplate("quiet", EventKind.TAX_REDUCTION, 5);
        var broken = new EventTemplate("broken", EventKind.TAX_REDUCTION, 5);
        broken.offers(List.of(offer(OfferKind.GOAL, "Broken", "not_an_item", "", 10)));
        var funded = new EventTemplate("funded", EventKind.TAX_REDUCTION, 5);
        var paid = offer(OfferKind.GOAL, "", "oak_log", "", 500);
        paid.money("treasury");
        funded.offers(List.of(paid));
        var twin = new EventTemplate("twin", EventKind.TAX_REDUCTION, 5);
        twin.offers(List.of(
                offer(OfferKind.GOAL, "Logs", "oak_log", "", 500), offer(OfferKind.GOAL, "Logs", "oak_log", "", 500)));
        settings.templates(List.of(harvest, quiet, broken, funded, twin));
        templateOffers = new TemplateOffers(
                () -> settings,
                offers,
                checks::incrementAndGet,
                Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC),
                logger);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theOffersOfTheTemplateRunWithItsEvent() {
        templateOffers.accept(event(7, "harvest"));

        var created = offers.offers(10).stream()
                .sorted(Comparator.comparingInt(EventOffer::id))
                .toList();
        assertEquals(2, created.size());
        var goal = created.getFirst();
        assertEquals(OfferKind.GOAL, goal.kind());
        assertEquals("Harvest week", goal.name());
        assertEquals(7, goal.event());
        assertEquals(NOW.plusHours(2), goal.starts());
        assertEquals(NOW.plusHours(50), goal.ends());
        assertEquals(OfferState.SCHEDULED, goal.state());
        assertEquals(EventSchedule.CREATOR, goal.createdBy());
        var contest = created.get(1);
        assertEquals(OfferKind.CONTEST, contest.kind());
        assertEquals("Log race", contest.name());
        assertEquals(List.of(100.0, 50.0), contest.prizes());
        assertEquals(1, checks.get());
        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().contains("harvest"), warnings.getFirst());
    }

    @Test
    void aRefusedOfferIsLoggedForEachEventAndAnEventOfNoTemplateCreatesNone() {
        templateOffers.accept(event(7, "harvest"));
        templateOffers.accept(event(8, "harvest"));
        templateOffers.accept(event(9, null));
        templateOffers.accept(event(10, "unknown"));

        assertEquals(4, offers.offers(10).size());
        assertEquals(2, warnings.size());
        assertTrue(warnings.getFirst().contains("not_an_item"), warnings.getFirst());
    }

    @Test
    void anEventThatHasTheOffersOfItsTemplateGetsNoneAgain() {
        templateOffers.accept(event(7, "harvest"));
        templateOffers.accept(event(7, "harvest"));

        assertEquals(2, offers.offers(10).size());
        assertEquals(1, checks.get());
    }

    @Test
    void anOfferThatCouldNotBeStoredIsCreatedWhenTheEventIsHandedOverAgain() {
        var failures = new AtomicInteger(1);
        var flaky = new SqLiteOfferData(SqLiteTestDatabase.mapper()) {
            @Override
            public Optional<Integer> create(EventOffer offer) {
                if (offer.kind() == OfferKind.CONTEST && failures.getAndDecrement() > 0) {
                    throw new IllegalStateException("down");
                }
                return super.create(offer);
            }
        };
        var retrying = new TemplateOffers(
                () -> settings,
                flaky,
                checks::incrementAndGet,
                Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC),
                Logger.getAnonymousLogger());

        assertThrows(IllegalStateException.class, () -> retrying.accept(event(7, "harvest")));
        assertEquals(1, offers.offers(10).size());
        retrying.accept(event(7, "harvest"));

        var kinds = offers.offers(10).stream().map(EventOffer::kind).sorted().toList();
        assertEquals(List.of(OfferKind.GOAL, OfferKind.CONTEST), kinds);
    }

    @Test
    void twoEqualOffersOfATemplateAreBothMadeUpAfterOneCouldNotBeStored() {
        var calls = new AtomicInteger();
        var flaky = new SqLiteOfferData(SqLiteTestDatabase.mapper()) {
            @Override
            public Optional<Integer> create(EventOffer offer) {
                if (calls.incrementAndGet() == 2) return Optional.empty();
                return super.create(offer);
            }
        };
        var retrying = new TemplateOffers(
                () -> settings,
                flaky,
                checks::incrementAndGet,
                Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC),
                Logger.getAnonymousLogger());

        assertThrows(IllegalStateException.class, () -> retrying.accept(event(7, "twin")));
        retrying.accept(event(7, "twin"));
        retrying.accept(event(7, "twin"));

        assertEquals(2, offers.offers(10).size());
    }

    @Test
    void aTemplateWithoutOffersOrWithOnlyRefusedOnesCreatesNothing() {
        templateOffers.accept(event(7, "quiet"));
        templateOffers.accept(event(8, "broken"));

        assertTrue(offers.offers(10).isEmpty());
        assertEquals(0, checks.get());
        assertEquals(1, warnings.size());
    }

    @Test
    void theNameOfTheEventIsCutToTheLengthOfAnOfferName() {
        templateOffers.accept(event(7, "harvest", "Autumn harvest festival weekend of the guilds"));

        var goal = offers.offers(10).stream()
                .filter(offer -> offer.kind() == OfferKind.GOAL)
                .findFirst()
                .orElseThrow();
        assertEquals("Autumn harvest festival weekend", goal.name());
        assertEquals(EventOffer.MAX_NAME_LENGTH, goal.name().length());
    }

    @Test
    void anOfferPaidByAnAccountKeepsTheAccount() {
        templateOffers.accept(event(7, "funded"));

        var goal = offers.offers(10).getFirst();
        assertEquals(MoneySource.SourceType.ACCOUNT, goal.source());
        assertEquals("treasury", goal.account());
    }

    private static OfferTemplate offer(OfferKind kind, String name, String material, String criteria, int amount) {
        var offer = new OfferTemplate();
        offer.kind(kind);
        offer.name(name);
        offer.material(material);
        offer.criteria(criteria);
        offer.amount(amount);
        offer.price(2);
        offer.chunk(50);
        offer.prizes(kind == OfferKind.CONTEST ? "100+50" : "");
        return offer;
    }

    private static ServerEvent event(int id, @Nullable String template) {
        return event(id, template, "Harvest week");
    }

    private static ServerEvent event(int id, @Nullable String template, String name) {
        return new ServerEvent(
                id,
                name,
                EventKind.TAX_REDUCTION,
                5,
                List.of(),
                List.of(),
                NOW.plusHours(2),
                NOW.plusHours(50),
                null,
                null,
                null,
                0,
                EventState.SCHEDULED,
                template,
                "events.yml",
                NOW,
                null,
                null);
    }
}
