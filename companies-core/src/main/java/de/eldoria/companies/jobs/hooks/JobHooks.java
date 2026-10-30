/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs.hooks;

import de.eldoria.companies.data.wrapper.job.Job;
import de.eldoria.companies.data.wrapper.job.JobArea;
import de.eldoria.companies.data.wrapper.job.JobLand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads the hooks of the plugins jobs need: WorldGuard and PlotSquared for land and access, WorldEdit (or
 * FastAsyncWorldEdit) for selections.
 * <p>
 * A hook class is compiled against its plugin, so it is only loaded by name, and only when that plugin is enabled and
 * its hook is switched on. Without the plugin the class is never touched, so a missing plugin cannot cause a
 * {@link NoClassDefFoundError}. A hook that fails to load, for example because the plugin's API changed, is skipped
 * with a warning. A hook that throws while a job is posted is logged and refuses the area, as its land can not be told;
 * one that throws later is logged and retried.
 */
public final class JobHooks {
    static final Map<String, String> LANDS = lands();
    static final Map<String, String> SELECTIONS = selections();

    private final Predicate<String> available;
    private final Factory factory;
    private final Logger logger;
    private final List<JobLandHook> lands = new CopyOnWriteArrayList<>();
    private @Nullable SelectionHook selection;

    /**
     * @param available whether the hook of a plugin may load, by the plugin's name
     * @param factory   creates a hook from its class name
     * @param logger    logger of the warnings
     */
    public JobHooks(Predicate<String> available, Factory factory, Logger logger) {
        this.available = available;
        this.factory = factory;
        this.logger = logger;
    }

    /**
     * Hooks loaded only for enabled plugins whose hook is switched on, created by reflection.
     *
     * @param plugin     the plugin
     * @param switchedOn whether the hook of a plugin is switched on, by the plugin's name
     * @return the hooks, none loaded yet
     */
    public static JobHooks create(Plugin plugin, Predicate<String> switchedOn) {
        var plugins = plugin.getServer().getPluginManager();
        return new JobHooks(
                name -> plugins.isPluginEnabled(name) && switchedOn.test(name),
                Factory.reflective(JobHooks.class.getClassLoader()),
                plugin.getLogger());
    }

    private static Map<String, String> lands() {
        var known = new LinkedHashMap<String, String>();
        known.put("PlotSquared", "de.eldoria.companies.hooks.plotsquared.PlotSquaredJobs");
        known.put("WorldGuard", "de.eldoria.companies.hooks.worldguard.WorldGuardJobs");
        return Collections.unmodifiableMap(known);
    }

    private static Map<String, String> selections() {
        var known = new LinkedHashMap<String, String>();
        known.put("FastAsyncWorldEdit", "de.eldoria.companies.hooks.worldedit.WorldEditSelections");
        known.put("WorldEdit", "de.eldoria.companies.hooks.worldedit.WorldEditSelections");
        return Collections.unmodifiableMap(known);
    }

    /**
     * Loads the hook of every available plugin.
     */
    public void load() {
        LANDS.forEach((name, className) -> {
            if (!available.test(name)) return;
            try {
                lands.add((JobLandHook) factory.create(className));
                logger.info("Hooked into " + name + " for jobs.");
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                logger.log(Level.WARNING, "Could not hook into " + name + ". Jobs cannot use its land.", e);
            }
        });
        for (var entry : SELECTIONS.entrySet()) {
            if (selection != null || !available.test(entry.getKey())) continue;
            try {
                selection = (SelectionHook) factory.create(entry.getValue());
                logger.info("Hooked into " + entry.getKey() + " for the areas of jobs.");
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                logger.log(Level.WARNING, "Could not hook into " + entry.getKey() + " for selections.", e);
            }
        }
    }

    /**
     * Decides whose land the area of a new job lies on: refused when any plugin reports land the owner may not change,
     * the owner's plot or region when one covers the area, and otherwise unowned land where the world allows it.
     *
     * @param owner          the player creating the job
     * @param area           the area of the job
     * @param unownedAllowed whether jobs may lie on unowned land in the area's world
     * @return the land and its plot or region, or why the area is refused
     */
    public Decision decide(Player owner, JobArea area, boolean unownedAllowed) {
        List<Claim> claims = new ArrayList<>();
        for (var hook : lands) {
            try {
                claims.add(hook.claim(owner, area));
            } catch (LinkageError | RuntimeException e) {
                logger.log(Level.WARNING, "Could not ask " + hook.plugin() + " about the area of a job.", e);
                claims.add(new Claim.Foreign(null));
            }
        }
        return decide(claims, unownedAllowed);
    }

    /**
     * @param claims         what each land plugin said
     * @param unownedAllowed whether jobs may lie on unowned land in the area's world
     * @return the land and its plot or region, or why the area is refused
     */
    static Decision decide(List<Claim> claims, boolean unownedAllowed) {
        for (var claim : claims) {
            if (claim instanceof Claim.Foreign foreign) return new Decision.Refused(foreign.ref());
        }
        for (var claim : claims) {
            if (claim instanceof Claim.Owned owned) return new Decision.Land(owned.land(), owned.ref());
        }
        return unownedAllowed ? new Decision.Land(JobLand.UNOWNED, null) : new Decision.Unowned();
    }

