/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.prices;

import de.eldoria.companies.components.price.PriceBasis;
import de.eldoria.companies.components.price.PriceRecorder;
import de.eldoria.companies.components.price.PriceSource;
import de.eldoria.companies.components.price.ShopOrigin;
import de.eldoria.companies.components.price.ShopQuote;
import de.eldoria.companies.components.price.ShopSide;
import de.eldoria.companies.components.price.ShopTrade;
import de.eldoria.companies.configuration.elements.prices.PriceSettings;
import de.eldoria.companies.data.wrapper.price.TradeDay;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shop prices against a fake source: trades noted and added to their day, offers sampled per server, items that are
 * not plain and invalid prices left out, sources switched off, failing or dropped, and the baseline worked out from it
 * all.
 */
class ShopPricesTest {
    private static final UUID NODE = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    private final MemoryPriceData data = new MemoryPriceData();
    private final PriceSettings settings = new PriceSettings();
    private LocalDateTime now = LocalDateTime.of(2026, 11, 1, 12, 0);
    private TradeLog log;
    private PriceSources sources;
    private ShopPrices prices;
    private Plugin plugin;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        log = new TradeLog(data, () -> now.toLocalDate(), Logger.getAnonymousLogger());
        sources = new PriceSources(log, Logger.getAnonymousLogger());
        sources.settings(() -> settings);
        prices = new ShopPrices(
                sources,
                log,
                data,
                () -> settings,
                NODE,
                () -> now,
                Runnable::run,
                Runnable::run,
                Logger.getAnonymousLogger());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void tradesOfPlainItemsAddUpPerDayAndTheRestIsDropped() {
        var source = new FakeSource("Fake");
        sources.register(plugin, source);

        source.trade(new ShopTrade(new ItemStack(Material.STONE), 4, 8, ShopSide.SELL, ShopOrigin.PLAYER));
        source.trade(new ShopTrade(new ItemStack(Material.STONE), 2, 6, ShopSide.SELL, ShopOrigin.PLAYER));
        source.trade(new ShopTrade(enchanted(), 1, 100, ShopSide.SELL, ShopOrigin.PLAYER));
        source.trade(new ShopTrade(new ItemStack(Material.STONE), 0, 5, ShopSide.SELL, ShopOrigin.PLAYER));
        source.trade(new ShopTrade(new ItemStack(Material.STONE), 1, -1, ShopSide.SELL, ShopOrigin.PLAYER));
        prices.tick();

        var day = data.trades(now.toLocalDate()).getFirst();
        assertEquals(1, data.trades.size());
        assertEquals("fake", day.source());
        assertEquals("STONE", day.material());
        assertEquals(2, day.trades());
        assertEquals(6, day.items());
        assertEquals(14, day.total(), 1e-9);
        assertEquals(2, day.minUnit(), 1e-9);
        assertEquals(3, day.maxUnit(), 1e-9);
    }

    @Test
    void tradesAreKeptWhenTheyCanNotBeStored() {
        var source = new FakeSource("fake");
        sources.register(plugin, source);
        source.trade(new ShopTrade(new ItemStack(Material.STONE), 1, 2, ShopSide.BUY, ShopOrigin.PLAYER));

        data.failing = true;
        log.flush();
        data.failing = false;
        log.flush();

        assertEquals(1, data.trades.values().iterator().next().trades());
    }

    @Test
    void aSwitchedOffOrDroppedSourceRecordsNothingAndIsNotSampled() {
        var source = new FakeSource("fake");
        source.quotes.add(new ShopQuote(new ItemStack(Material.STONE), 1, 2, ShopSide.BUY, ShopOrigin.SERVER));
        sources.register(plugin, source);

        settings.sources(Map.of("fake", false));
        source.trade(new ShopTrade(new ItemStack(Material.STONE), 1, 2, ShopSide.BUY, ShopOrigin.PLAYER));
        assertTrue(sources.sources().isEmpty());
        assertTrue(sources.sample().quotes().isEmpty());

        settings.sources(Map.of());
        sources.onDisable(new PluginDisableEvent(plugin));
        source.trade(new ShopTrade(new ItemStack(Material.STONE), 1, 2, ShopSide.BUY, ShopOrigin.PLAYER));
        log.flush();

        assertTrue(sources.sources().isEmpty());
        assertTrue(data.trades.isEmpty());
    }

