/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.perks;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.company.PerkDefinition;
import de.eldoria.companies.configuration.elements.perks.PerkSettings;
import de.eldoria.companies.data.repository.impl.sqlite.SqLitePerkData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.company.CompanyDetails;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.events.company.CompanyPerkChangeEvent;
import de.eldoria.companies.services.TreasuryService;
import org.bukkit.event.Event;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Buying perks: offered while registered and switched on, bought once with {@code MANAGE_PERKS} from the minimum level
 * on and paid from the treasury, usable while offered and the level holds, paused otherwise.
 */
class PerkServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 3, 12, 0);
    private static final PerkDefinition TOOL = new PerkDefinition("builder-tool", "Builder's tool", "Places", 500, 2);
    private static final PerkDefinition FREE = new PerkDefinition("free-coffee", "Coffee", "Free", 0, 1);

    @TempDir
    Path directory;

    private final UUID owner = UUID.randomUUID();
    private final UUID manager = UUID.randomUUID();
    private final UUID worker = UUID.randomUUID();
    private final List<Event> events = new ArrayList<>();
    private final List<BigDecimal> paid = new ArrayList<>();
    private final PerkSettings settings = new PerkSettings();
    private TreasuryService.Result answer = new TreasuryService.Result(TreasuryService.Outcome.DONE, 0);
    private PerkService perks;
    private int company;

    @BeforeEach
    void setUp() throws Exception {
        MockBukkit.mock();
        SqLiteTestDatabase.create(directory);
        query("INSERT INTO companies(name) VALUES ('Builders')").single().insert();
        company = query("SELECT id FROM companies WHERE name = 'Builders'")
                .single()
                .map(row -> row.getInt("id"))
                .first()
                .orElseThrow();
        perks = new PerkService(new SqLitePerkData(), events::add, () -> NOW);
        perks.settings(() -> settings);
        perks.payment((buyer, actor, amount, note, purchase) -> {
            if (!answer.done()) return CompletableFuture.completedFuture(answer);
            paid.add(amount);
            return purchase.get()
                    .thenApply(stored ->
                            stored ? answer : TreasuryService.Result.of(TreasuryService.Outcome.PURCHASE_FAILED));
        });
        perks.register(TOOL);
        perks.register(FREE);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aMemberWithManagePerksBuysAPerkOnceFromItsLevelOn() {
        assertEquals(PerkService.Outcome.LEVEL, buy(level(1), manager, "builder-tool"));
        assertEquals(PerkService.Outcome.NO_PERMISSION, buy(level(2), worker, "builder-tool"));
        assertEquals(PerkService.Outcome.DONE, buy(level(2), manager, "Builder-Tool"));
        assertEquals(PerkService.Outcome.OWNED, buy(level(2), owner, "builder-tool"));

        assertEquals(List.of(new BigDecimal("500.00")), paid);
        var bought = assertInstanceOf(CompanyPerkChangeEvent.class, events.getFirst());
        assertEquals("builder-tool", bought.perk());
        assertEquals(CompanyPerkChangeEvent.Change.BOUGHT, bought.change());
        assertEquals(manager, bought.actor());
        assertTrue(perks.usable(level(2), "builder-tool"));
    }

    @Test
    void aTreasuryThatDoesNotPayStoresNothing() {
        answer = TreasuryService.Result.of(TreasuryService.Outcome.TREASURY_FUNDS);

        var result = perks.buy(level(3), manager, "builder-tool").join();

        assertEquals(PerkService.Outcome.TREASURY, result.outcome());
        assertEquals(TreasuryService.Outcome.TREASURY_FUNDS, result.treasury().outcome());
        assertEquals(List.of(), perks.perks(level(3)));
        assertEquals(List.of(), events);
    }

    @Test
    void aPerkBoughtBeforeTheTreasuryIsReadyIsRefused() {
        var early = new PerkService(new SqLitePerkData(), events::add, () -> NOW);
        early.register(TOOL);

        var result = early.buy(level(2), manager, "builder-tool").join();

        assertEquals(PerkService.Outcome.TREASURY, result.outcome());
        assertEquals(TreasuryService.Outcome.DISABLED, result.treasury().outcome());
    }

    @Test
    void aPerkBoughtMeanwhileGivesThePriceBackAsOwned() {
        perks.payment((buyer, actor, amount, note, purchase) -> {
            new SqLitePerkData().add(company, "builder-tool", NOW);
            return purchase.get()
                    .thenApply(stored ->
                            stored ? answer : TreasuryService.Result.of(TreasuryService.Outcome.PURCHASE_FAILED));
        });

        assertEquals(PerkService.Outcome.OWNED, buy(level(2), manager, "builder-tool"));
        assertEquals(List.of(), events);
    }

    @Test
    void aPerkThatCouldNotBeStoredIsATreasuryRefusal() {
        perks.payment((buyer, actor, amount, note, purchase) ->
                CompletableFuture.completedFuture(TreasuryService.Result.of(TreasuryService.Outcome.PURCHASE_FAILED)));

        var result = perks.buy(level(2), manager, "builder-tool").join();

        assertEquals(PerkService.Outcome.TREASURY, result.outcome());
        assertEquals(TreasuryService.Outcome.PURCHASE_FAILED, result.treasury().outcome());
    }

    @Test
    void aFreePerkIsStoredWithoutPayment() {
        assertEquals(PerkService.Outcome.DONE, buy(level(1), manager, "free-coffee"));

        assertEquals(List.of(), paid);
        assertTrue(perks.usable(level(1), "free-coffee"));
    }

    @Test
    void settingsOverrideThePriceAndLevelAndSwitchAPerkOff() {
        settings.perk("builder-tool", new PerkSettings.Perk(true, 120.555, 1));

        assertEquals(PerkService.Outcome.DONE, buy(level(1), manager, "builder-tool"));
        assertEquals(List.of(new BigDecimal("120.56")), paid);

        settings.perk("free-coffee", new PerkSettings.Perk(false, null, null));
        assertEquals(PerkService.Outcome.UNKNOWN, buy(level(1), manager, "free-coffee"));
        assertEquals(PerkService.Outcome.UNKNOWN, buy(level(1), manager, "no-such-perk"));
    }

    @Test
    void anOwnedPerkIsPausedBelowItsLevelAndWhileSwitchedOff() {
        buy(level(2), manager, "builder-tool");

        assertFalse(perks.usable(level(1), "builder-tool"));
        assertEquals(PerkService.State.PAUSED, state(level(1), "builder-tool"));
        assertEquals(PerkService.State.OWNED, state(level(2), "builder-tool"));

        settings.perk("builder-tool", new PerkSettings.Perk(false, null, null));
        assertFalse(perks.usable(level(2), "builder-tool"));
        assertEquals(PerkService.State.PAUSED, state(level(2), "builder-tool"));
        assertFalse(perks.perks(level(2)).getFirst().usable());
    }

    @Test
    void offersShowWhatTheCompanyMayBuy() {
        assertEquals(PerkService.State.LOCKED, state(level(1), "builder-tool"));
        assertEquals(PerkService.State.AVAILABLE, state(level(2), "builder-tool"));
        assertEquals(
                List.of("builder-tool", "free-coffee"),
                perks.offers(level(1)).stream().map(offer -> offer.perk().id()).toList());

        settings.perk("free-coffee", new PerkSettings.Perk(false, null, null));
        assertEquals(
                List.of("builder-tool"),
                perks.offers(level(1)).stream().map(offer -> offer.perk().id()).toList());
    }

    @Test
    void perkIdsAreChecked() {
        assertThrowsIllegal(() -> new PerkDefinition("Builder Tool", "x", "x", 1, 1));
        assertThrowsIllegal(() -> new PerkDefinition("tool", "x", "x", -1, 1));
        assertThrowsIllegal(() -> new PerkDefinition("tool", "x", "x", 1, 0));
        assertThrowsIllegal(() -> new PerkDefinition("tool", "x", "x", Double.POSITIVE_INFINITY, 1));
        assertEquals("tool", new PerkDefinition("TOOL", "x", "x", 1, 1).id());
    }

    private static void assertThrowsIllegal(Runnable action) {
        assertThrows(IllegalArgumentException.class, action::run);
    }

    private PerkService.Outcome buy(CompanyProfile company, UUID actor, String perk) {
        return perks.buy(company, actor, perk).join().outcome();
    }

    private PerkService.State state(CompanyProfile company, String perk) {
        return perks.offers(company).stream()
                .filter(offer -> offer.perk().id().equals(perk))
                .findFirst()
                .orElseThrow()
                .state();
    }

    private CompanyProfile level(int level) {
        return new CompanyProfile(
                company,
                "Builders",
                NOW,
                level,
                CompanyDetails.NONE,
                List.of(
                        CompanyMember.of(company, owner, CompanyPermission.OWNER.mask()),
                        CompanyMember.of(company, manager, CompanyPermission.MANAGE_PERKS.mask()),
                        CompanyMember.of(company, worker, CompanyPermission.BUILD.mask())));
    }
}
