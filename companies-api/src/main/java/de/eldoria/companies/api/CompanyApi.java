/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.api;

import de.eldoria.companies.api.write.FoundRequest;
import de.eldoria.companies.api.write.FoundResult;
import de.eldoria.companies.api.write.HomeRequest;
import de.eldoria.companies.api.write.HomeResult;
import de.eldoria.companies.components.area.ICompanyArea;
import de.eldoria.companies.components.area.ICompanyHome;
import de.eldoria.companies.components.company.ICompanyProfile;
import de.eldoria.companies.components.company.ICompanyStats;
import de.eldoria.companies.components.company.IMemberStats;
import de.eldoria.companies.components.company.ITreasury;
import de.eldoria.companies.components.join.JoinResult;
import de.eldoria.companies.components.ranking.TimeRange;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Reads companies and adds members to them.
 * <p>
 * Every future completes off the main thread; see {@link CompaniesApi} for how to get back onto it.
 */
public interface CompanyApi {
    /**
     * The company with the given id.
     *
     * @param id id of the company
     * @return future of the company, empty when no company has this id
     */
    CompletableFuture<Optional<? extends ICompanyProfile>> byId(int id);

    /**
     * The company with the given name.
     *
     * @param name name of the company, in any case
     * @return future of the company, empty when no company has this name
     */
    CompletableFuture<Optional<? extends ICompanyProfile>> byName(String name);

    /**
     * The company with the given tag.
     *
     * @param tag tag of the company, in any case
     * @return future of the company, empty when no company has this tag
     */
    CompletableFuture<Optional<? extends ICompanyProfile>> byTag(String tag);

    /**
     * The company a player is a member of.
     *
     * @param player id of the player
     * @return future of the company, empty when the player is not a member of any company
     */
    CompletableFuture<Optional<? extends ICompanyProfile>> of(UUID player);

    /**
     * The all time statistics of a company.
     *
     * @param company id of the company
     * @return future of the statistics, empty when no company has this id
     */
    CompletableFuture<Optional<? extends ICompanyStats>> stats(int company);

    /**
     * What each worker contributed to a company in a time range.
     * <p>
     * Workers without a completed order in the range are left out. Former members who contributed are included.
     *
     * @param company id of the company
     * @param range   time range of the values
     * @return future of the statistics per worker, empty when the company is unknown or had no activity in the range
     */
    CompletableFuture<List<? extends IMemberStats>> memberStats(int company, TimeRange range);

    /**
     * The treasury of a company: its balance and the share of each payout it keeps. A company that never used its
     * treasury has a balance and share of 0.
     *
     * @param company id of the company
     * @return future of the treasury, empty when no company has this id
     */
    CompletableFuture<Optional<? extends ITreasury>> treasury(int company);

    /**
     * The areas a company uses: plots and regions it linked, bought or rents.
     *
     * @param company id of the company
     * @return future of the areas, oldest first, empty when the company has none or no company has this id
     */
    CompletableFuture<List<? extends ICompanyArea>> areas(int company);

    /**
     * The home of a company.
     *
     * @param company id of the company
     * @return future of the home, empty when the company has set none or no company has this id
     */
    CompletableFuture<Optional<? extends ICompanyHome>> home(int company);

    /**
     * Adds a player to a company, regardless of the company's join mode.
     * <p>
     * The member limit of the company's level applies. Open invites and applications of the player are removed. On
     * success a {@link de.eldoria.companies.events.company.CompanyJoinEvent} is fired, so levels and announcements
     * behave as for a player who joined on their own. The player gets no permissions in the company.
     * <p>
     * This is a write operation: before Companies is ready the future completes exceptionally with an
     * {@link ApiNotReadyException}.
     *
     * @param company id of the company
     * @param player  id of the player
     * @return future of the outcome
     */
    CompletableFuture<JoinResult> addMember(int company, UUID player);

    /**
     * Founds a company with the same checks as {@code /company create}: a valid and free name, an owner without a
     * company and, unless {@link FoundRequest#charge()} is false, the founding price. While the owner is online on
     * this server a {@link de.eldoria.companies.events.company.PreCompanyCreateEvent} is fired and may cancel it; a
     * founded company fires {@link de.eldoria.companies.events.company.CompanyCreateEvent}.
     * <p>
     * This is a write operation: before Companies is ready the future completes exceptionally with an
     * {@link ApiNotReadyException}.
     *
     * @param request the company to found
     * @return future of the new company, or why it was not founded
     */
    CompletableFuture<FoundResult> found(FoundRequest request);

    /**
     * Sets the home of a company, replacing its former one. A company with areas sets it inside one of them while
     * {@code homeInsideArea} is on. Fires {@link de.eldoria.companies.events.company.CompanyAreasChangeEvent}.
     * <p>
     * This is a write operation: before Companies is ready the future completes exceptionally with an
     * {@link ApiNotReadyException}.
     *
     * @param request the company and where its home goes
     * @return future of the stored home, or why it was not set
     */
    CompletableFuture<HomeResult> setHome(HomeRequest request);
}
