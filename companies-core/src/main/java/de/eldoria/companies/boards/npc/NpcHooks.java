/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.boards.npc;

import org.bukkit.plugin.Plugin;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads the hooks of the NPC plugins.
 * <p>
 * A hook class is compiled against its NPC plugin, so it is only loaded by name, and only when that plugin is enabled
 * and its hook is switched on. Without the plugin the class is never touched, so a missing plugin cannot cause a
 * {@link NoClassDefFoundError}. A hook that fails to load, for example because the plugin's API changed, is skipped
 * with a warning and the other hooks still load.
 */
public final class NpcHooks {
    static final Map<String, String> KNOWN = known();

    private final Predicate<String> available;
    private final HookFactory factory;
    private final Logger logger;
    private final Map<String, NpcHook> loaded = new ConcurrentHashMap<>();

    /**
     * @param available whether the hook of a plugin may load, by the plugin's name
     * @param factory   creates a hook from its class name
     * @param logger    logger of the warnings
     */
    public NpcHooks(Predicate<String> available, HookFactory factory, Logger logger) {
        this.available = available;
        this.factory = factory;
        this.logger = logger;
    }

    /**
     * Hooks loaded only for enabled plugins whose hook is switched on, created by reflection.
     *
     * @param plugin    the plugin
     * @param switchedOn whether the hook of a plugin is switched on, by the plugin's name
     * @return the hooks, none loaded yet
     */
    public static NpcHooks create(Plugin plugin, Predicate<String> switchedOn) {
        var plugins = plugin.getServer().getPluginManager();
        return new NpcHooks(
                name -> plugins.isPluginEnabled(name) && switchedOn.test(name),
                HookFactory.reflective(NpcHooks.class.getClassLoader()),
                plugin.getLogger());
    }

    private static Map<String, String> known() {
        var known = new LinkedHashMap<String, String>();
        known.put("Citizens", "de.eldoria.companies.hooks.citizens.CitizensHook");
        known.put("FancyNpcs", "de.eldoria.companies.hooks.fancynpcs.FancyNpcsHook");
        return Collections.unmodifiableMap(known);
    }

    /**
     * Loads and enables the hook of every available NPC plugin.
     *
     * @param plugin  the plugin owning the hooks' listeners
     * @param handler told about right-clicks on NPCs
     */
    public void load(Plugin plugin, NpcClickHandler handler) {
        KNOWN.forEach((name, className) -> {
            if (loaded.containsKey(name) || !available.test(name)) return;
            try {
                var hook = factory.create(className);
                hook.enable(plugin, handler);
                loaded.put(name, hook);
                logger.info("Hooked into " + name + " for NPC boards.");
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                logger.log(
                        Level.WARNING, "Could not hook into " + name + ". NPCs of " + name + " cannot open boards.", e);
            }
        });
    }

    /**
     * Disables every loaded hook.
     */
    public void unload() {
        loaded.forEach(this::disable);
        loaded.clear();
    }

    private void disable(String name, NpcHook hook) {
        try {
            hook.disable();
        } catch (LinkageError | RuntimeException e) {
            logger.log(Level.WARNING, "Could not unhook from " + name + ".", e);
        }
    }

    /**
     * @param name name of an NPC plugin, ignoring case
     * @return the loaded hook of that plugin
     */
    public Optional<NpcHook> hook(String name) {
        return loaded.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    /**
     * @return the names of the loaded hooks
     */
    public List<String> names() {
        return KNOWN.keySet().stream().filter(loaded::containsKey).toList();
    }

    /**
     * Creates a hook from its class name.
     */
    @FunctionalInterface
    public interface HookFactory {
        /**
         * Creates hooks by loading their class and calling the constructor without arguments.
         *
         * @param loader class loader holding the hook classes
         * @return the factory
         */
        static HookFactory reflective(ClassLoader loader) {
            return className -> Class.forName(className, true, loader)
                    .asSubclass(NpcHook.class)
                    .getDeclaredConstructor()
                    .newInstance();
        }

        /**
         * @param className binary name of the hook class
         * @return a new hook
         * @throws ReflectiveOperationException when the class cannot be loaded or created
         */
        NpcHook create(String className) throws ReflectiveOperationException;
    }
}