    @Test
    void aSampleReplacesTheServersOffersAndAFailingSourceIsSkipped() {
        var source = new FakeSource("fake");
        source.quotes.add(new ShopQuote(new ItemStack(Material.STONE), 2, 2, ShopSide.BUY, ShopOrigin.PLAYER));
        source.quotes.add(new ShopQuote(new ItemStack(Material.STONE), 1, 3, ShopSide.BUY, ShopOrigin.PLAYER));
        source.quotes.add(new ShopQuote(new ItemStack(Material.STONE), 1, 5, ShopSide.BUY, ShopOrigin.PLAYER));
        source.quotes.add(new ShopQuote(enchanted(), 1, 50, ShopSide.BUY, ShopOrigin.PLAYER));
        sources.register(plugin, source);
        sources.register(plugin, new FakeSource("broken") {
            @Override
            public Collection<ShopQuote> quotes() {
                throw new IllegalStateException("broken");
            }
        });

        prices.tick();

        var groups = data.quotes.get(NODE);
        assertEquals(1, groups.size());
        var group = groups.getFirst();
        assertEquals(3, group.quotes());
        assertEquals(4, group.items());
        assertEquals(3, group.medianUnit(), 1e-9);
        assertEquals(1, group.minUnit(), 1e-9);
        assertEquals(5, group.maxUnit(), 1e-9);
    }

    @Test
    void theBaselineComesFromTheStoredTradesAndSamplesAndOldTradesArePurged() {
        var source = new FakeSource("fake");
        source.quotes.add(new ShopQuote(new ItemStack(Material.STONE), 1, 4, ShopSide.BUY, ShopOrigin.SERVER));
        sources.register(plugin, source);
        for (var i = 0; i < 5; i++) {
            source.trade(new ShopTrade(new ItemStack(Material.STONE), 1, 2, ShopSide.SELL, ShopOrigin.PLAYER));
        }
        prices.tick();
        data.replaceQuotes(
                NODE, Set.of("fake"), QuoteSamples.groups(sources.sample().quotes(), now));

        now = now.plusDays(settings.days() - 1L);
        prices.refreshBaselines();

        var baseline = data.baselines.getFirst();
        assertEquals("STONE", baseline.material());
        assertEquals(3.0, baseline.unit(), 1e-9);
        assertEquals(PriceBasis.SERVER, baseline.basis());

        now = now.plusDays(2);
        prices.refreshBaselines();
        assertTrue(data.trades.isEmpty());
        assertTrue(data.baselines.isEmpty());
    }

    @Test
    void onlyTheShopsAreAskedOnTheMainThreadAndTheSampleIsStoredOffIt() {
        var source = new FakeSource("fake");
        source.quotes.add(new ShopQuote(new ItemStack(Material.STONE), 1, 2, ShopSide.BUY, ShopOrigin.SERVER));
        sources.register(plugin, source);
        List<Runnable> main = new ArrayList<>();
        List<Runnable> worker = new ArrayList<>();
        var queued = new ShopPrices(
                sources,
                log,
                data,
                () -> settings,
                NODE,
                () -> now,
                main::add,
                worker::add,
                Logger.getAnonymousLogger());

        queued.tick();
        assertEquals(1, main.size());
        main.removeFirst().run();
        assertTrue(data.quotes.isEmpty());
        assertEquals(1, worker.size());
        worker.removeFirst().run();

        assertEquals(1, data.quotes.get(NODE).size());
    }

    @Test
    void aSourceThatFailsOrIsGoneKeepsItsLastSampleUntilItAgesOut() {
        var failing = new boolean[1];
        var source = new FakeSource("fake") {
            @Override
            public Collection<ShopQuote> quotes() {
                if (failing[0]) throw new IllegalStateException("down");
                return super.quotes();
            }
        };
        source.quotes.add(new ShopQuote(new ItemStack(Material.STONE), 1, 2, ShopSide.BUY, ShopOrigin.SERVER));
        sources.register(plugin, source);
        prices.tick();

        failing[0] = true;
        now = now.plusMinutes(settings.sampleMinutes());
        prices.tick();
        assertEquals(1, data.quotes.get(NODE).size());

        sources.unregister("fake");
        now = now.plusMinutes(settings.sampleMinutes());
        prices.tick();
        assertEquals(1, data.quotes.get(NODE).size());
        assertTrue(data.quotes(ShopPrices.oldestSample(now.plusDays(settings.days()), settings))
                .isEmpty());
    }

