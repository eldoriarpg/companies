/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.suggest;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.company.JoinMode;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AMembershipData;
import de.eldoria.companies.data.wrapper.company.Application;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.company.Invite;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;
import de.eldoria.companies.util.Times;
import org.bukkit.Bukkit;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Where the suggestions of invites, applications and joining read their data from: per player the companies that
 * invited them, the companies they applied to and, for members who may invite, the invited players and applicants of
 * their company; and the company names by join mode. Both are cached like other database data.
 * <p>
 * The plugin hands the sources in when it enables and takes them back when it disables.
 */
public final class MembershipSources {
    private static volatile Optional<MembershipSources> current = Optional.empty();

    private final SuggestionCache<UUID, PlayerMembership> players;
    private final SuggestionCache<JoinMode, List<String>> companies;

    /**
     * @param companyData    company repository
     * @param membershipData invite and application repository
     * @param executor       executor loading data from the database
     */
    public MembershipSources(ACompanyData companyData, AMembershipData membershipData, Executor executor) {
        this(
                new SuggestionCache<>(
                        SuggestionSources.LIFETIME,
                        executor,
                        player -> PlayerMembership.load(companyData, membershipData, player)),
                new SuggestionCache<>(
                        SuggestionSources.LIFETIME, executor, mode -> companyNames(companyData.getCompanies(), mode)));
    }

    /**
     * @param players   cache of the membership data of a player, by the player's id
     * @param companies cache of the company names, by join mode
     */
    public MembershipSources(
            SuggestionCache<UUID, PlayerMembership> players, SuggestionCache<JoinMode, List<String>> companies) {
        this.players = players;
        this.companies = companies;
    }

    /**
     * @param sources the sources the providers read from
     */
    public static void use(MembershipSources sources) {
        current = Optional.of(sources);
    }

    /**
     * Stops suggesting data of the plugin.
     */
    public static void clear() {
        current = Optional.empty();
    }

    /**
     * @return the sources, empty while the plugin is disabled
     */
    public static Optional<MembershipSources> current() {
        return current;
    }

    /**
     * Drops the cached data of a player, after the player changed it.
     *
     * @param player id of the player
     */
    public static void invalidate(UUID player) {
        current.ifPresent(sources -> sources.players().invalidate(player));
    }

    /**
     * @return the membership data of a player, by the player's id
     */
    public SuggestionCache<UUID, PlayerMembership> players() {
        return players;
    }

    /**
     * @return the names of the companies, by join mode
     */
    public SuggestionCache<JoinMode, List<String>> companies() {
        return companies;
    }

    /**
     * @param companies all companies
     * @param mode      join mode to keep
     * @return names of the companies with this join mode
     */
    public static List<String> companyNames(Collection<SimpleCompany> companies, JoinMode mode) {
        return companies.stream()
                .filter(company -> company.joinMode() == mode)
                .map(SimpleCompany::name)
                .toList();
    }

    /**
     * What a player can answer or take back.
     *
     * @param invitingCompanies companies that invited the player
     * @param appliedCompanies  companies the player applied to
     * @param invitedPlayers    players the player's company invited, empty unless the player may invite
     * @param applicants        players who applied to the player's company, empty unless the player may invite
     */
    public record PlayerMembership(
            List<String> invitingCompanies,
            List<String> appliedCompanies,
            List<String> invitedPlayers,
            List<String> applicants) {
        /**
         * @param invitingCompanies companies that invited the player
         * @param appliedCompanies  companies the player applied to
         * @param invitedPlayers    players the player's company invited
         * @param applicants        players who applied to the player's company
         */
        public PlayerMembership {
            invitingCompanies = List.copyOf(invitingCompanies);
            appliedCompanies = List.copyOf(appliedCompanies);
            invitedPlayers = List.copyOf(invitedPlayers);
            applicants = List.copyOf(applicants);
        }

        private static PlayerMembership load(ACompanyData companyData, AMembershipData membershipData, UUID player) {
            var now = Times.now();
            var inviting = membershipData.retrieveInvitesOf(player, now).join().stream()
                    .map(Invite::companyName)
                    .toList();
            var applied = membershipData.retrieveApplicationsOf(player, now).join().stream()
                    .map(Application::companyName)
                    .toList();
            var recruiting = companyData
                    .retrievePlayerCompanyProfile(Bukkit.getOfflinePlayer(player))
                    .asFuture()
                    .join()
                    .filter(profile -> mayInvite(profile, player));
            var invited = recruiting
                    .map(profile -> names(membershipData.retrieveInvitesBy(profile.id(), now).join().stream()
                            .map(Invite::target)
                            .toList()))
                    .orElse(List.of());
            var applicants = recruiting
                    .map(profile -> names(membershipData.retrieveApplicationsTo(profile.id(), now).join().stream()
                            .map(Application::applicant)
                            .toList()))
                    .orElse(List.of());
            return new PlayerMembership(inviting, applied, invited, applicants);
        }

        private static boolean mayInvite(CompanyProfile profile, UUID player) {
            return profile.members().stream()
                    .anyMatch(member -> member.uuid().equals(player) && member.hasPermission(CompanyPermission.INVITE));
        }

        private static List<String> names(List<UUID> players) {
            return players.stream()
                    .map(uuid -> Bukkit.getOfflinePlayer(uuid).getName())
                    .filter(Objects::nonNull)
                    .toList();
        }
    }
}
