/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.prices;

import de.eldoria.companies.components.price.PriceRecorder;
import de.eldoria.companies.components.price.PriceSource;
import de.eldoria.companies.components.price.ShopQuote;
import de.eldoria.companies.configuration.elements.prices.PriceSettings;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * The price sources of this server: Companies' own for the shop plugins it knows and those other plugins register.
 * Each source gets a recorder when it registers, which hands its trades to the {@link TradeLog} while the source is
 * registered and not switched off in {@code prices.yml}. A source is dropped when the plugin that registered it is
 * disabled. Every call into a source is guarded, so a source that throws is logged and skipped. Safe to use from any
 * thread; the sources are asked for their offers on the main thread.
 */
public final class PriceSources implements Listener {
    /**
     * The longest id a source may have, as long as the column that stores it.
     */
    public static final int MAX_ID = 64;

    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]{1," + MAX_ID + "}");

    private final Map<String, Registered> registered = new LinkedHashMap<>();
    private final TradeLog trades;
    private final Logger logger;
    private volatile Supplier<PriceSettings> settings = PriceSettings::new;

    /**
     * @param trades takes the trades of the sources
     * @param logger reports sources that fail
     */
    public PriceSources(TradeLog trades, Logger logger) {
        this.trades = trades;
        this.logger = logger;
    }

    /**
     * @param settings the settings of shop prices, read on every use
     */
    public void settings(Supplier<PriceSettings> settings) {
        this.settings = settings;
    }

    /**
     * Registers a source, replacing one with the same id, and hands it its recorder.
     *
     * @param plugin the plugin the source belongs to; it is dropped when the plugin is disabled
     * @param source the source
     */
    public void register(Plugin plugin, PriceSource source) {
        var id = source.id();
        if (id == null || !ID.matcher(key(id)).matches()) {
            logger.warning("The price source " + id + " of " + plugin.getName() + " was refused: its id is 1 to "
                    + MAX_ID + " letters, digits, _, - and .");
            return;
        }
        var key = key(id);
        var entry = new Registered(plugin, source);
        synchronized (this) {
            registered.put(key, entry);
        }
        logger.info("Shop prices can come from " + source.id() + ".");
        PriceRecorder recorder = trade -> {
            if (current(key) == entry && settings.get().sourceEnabled(key)) trades.trade(key, trade);
        };
        try {
            source.record(recorder);
        } catch (LinkageError | RuntimeException e) {
            logger.log(Level.WARNING, "The price source " + key + " could not start recording trades", e);
        }
    }

    /**
     * @param id id of the source to remove
     */
    public synchronized void unregister(String id) {
        registered.remove(key(id));
    }

    /**
     * Drops the sources of a plugin that is disabled.
     *
     * @param event the disabling
     */
    @EventHandler
    public synchronized void onDisable(PluginDisableEvent event) {
        registered.values().removeIf(entry -> entry.plugin().equals(event.getPlugin()));
    }

    /**
     * @return the ids of the registered sources that are not switched off, lower case, in the order they registered
     */
    public List<String> sources() {
        var current = settings.get();
        return registeredKeys().stream().filter(current::sourceEnabled).toList();
    }

    /**
     * Asks every source in use for the offers of its shops. Called on the main thread.
     *
     * @return the offers per source id, and the ids of the sources that failed
     */
    public Sample sample() {
        Map<String, Collection<ShopQuote>> sampled = new LinkedHashMap<>();
        Set<String> failed = new LinkedHashSet<>();
        for (var key : sources()) {
            var entry = current(key);
            if (entry == null) continue;
            try {
                var quotes = entry.source().quotes();
                if (quotes != null) sampled.put(key, List.copyOf(quotes));
            } catch (LinkageError | RuntimeException e) {
                logger.log(Level.WARNING, "The price source " + key + " failed", e);
                failed.add(key);
            }
        }
        return new Sample(sampled, failed);
    }

    /**
     * @return the ids of the registered sources, switched off or not, in the order they registered
     */
    public List<String> registered() {
        return registeredKeys();
    }

    private synchronized Registered current(String key) {
        return registered.get(key);
    }

    private synchronized List<String> registeredKeys() {
        return new ArrayList<>(registered.keySet());
    }

    private static String key(String id) {
        return id.toLowerCase(Locale.ROOT);
    }

    private record Registered(Plugin plugin, PriceSource source) {}

    /**
     * What the sources offered when they were sampled.
     *
     * @param quotes the offers per source id
     * @param failed the ids of the sources that failed
     */
    public record Sample(Map<String, Collection<ShopQuote>> quotes, Set<String> failed) {}
}