    /**
     * @param job     the job
     * @param members the members of the working company
     * @param granted the players the hooks gave access to before
     * @return the players the hooks will have given access to after granting these members
     */
    public Set<UUID> planned(Job job, Collection<UUID> members, Set<UUID> granted) {
        Set<UUID> planned = new HashSet<>(granted);
        for (var hook : lands) {
            try {
                planned.addAll(hook.planned(job, members, granted));
            } catch (LinkageError | RuntimeException e) {
                logger.log(Level.WARNING, "Could not plan the access to job " + job.id() + " in " + hook.plugin(), e);
            }
        }
        return planned;
    }

    /**
     * Gives the members access to a running job's area through every land hook.
     *
     * @param job     the job
     * @param members the members of the working company
     * @param granted the players the hooks gave access to before
     * @return the players the hooks gave access to, which they take back later
     */
    public Set<UUID> grant(Job job, Collection<UUID> members, Set<UUID> granted) {
        Set<UUID> given = new HashSet<>();
        for (var hook : lands) {
            try {
                given.addAll(hook.grant(job, members, granted));
            } catch (LinkageError | RuntimeException e) {
                logger.log(Level.WARNING, "Could not give access to job " + job.id() + " in " + hook.plugin(), e);
                given.addAll(granted);
            }
        }
        return given;
    }

    /**
     * Takes the access to a job's area back through every land hook.
     *
     * @param job     the job
     * @param granted the players the hooks gave access to
     * @return true when every hook took its access back; false to ask again later
     */
    public boolean revoke(Job job, Set<UUID> granted) {
        var done = true;
        for (var hook : lands) {
            try {
                done &= hook.revoke(job, granted);
            } catch (LinkageError | RuntimeException e) {
                logger.log(Level.WARNING, "Could not take access to job " + job.id() + " back in " + hook.plugin(), e);
                done = false;
            }
        }
        return done;
    }

    /**
     * @param job a job
     * @return false when a land plugin says the job's land changed hands; a hook that fails counts as unchanged
     */
    public boolean owns(Job job) {
        for (var hook : lands) {
            try {
                if (!hook.owns(job)) return false;
            } catch (LinkageError | RuntimeException e) {
                logger.log(Level.WARNING, "Could not check the land of job " + job.id() + " in " + hook.plugin(), e);
            }
        }
        return true;
    }

    /**
     * @param job   a job
     * @param world name of a block's world
     * @param x     x of the block
     * @param y     y of the block
     * @param z     z of the block
     * @return whether a land plugin's access for the job reaches the block
     */
    public boolean reaches(Job job, String world, int x, int y, int z) {
        for (var hook : lands) {
            try {
                if (hook.reaches(job, world, x, y, z)) return true;
            } catch (LinkageError | RuntimeException e) {
                logger.log(Level.WARNING, "Could not check the land of job " + job.id() + " in " + hook.plugin(), e);
            }
        }
        return false;
    }

    /**
     * @param player a player
     * @return the area the player selected, empty without a selection or without WorldEdit
     */
    public Optional<JobArea> selection(Player player) {
        var current = selection;
        if (current == null) return Optional.empty();
        try {
            return current.selection(player);
        } catch (LinkageError | RuntimeException e) {
            logger.log(Level.WARNING, "Could not read the selection of " + player.getName(), e);
            return Optional.empty();
        }
    }

    /**
     * @param player a player
     * @return the area of the plot the player stands on and owns, empty without one
     */
    public Optional<JobArea> ownArea(Player player) {
        for (var hook : lands) {
            try {
                var area = hook.ownArea(player);
                if (area.isPresent()) return area;
            } catch (LinkageError | RuntimeException e) {
                logger.log(Level.WARNING, "Could not read the land of " + player.getName(), e);
            }
        }
        return Optional.empty();
    }

    /**
     * @return whether a selection plugin is hooked
     */
    public boolean selects() {
        return selection != null;
    }

    /**
     * @return the names of the hooked land plugins
     */
    public List<String> landPlugins() {
        return lands.stream().map(JobLandHook::plugin).toList();
    }

    /**
     * Adds a land hook beside the loaded ones, such as one of a land plugin Companies does not know.
     *
     * @param hook the hook
     */
    public void register(JobLandHook hook) {
        lands.add(hook);
    }

    /**
     * Where the area of a new job may lie.
     */
    public sealed interface Decision {
        /**
         * The area lies on this land.
         *
         * @param land what kind of land
         * @param ref  the plot or region, null for unowned land
         */
        record Land(JobLand land, @Nullable String ref) implements Decision {}

        /**
         * The area touches land the owner may not change.
         *
         * @param ref the plot or region, null when it has no name
         */
        record Refused(@Nullable String ref) implements Decision {}

        /**
         * The area lies on unowned land, which the world does not allow.
         */
        record Unowned() implements Decision {}
    }

    /**
     * Creates a hook from its class name.
     */
    @FunctionalInterface
    public interface Factory {
        /**
         * Creates hooks by loading their class and calling the constructor without arguments.
         *
         * @param loader class loader holding the hook classes
         * @return the factory
         */
        static Factory reflective(ClassLoader loader) {
            return className -> Class.forName(className, true, loader)
                    .getDeclaredConstructor()
                    .newInstance();
        }

        /**
         * @param className binary name of the hook class
         * @return a new hook
         * @throws ReflectiveOperationException when the class cannot be loaded or created
         */
        Object create(String className) throws ReflectiveOperationException;
    }
}
