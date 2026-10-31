/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.areas;

import de.eldoria.companies.api.AreaApi;
import de.eldoria.companies.components.area.AreaProvider;
import de.eldoria.companies.configuration.elements.areas.AreaSettings;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The area providers of this server: Companies' own for PlotSquared and WorldGuard and those other plugins register.
 * A location is looked up in the providers in the order of {@link AreaSettings#providerOrder()}, skipping those
 * switched off. Every call into a provider goes through {@link #call}, so a provider that throws is logged and treated
 * as not knowing the area. A provider is dropped when the plugin that registered it is disabled. Safe to use from any
 * thread; the providers themselves are only called where the caller is, the main thread.
 */
public final class AreaProviders implements AreaApi, Listener {
    private final Map<String, Registered> registered = new LinkedHashMap<>();
    private final Logger logger;
    private volatile Supplier<AreaSettings> settings = AreaSettings::new;

    /**
     * @param logger reports providers that fail
     */
    public AreaProviders(Logger logger) {
        this.logger = logger;
    }

    /**
     * @param settings the settings of areas, read on every lookup
     */
    public void settings(Supplier<AreaSettings> settings) {
        this.settings = settings;
    }

    @Override
    public synchronized void register(Plugin plugin, AreaProvider provider) {
        registered.put(key(provider.id()), new Registered(plugin, provider));
        logger.info("Areas can come from " + provider.id() + ".");
    }

    @Override
    public synchronized void unregister(String id) {
        registered.remove(key(id));
    }

    @Override
    public List<String> providers() {
        return ordered().stream().map(provider -> key(provider.id())).toList();
    }

    /**
     * Drops the providers of a plugin that is disabled.
     *
     * @param event the disabling
     */
    @EventHandler
    public synchronized void onDisable(PluginDisableEvent event) {
        registered.values().removeIf(entry -> entry.plugin().equals(event.getPlugin()));
    }

    /**
     * @param id id of a provider
     * @return whether the provider is registered and not switched off
     */
    public boolean available(String id) {
        return provider(id).isPresent();
    }

    /**
     * Calls a provider, logging a failure.
     *
     * @param id       id of the provider
     * @param call     what to ask it
     * @param fallback the answer when the provider is not there or fails
     * @param <T>      the answer
     * @return the provider's answer, the fallback when it is not there or fails
     */
    public <T> T call(String id, Function<AreaProvider, T> call, T fallback) {
        var provider = provider(id);
        if (provider.isEmpty()) return fallback;
        try {
            var answer = call.apply(provider.get());
            return answer == null ? fallback : answer;
        } catch (LinkageError | RuntimeException e) {
            logger.log(Level.WARNING, "The area provider " + id + " failed", e);
            return fallback;
        }
    }

    /**
     * @param location a location
     * @return the area that decides at the location, of the first provider that knows one, empty when none does
     */
    public Optional<AreaRef> at(Location location) {
        return areasAt(location).stream().findFirst();
    }

    /**
     * @param location a location
     * @return every area holding the location, the providers in their order and each provider's areas in its own
     */
    public List<AreaRef> areasAt(Location location) {
        List<AreaRef> found = new ArrayList<>();
        for (var provider : ordered()) {
            var id = key(provider.id());
            for (var ref : call(id, it -> it.areasAt(location), List.<String>of())) {
                found.add(new AreaRef(id, call(id, it -> it.canonical(ref), ref)));
            }
        }
        return found;
    }

    private synchronized Optional<AreaProvider> provider(String id) {
        var key = key(id);
        if (!settings.get().providerEnabled(key)) return Optional.empty();
        return Optional.ofNullable(registered.get(key)).map(Registered::provider);
    }

    private synchronized List<AreaProvider> ordered() {
        var current = settings.get();
        var order = current.providerOrder();
        List<String> keys = new ArrayList<>(registered.keySet());
        List<AreaProvider> providers = new ArrayList<>();
        for (var entry : registered.entrySet()) {
            if (current.providerEnabled(entry.getKey()))
                providers.add(entry.getValue().provider());
        }
        providers.sort(Comparator.comparingInt(provider -> {
            var key = key(provider.id());
            var at = order.indexOf(key);
            return at >= 0 ? at : order.size() + keys.indexOf(key);
        }));
        return providers;
    }

    private static String key(String id) {
        return id.toLowerCase(Locale.ROOT);
    }

    private record Registered(Plugin plugin, AreaProvider provider) {}
}
