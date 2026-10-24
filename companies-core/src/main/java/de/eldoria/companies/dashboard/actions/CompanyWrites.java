/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.actions;

import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.company.JoinMode;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.services.CompanyService;
import de.eldoria.companies.services.DisbandService;
import de.eldoria.companies.services.MemberService;
import de.eldoria.companies.services.MembershipService;
import de.eldoria.companies.services.ProfileChange;
import de.eldoria.companies.services.ProfileService;
import de.eldoria.companies.services.TreasuryService;
import de.eldoria.companies.util.Permission;
import de.eldoria.companies.web.api.Actions;
import de.eldoria.companies.web.api.action.ActionRequest;
import de.eldoria.companies.web.api.action.ActionResult;
import org.bukkit.OfflinePlayer;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Performs the dashboard's actions on the player's company: taking back invites, kicking, leaving, editing
 * permissions and handing the company to another member; accepting and denying applications and setting the join
 * mode; editing the profile and renaming; paying into and out of the treasury and setting its share; founding a
 * company and dissolving it. Founding needs the permission remembered with the session and is handed in through
 * {@link #create}; dissolving is done by the owner only and takes the company's name in {@code name}, as typed to
 * confirm it.
 * <p>
 * Every action goes through the service the command uses, with the session's player as actor, so the company
 * permissions are checked exactly as in game. A player is named by id in {@code player}. Renaming answers a quote
 * with {@code "quote": true}: the price, and the treasury's balance when the server lets the treasury pay; the rename
 * itself takes {@code payment}, {@code PLAYER} or {@code TREASURY}; the name is stripped, and a blank one refused by the
 * service. Amounts of money are numbers, shares whole percents. Arguments of the wrong shape are refused with
 * {@code dashboard.invalid}. The methods block on the database and the economy and are called off the main thread.
 */
public final class CompanyWrites {
    /**
     * The actions this version performs.
     */
    public static final Set<String> ACTIONS = Set.of(
            Actions.MEMBERS_REVOKE,
            Actions.MEMBERS_KICK,
            Actions.MEMBERS_LEAVE,
            Actions.MEMBERS_PERMISSIONS,
            Actions.APPLICATIONS_ACCEPT,
            Actions.APPLICATIONS_DENY,
            Actions.APPLICATIONS_JOIN_MODE,
            Actions.PROFILE_DESCRIPTION,
            Actions.PROFILE_TAG,
            Actions.PROFILE_COLOR,
            Actions.PROFILE_RENAME,
            Actions.TREASURY_DEPOSIT,
            Actions.TREASURY_WITHDRAW,
            Actions.TREASURY_SHARE,
            Actions.MEMBERS_TRANSFER,
            Actions.COMPANY_CREATE,
            Actions.COMPANY_DISBAND);

    private static final String INVALID = "dashboard.invalid";

    private final Sources sources;
    private final Answers answers;

    /**
     * @param sources what the actions are performed with
     */
    public CompanyWrites(Sources sources) {
        this.sources = sources;
        answers = new Answers(sources.render());
    }

    /**
     * Performs an action of a player.
     *
     * @param request the action, one of {@link #ACTIONS}
     * @param player  the acting player
     * @param args    the arguments
     * @return the result
     */
    ActionResult perform(ActionRequest request, UUID player, JsonNode args) {
        return switch (request.name()) {
            case Actions.MEMBERS_REVOKE ->
                onPlayer(
                        request,
                        args,
                        target -> membership(
                                request, sources.memberships().revokeInvite(offline(player), target), target));
            case Actions.MEMBERS_KICK ->
                onPlayer(
                        request,
                        args,
                        target -> answers.answer(
                                request,
                                sources.members().kick(player, target).join(),
                                kicked -> Map.of("player", kicked.member().uuid())));
            case Actions.MEMBERS_LEAVE -> leave(request, player);
            case Actions.MEMBERS_PERMISSIONS -> permissions(request, player, args);
            case Actions.APPLICATIONS_ACCEPT ->
                onPlayer(
                        request,
                        args,
                        target -> membership(
                                request, sources.memberships().acceptApplication(offline(player), target), target));
            case Actions.APPLICATIONS_DENY ->
                onPlayer(
                        request,
                        args,
                        target -> membership(
                                request, sources.memberships().denyApplication(offline(player), target), target));
            case Actions.APPLICATIONS_JOIN_MODE -> joinMode(request, player, args);
            case Actions.PROFILE_DESCRIPTION ->
                profile(request, player, args, "description", ProfileChange::description);
            case Actions.PROFILE_TAG -> profile(request, player, args, "tag", ProfileChange::tag);
            case Actions.PROFILE_COLOR -> profile(request, player, args, "color", ProfileChange::color);
            case Actions.PROFILE_RENAME -> rename(request, player, args);
            case Actions.TREASURY_DEPOSIT ->
                money(request, player, args, (company, amount) -> sources.treasury()
                        .deposit(company, offline(player), amount));
            case Actions.TREASURY_WITHDRAW ->
                money(request, player, args, (company, amount) -> sources.treasury()
                        .withdraw(company, offline(player), amount));
            case Actions.TREASURY_SHARE -> share(request, player, args);
            case Actions.MEMBERS_TRANSFER ->
                onPlayer(
                        request,
                        args,
                        target -> withCompany(
                                request,
                                player,
                                company -> answers.answer(
                                        request,
                                        sources.members()
                                                .transfer(player, company.id(), target)
                                                .join(),
                                        transferred -> Map.of("player", target))));
            case Actions.COMPANY_DISBAND -> disband(request, player, args);
            default -> answers.refused(request, "dashboard.unsupported");
        };
    }

    /**
     * Founds a company with the player as owner, or with {@code "quote": true} names its price. The player needs the
     * permission to found companies, from the permissions remembered with the session.
     *
     * @param request     the action
     * @param player      the session's player
     * @param permissions the permissions remembered with the session
     * @param args        the arguments: {@code name} and {@code quote}
     * @return the result
     */
    ActionResult create(ActionRequest request, UUID player, Set<String> permissions, JsonNode args) {
        if (!permissions.contains(Permission.Company.CREATE)) {
            return answers.refused(request, "error.permission.createCompany");
        }
        if (!args.path("name").isString()) return answers.refused(request, INVALID);
        var name = args.path("name").asString().strip();
        var price = sources.companies().foundingPrice();
        if (Answers.quote(args)) {
            return answers.answer(
                    request,
                    sources.companies().foundable(player, name).join(),
                    foundable -> Map.of("name", foundable, "price", price));
        }
        return answers.answer(
                request,
                sources.companies().found(player, name).join(),
                company -> Map.of("company", company, "price", price));
    }

    private ActionResult disband(ActionRequest request, UUID player, JsonNode args) {
        return withCompany(request, player, company -> {
            if (!company.member(player).map(CompanyMember::isOwner).orElse(false)) {
                return answers.refused(request, "error.noOwner");
            }
            if (!company.name().equalsIgnoreCase(args.path("name").asString("").strip())) {
                return answers.refused(request, "dashboard.disband.nameMismatch");
            }
            return answers.answer(
                    request,
                    sources.disband().disbandOwned(player, company.id()),
                    disbanded -> Map.of("company", disbanded.id()));
        });
    }

    private ActionResult leave(ActionRequest request, UUID player) {
        return withCompany(
                request,
                player,
                company -> answers.answer(
                        request,
                        sources.members().leave(player, company.id()).join(),
                        left -> Map.of("company", left.id())));
    }

    private ActionResult permissions(ActionRequest request, UUID player, JsonNode args) {
        return onPlayer(request, args, target -> {
            var wanted = wanted(args.path("permissions"));
            if (wanted.isEmpty()) return answers.refused(request, INVALID);
            return answers.answer(
                    request,
                    sources.members()
                            .setPermissions(player, new MemberService.MemberRef(target, null), wanted.get())
                            .join(),
                    edit -> Map.of(
                            "player",
                            target,
                            "permissions",
                            edit.target().permissions().stream().map(Enum::name).toList()));
        });
    }

    private static Optional<Map<CompanyPermission, Boolean>> wanted(JsonNode permissions) {
        if (!permissions.isObject() || permissions.isEmpty()) return Optional.empty();
        Map<CompanyPermission, Boolean> wanted = new EnumMap<>(CompanyPermission.class);
        for (var entry : permissions.properties()) {
            var permission = permission(entry.getKey());
            if (permission.isEmpty() || !entry.getValue().isBoolean()) return Optional.empty();
            wanted.put(permission.get(), entry.getValue().asBoolean());
        }
        return Optional.of(wanted);
    }

    private static Optional<CompanyPermission> permission(String name) {
        try {
            return Optional.of(CompanyPermission.valueOf(name));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private ActionResult joinMode(ActionRequest request, UUID player, JsonNode args) {
        JoinMode mode;
        try {
            mode = JoinMode.valueOf(args.path("mode").asString(""));
        } catch (IllegalArgumentException e) {
            return answers.refused(request, INVALID);
        }
        var outcome = sources.memberships().setJoinMode(offline(player), mode).join();
        return outcome.success()
                ? answers.data(request, Map.of("mode", mode.name()))
                : membershipRefused(request, outcome);
    }

    private ActionResult profile(
            ActionRequest request, UUID player, JsonNode args, String field, Function<String, ProfileChange> change) {
        if (!args.path(field).isString()) return answers.refused(request, INVALID);
        var outcome = sources.profiles()
                .edit(offline(player), change.apply(args.path(field).asString()))
                .join();
        return switch (outcome) {
            case ProfileService.Outcome.Saved saved -> answers.data(request, profileOf(saved.current()));
            case ProfileService.Outcome.Refused refused -> answers.refused(request, refused.failure());
        };
    }

    private static Map<String, Object> profileOf(CompanyProfile company) {
        Map<String, Object> profile = new HashMap<>();
        profile.put("name", company.name());
        var details = company.details();
        if (details.description() != null) profile.put("description", details.description());
        if (details.tag() != null) profile.put("tag", details.tag());
        if (details.color() != null) profile.put("color", details.color());
        return profile;
    }

    private ActionResult rename(ActionRequest request, UUID player, JsonNode args) {
        if (!args.path("name").isString()) return answers.refused(request, INVALID);
        var name = args.path("name").asString().strip();
        if (Answers.quote(args)) {
            return answers.answer(
                    request, sources.companies().renamable(player, name).join(), quote -> {
                        Map<String, Object> value = new HashMap<>();
                        value.put("price", quote.price());
                        quote.treasuryBalance().ifPresent(balance -> value.put("treasury", balance));
                        return value;
                    });
        }
        CompanyService.Payment payment;
        try {
            payment = CompanyService.Payment.valueOf(args.path("payment").asString("PLAYER"));
        } catch (IllegalArgumentException e) {
            return answers.refused(request, INVALID);
        }
        return answers.answer(
                request, sources.companies().rename(player, name, payment).join(), renamed -> Map.of("name", name));
    }

    private ActionResult money(
            ActionRequest request,
            UUID player,
            JsonNode args,
            BiFunction<CompanyProfile, BigDecimal, CompletableFuture<TreasuryService.Result>> action) {
        return withCompany(request, player, company -> {
            var amount = args.path("amount");
            var result = amount.isNumber() && amount.decimalValue().signum() > 0
                    ? action.apply(company, amount.decimalValue()).join()
                    : TreasuryService.Result.of(TreasuryService.Outcome.INVALID_AMOUNT);
            return treasury(request, result, Map.of());
        });
    }

    private ActionResult share(ActionRequest request, UUID player, JsonNode args) {
        var percent = args.path("percent");
        if (!percent.isIntegralNumber() || !percent.canConvertToInt()) return answers.refused(request, INVALID);
        return withCompany(
                request,
                player,
                company -> treasury(
                        request,
                        sources.treasury()
                                .setShare(company, offline(player), percent.asInt())
                                .join(),
                        Map.of("share", percent.asInt())));
    }

    private ActionResult treasury(ActionRequest request, TreasuryService.Result result, Map<String, Object> extra) {
        if (!result.done()) {
            return answers.refused(
                    request,
                    sources.treasury().explain(result, sources.currency().get()).failure());
        }
        Map<String, Object> value = new HashMap<>(extra);
        value.put("balance", result.balance());
        return answers.data(request, value);
    }

    private ActionResult membership(
            ActionRequest request, CompletableFuture<MembershipService.Outcome> action, UUID target) {
        var outcome = action.join();
        return outcome.success()
                ? answers.data(request, Map.of("player", target))
                : membershipRefused(request, outcome);
    }

    private ActionResult membershipRefused(ActionRequest request, MembershipService.Outcome outcome) {
        return answers.refused(request, outcome.key(), sources.renderText().apply(outcome.text()));
    }

    private ActionResult onPlayer(ActionRequest request, JsonNode args, Function<UUID, ActionResult> action) {
        return Answers.player(args, "player").map(action).orElseGet(() -> answers.refused(request, INVALID));
    }

    private ActionResult withCompany(
            ActionRequest request, UUID player, Function<CompanyProfile, ActionResult> action) {
        return sources.companyOf()
                .apply(player)
                .join()
                .map(action)
                .orElseGet(() -> answers.refused(request, "error.noMember"));
    }

    private OfflinePlayer offline(UUID player) {
        return sources.players().apply(player);
    }

    /**
     * What the actions are performed with.
     *
     * @param companyOf   the company a player is a member of
     * @param players     a player by id, online or not
     * @param members     kicks, lets leave and edits permissions
     * @param memberships invites, applications and the join mode
     * @param profiles    edits the profile
     * @param companies   founds and renames
     * @param disband     dissolves companies
     * @param treasury    the treasuries
     * @param currency    the name of the currency
     * @param render      renders a refusal as plain text, with its values
     * @param renderText  renders a message of the membership service as plain text
     */
    public record Sources(
            Function<UUID, CompletableFuture<Optional<CompanyProfile>>> companyOf,
            Function<UUID, OfflinePlayer> players,
            MemberService members,
            MembershipService memberships,
            ProfileService profiles,
            CompanyService companies,
            DisbandService disband,
            TreasuryService treasury,
            Supplier<String> currency,
            Function<CommandFailure, String> render,
            Function<MenuText, String> renderText) {}
}
