/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.areas;

import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.configuration.elements.areas.AreaSettings;
import de.eldoria.companies.data.wrapper.area.CompanyHome;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.events.company.CompanyAreasChangeEvent;
import de.eldoria.companies.services.ActionResult;
import de.eldoria.companies.util.AsyncEvents;
import de.eldoria.companies.util.Times;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Sets the homes of companies, for {@code /company sethome} and the API: a company with areas sets it inside one of
 * them while {@code homeInsideArea} is on.
 */
public final class CompanyHomes {
    /**
     * The message key of a member without {@code manage_areas}.
     */
    public static final String NOT_PERMITTED = "company.home.error.manage";
    /**
     * The message key of a home outside the company's areas.
     */
    public static final String OUTSIDE = "company.home.error.outside";
    /**
     * The message key of a home that could not be saved.
     */
    public static final String FAILED = "company.home.error.failed";

    private final AreaServices areas;
    private final Supplier<AreaSettings> settings;
    private final Plugin plugin;

    /**
     * @param areas    the areas and homes of companies
     * @param settings the current settings of areas
     * @param plugin   fires the change event
     */
    public CompanyHomes(AreaServices areas, Supplier<AreaSettings> settings, Plugin plugin) {
        this.areas = areas;
        this.settings = settings;
        this.plugin = plugin;
    }

    /**
     * Reads where a home would be. Call it on the main thread, since it asks the area plugins.
     *
     * @param at the spot
     * @param by who sets the home
     * @return the home at the spot and the areas there
     */
    public Spot spot(Location at, UUID by) {
        Set<String> here = new HashSet<>();
        for (var area : areas.providers().areasAt(at)) here.add(area.toString());
        var home = new CompanyHome(
                0,
                areas.node(),
                areas.proxyServer().get(),
                at.getWorld().getName(),
                at.getX(),
                at.getY(),
                at.getZ(),
                at.getYaw(),
                at.getPitch(),
                by,
                Times.now());
        return new Spot(home, Set.copyOf(here));
    }

    /**
     * Sets the home of a company. Blocks; call it off the main thread.
     *
     * @param company     the company
     * @param spot        where the home goes, from {@link #spot(Location, UUID)}
     * @param managerOnly whether the setter must be a member with {@code manage_areas}
     * @return the stored home, or why it was not set
     */
    public ActionResult<CompanyHome> set(CompanyProfile company, Spot spot, boolean managerOnly) {
        if (managerOnly) {
            var member = company.member(spot.home().setBy());
            if (member.isEmpty() || !member.get().hasPermission(CompanyPermission.MANAGE_AREAS)) {
                return ActionResult.refused(CommandFailure.of(NOT_PERMITTED));
            }
        }
        var owned = areas.areas().ofCompany(company.id()).stream()
                .filter(area -> areas.providers().available(area.provider()))
                .toList();
        var inside = owned.stream().anyMatch(area -> spot.here().contains(area.provider() + ":" + area.ref()));
        if (!AreaHome.allowed(settings.get(), !owned.isEmpty(), inside)) {
            return ActionResult.refused(CommandFailure.of(OUTSIDE));
        }
        var home = spot.home();
        var stored = new CompanyHome(
                company.id(),
                home.node(),
                home.server(),
                home.world(),
                home.x(),
                home.y(),
                home.z(),
                home.yaw(),
                home.pitch(),
                home.setBy(),
                home.setAt());
        if (!areas.homes().set(stored)) return ActionResult.refused(CommandFailure.of(FAILED));
        AsyncEvents.fire(plugin, new CompanyAreasChangeEvent(company, CompanyAreasChangeEvent.Change.HOME));
        return ActionResult.done(stored);
    }

    /**
     * A spot for a home.
     *
     * @param home the home there, of no company yet
     * @param here the areas at the spot, as {@code provider:ref}
     */
    public record Spot(CompanyHome home, Set<String> here) {}
}