    @Test
    void aSourceRegisteredAgainOrUnregisteredDropsTheTradesOfItsOldRecorder() {
        var old = new FakeSource("fake");
        sources.register(plugin, old);
        var replacement = new FakeSource("fake");
        sources.register(plugin, replacement);

        old.trade(new ShopTrade(new ItemStack(Material.STONE), 1, 2, ShopSide.BUY, ShopOrigin.PLAYER));
        replacement.trade(new ShopTrade(new ItemStack(Material.STONE), 1, 3, ShopSide.BUY, ShopOrigin.PLAYER));
        sources.unregister("FAKE");
        replacement.trade(new ShopTrade(new ItemStack(Material.STONE), 1, 4, ShopSide.BUY, ShopOrigin.PLAYER));
        log.flush();

        assertEquals(3, data.trades.values().iterator().next().total(), 1e-9);
        assertTrue(sources.sources().isEmpty());
    }

    @Test
    void aSourceWithoutAUsableIdIsRefused() {
        sources.register(plugin, new FakeSource(" "));
        sources.register(plugin, new FakeSource("chest,shop"));
        sources.register(plugin, new FakeSource("x".repeat(PriceSources.MAX_ID + 1)));
        sources.register(plugin, new FakeSource("x".repeat(PriceSources.MAX_ID)));

        assertEquals(List.of("x".repeat(PriceSources.MAX_ID)), sources.sources());
    }

    @Test
    void aTotalThatKeepsFailingAloneIsDroppedWhileTheOthersAreStored() {
        var picky = new MemoryPriceData() {
            @Override
            public synchronized boolean addTrades(List<TradeDay> added) {
                if (added.stream().anyMatch(day -> day.material().equals("DIRT"))) return false;
                return super.addTrades(added);
            }
        };
        var pickyLog = new TradeLog(picky, () -> now.toLocalDate(), Logger.getAnonymousLogger());
        pickyLog.trade("fake", new ShopTrade(new ItemStack(Material.STONE), 1, 2, ShopSide.BUY, ShopOrigin.PLAYER));
        pickyLog.trade("fake", new ShopTrade(new ItemStack(Material.DIRT), 1, 2, ShopSide.BUY, ShopOrigin.PLAYER));

        for (var i = 0; i < 3; i++) pickyLog.flush();
        pickyLog.flush();

        assertEquals(1, picky.trades.size());
        assertEquals("STONE", picky.trades.values().iterator().next().material());
    }

    @Test
    void totalsAreKeptWhileTheDatabaseStoresNone() {
        sources.register(plugin, new FakeSource("fake"));
        log.trade("fake", new ShopTrade(new ItemStack(Material.STONE), 1, 2, ShopSide.BUY, ShopOrigin.PLAYER));

        data.failing = true;
        for (var i = 0; i < 5; i++) log.flush();
        data.failing = false;
        log.flush();

        assertEquals(1, data.trades.size());
    }

    private static ItemStack enchanted() {
        var item = new ItemStack(Material.STONE);
        item.addUnsafeEnchantment(Enchantment.UNBREAKING, 1);
        return item;
    }

    private static class FakeSource implements PriceSource {
        private final String id;
        protected final List<ShopQuote> quotes = new ArrayList<>();
        private PriceRecorder recorder = trade -> {};

        FakeSource(String id) {
            this.id = id;
        }

        void trade(ShopTrade trade) {
            recorder.trade(trade);
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String name() {
            return id;
        }

        @Override
        public Collection<ShopQuote> quotes() {
            return quotes;
        }

        @Override
        public void record(PriceRecorder recorder) {
            this.recorder = recorder;
        }
    }
}
