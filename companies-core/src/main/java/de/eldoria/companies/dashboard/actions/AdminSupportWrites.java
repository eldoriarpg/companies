/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.actions;

import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.dashboard.views.Payloads;
import de.eldoria.companies.dashboard.views.Views;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AInboxData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.repository.ATreasuryData;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.dashboard.StoredSession;
import de.eldoria.companies.data.wrapper.inbox.InboxEntry;
import de.eldoria.companies.data.wrapper.order.ReturnKind;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.orders.definition.ItemDefinitions;
import de.eldoria.companies.services.AdminCompanyService;
import de.eldoria.companies.services.AdminGrantService;
import de.eldoria.companies.services.DisbandService;
import de.eldoria.companies.services.TreasuryService;
import de.eldoria.companies.util.AdminLog;
import de.eldoria.companies.util.Permission;
import de.eldoria.companies.web.api.Actions;
import de.eldoria.companies.web.api.action.ActionRequest;
import de.eldoria.companies.web.api.action.ActionResult;
import de.eldoria.companies.web.api.session.PlayerRef;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * The dashboard's admin tools for players and companies, as `/companyadmin player`, `grant`, `treasury`, `rename`,
 * `transferowner` and `delete` offer them: look a player up, grant a player money or items, show a company's treasury
 * and give or take money, rename a company free of charge, make another member its owner, and dissolve it. Each tool
 * needs the permission of its command, checked against the permissions remembered with the session as every
 * dashboard action is, and every action is written to the admin log as `<name>@dashboard`.
 * <p>
 * A player is named by `player`, a company by `company`, both by name. Granting items takes a material key in
 * `material` and `amount` (1 to 100000), granting money `amount` (0.01 to a trillion), the treasury `amount` with
 * at most two decimals, renaming `name`. The methods block and are called off the main thread.
 */
public final class AdminSupportWrites {
    /**
     * The actions performed here.
     */
    public static final Set<String> ACTIONS = Set.of(
            Actions.ADMIN_PLAYERS_LOOKUP,
            Actions.ADMIN_PLAYERS_GRANT_ITEMS,
            Actions.ADMIN_PLAYERS_GRANT_MONEY,
            Actions.ADMIN_COMPANIES_TREASURY,
            Actions.ADMIN_COMPANIES_TREASURY_GIVE,
            Actions.ADMIN_COMPANIES_TREASURY_TAKE,
            Actions.ADMIN_COMPANIES_RENAME,
            Actions.ADMIN_COMPANIES_TRANSFER_OWNER,
            Actions.ADMIN_COMPANIES_DELETE);

    private static final Map<String, String> PERMISSIONS = Map.of(
            Actions.ADMIN_PLAYERS_LOOKUP, Permission.Admin.PLAYER,
            Actions.ADMIN_PLAYERS_GRANT_ITEMS, Permission.Admin.GRANT,
            Actions.ADMIN_PLAYERS_GRANT_MONEY, Permission.Admin.GRANT,
            Actions.ADMIN_COMPANIES_TREASURY, Permission.Admin.TREASURY,
            Actions.ADMIN_COMPANIES_TREASURY_GIVE, Permission.Admin.TREASURY,
            Actions.ADMIN_COMPANIES_TREASURY_TAKE, Permission.Admin.TREASURY,
            Actions.ADMIN_COMPANIES_RENAME, Permission.Admin.RENAME,
            Actions.ADMIN_COMPANIES_TRANSFER_OWNER, Permission.Admin.TRANSFER_OWNER,
            Actions.ADMIN_COMPANIES_DELETE, Permission.Admin.DELETE);

    private static final int LATEST = 5;
    private static final int LOG_ENTRIES = 10;
    private static final int MAX_ITEMS = 100_000;
    private static final double MAX_MONEY = 1.0E12;
    private static final String INVALID = "dashboard.invalid";
    private static final String UNKNOWN_PLAYER = "error.unknownPlayer";
    private static final String UNKNOWN_COMPANY = "error.unknownCompany";

    private final Sources sources;
    private final Answers answers;

    /**
     * @param sources what players and companies are looked up and changed with
     */
    public AdminSupportWrites(Sources sources) {
        this.sources = sources;
        answers = new Answers(sources.render());
    }

    /**
     * Performs an admin action on a player or a company.
     *
     * @param request the action, one of {@link #ACTIONS}
     * @param session the admin's session
     * @param args    the arguments
     * @return the result
     */
    ActionResult perform(ActionRequest request, StoredSession session, JsonNode args) {
        if (!session.permissions().contains(PERMISSIONS.get(request.name()))) {
            return answers.refused(request, "companyadmin.error.permission");
        }
        var admin = session.playerName() + "@dashboard";
        if (request.name().startsWith("admin.players.")) {
            var player = sources.player().apply(args.path("player").asString("").strip());
            if (player.isEmpty()) return answers.refused(request, UNKNOWN_PLAYER);
            return switch (request.name()) {
                case Actions.ADMIN_PLAYERS_LOOKUP -> lookup(request, admin, player.get());
                case Actions.ADMIN_PLAYERS_GRANT_ITEMS -> grantItems(request, admin, player.get(), args);
                case Actions.ADMIN_PLAYERS_GRANT_MONEY -> grantMoney(request, admin, player.get(), args);
                default -> answers.refused(request, "dashboard.unsupported");
            };
        }
        var company =
                sources.companies().profile(args.path("company").asString("").strip());
        if (company.isEmpty()) return answers.refused(request, UNKNOWN_COMPANY);
        return switch (request.name()) {
            case Actions.ADMIN_COMPANIES_TREASURY -> treasury(request, admin, company.get());
            case Actions.ADMIN_COMPANIES_TREASURY_GIVE ->
                adjust(request, admin, session.player(), company.get(), args, true);
            case Actions.ADMIN_COMPANIES_TREASURY_TAKE ->
                adjust(request, admin, session.player(), company.get(), args, false);
            case Actions.ADMIN_COMPANIES_RENAME -> rename(request, admin, session.player(), company.get(), args);
            case Actions.ADMIN_COMPANIES_TRANSFER_OWNER -> transfer(request, admin, company.get(), args);
            case Actions.ADMIN_COMPANIES_DELETE -> delete(request, admin, session.player(), company.get());
            default -> answers.refused(request, "dashboard.unsupported");
        };
    }

    private ActionResult lookup(ActionRequest request, String admin, PlayerRef player) {
        log(admin, "player")
                .with("player", player.name())
                .with("uuid", player.uuid())
                .write();
        var offline = sources.offline().apply(player.uuid());
        var profile =
                sources.companyData().retrievePlayerCompanyProfile(offline).join();
        var member = profile == null
                ? Optional.<CompanyMember>empty()
                : profile.flatMap(company -> company.member(player.uuid()));
        var orders = sources.orders()
                .retrieveOrdersByPlayer(offline, OrderState.UNCLAIMED, OrderState.RECEIVED)
                .join();
        var workerItems = sources.orders()
                .retrieveItemReturnCount(player.uuid(), ReturnKind.WORKER)
                .join();
        var ownerItems = sources.orders()
                .retrieveItemReturnCount(player.uuid(), ReturnKind.OWNER)
                .join();
        var latest = sources.inbox().retrievePage(player.uuid(), 1, LATEST).join();
        return answers.data(
                request,
                new Payloads.AdminPlayer(
                        player,
                        member.isPresent() && profile != null
                                ? profile.map(CompanyProfile::name).orElse(null)
                                : null,
                        member.map(CompanyMember::isOwner).orElse(false),
                        member.map(found -> found.permissions().stream()
                                        .map(Enum::name)
                                        .toList())
                                .orElse(List.of()),
                        (orders == null ? List.<SimpleOrder>of() : orders)
                                .stream()
                                        .map(order -> new Payloads.AdminOrderRow(
                                                order.id(),
                                                order.name(),
                                                order.state().name(),
                                                player.name(),
                                                null,
                                                sources.views().instant(order.created())))
                                        .toList(),
                        workerItems == null ? 0 : workerItems,
                        ownerItems == null ? 0 : ownerItems,
                        sources.inbox().retrieveUnreadCount(player.uuid(), 0L).join(),
                        latest.stream().map(this::inboxEntry).toList()));
    }

    private Payloads.InboxEntry inboxEntry(InboxEntry entry) {
        return new Payloads.InboxEntry(
                entry.id(),
                entry.type().name(),
                entry.type().category().name(),
                sources.views().instant(entry.created()),
                entry.read(),
                sources.inboxText().apply(entry));
    }

    private ActionResult grantItems(ActionRequest request, String admin, PlayerRef player, JsonNode args) {
        var amount = args.path("amount");
        if (!amount.isIntegralNumber()
                || !amount.canConvertToInt()
                || amount.asInt() < 1
                || amount.asInt() > MAX_ITEMS) {
            return answers.refused(request, INVALID);
        }
        ItemStack stack;
        try {
            stack = new ItemStack(ItemDefinitions.material(args.path("material").asString("")));
        } catch (CommandFailure failure) {
            return answers.refused(request, failure);
        }
        if (!Boolean.TRUE.equals(
                sources.grants().items(player.uuid(), stack, amount.asInt()).join())) {
            return answers.refused(request, "error.saveFailed");
        }
        log(admin, "grant items")
                .with("player", player.name())
                .with("uuid", player.uuid())
                .with("item", stack.getType().getKey())
                .with("amount", amount.asInt())
                .write();
        return answers.data(request, Map.of("player", player.name(), "amount", amount.asInt()));
    }

    private ActionResult grantMoney(ActionRequest request, String admin, PlayerRef player, JsonNode args) {
        var amount = args.path("amount");
        if (!amount.isNumber()
                || !Double.isFinite(amount.asDouble())
                || amount.asDouble() < 0.01
                || amount.asDouble() > MAX_MONEY) {
            return answers.refused(request, INVALID);
        }
        var offline = sources.offline().apply(player.uuid());
        var result = CompletableFuture.supplyAsync(
                        () -> sources.grants().money(offline, amount.asDouble()), sources.mainThread())
                .join();
        return switch (result) {
            case de.eldoria.companies.services.ActionResult.Refused<String> refused ->
                answers.refused(request, refused.failure());
            case de.eldoria.companies.services.ActionResult.Done<String> done -> {
                log(admin, "grant money")
                        .with("player", player.name())
                        .with("uuid", player.uuid())
                        .with("amount", amount.asDouble())
                        .write();
                yield answers.data(request, Map.of("player", player.name(), "amount", done.value()));
            }
        };
    }

    private ActionResult treasury(ActionRequest request, String admin, CompanyProfile company) {
        var treasury = sources.treasury().treasury(company.id()).join();
        var entries = sources.treasuryData().retrieveLog(company.id(), 1, LOG_ENTRIES).join().stream()
                .map(entry -> new Payloads.TreasuryEntry(
                        entry.id(),
                        sources.views().instant(entry.created()),
                        entry.kind().name(),
                        entry.amount() / 100.0,
                        entry.balance() / 100.0,
                        entry.actorId()
                                .map(actor ->
                                        new PlayerRef(actor, sources.names().apply(actor)))
                                .orElse(null),
                        entry.orderId().orElse(null),
                        entry.note()))
                .toList();
        log(admin, "treasury")
                .with("company", company.name())
                .with("company_id", company.id())
                .write();
        return answers.data(
                request,
                new Payloads.AdminTreasury(company.name(), treasury.cents() / 100.0, treasury.payoutShare(), entries));
    }

    private ActionResult adjust(
            ActionRequest request, String admin, UUID actor, CompanyProfile company, JsonNode args, boolean give) {
        var amount = args.path("amount");
        if (!amount.isNumber() || !Double.isFinite(amount.asDouble()) || amount.asDouble() > MAX_MONEY) {
            return answers.refused(request, INVALID);
        }
        var value = BigDecimal.valueOf(amount.asDouble());
        var result = sources.treasury().adminAdjust(company, actor, give, value).join();
        return switch (result.outcome()) {
            case DONE -> {
                log(admin, give ? "treasury give" : "treasury take")
                        .with("company", company.name())
                        .with("company_id", company.id())
                        .with("amount", value.toPlainString())
                        .with("balance", result.balance().toPlainString())
                        .write();
                yield answers.data(
                        request,
                        Map.of(
                                "company",
                                company.name(),
                                "balance",
                                result.balance().doubleValue()));
            }
            case TREASURY_FUNDS -> answers.refused(request, "company.bank.error.insufficient");
            case INVALID_AMOUNT -> answers.refused(request, "error.invalidNumber");
            default -> answers.refused(request, "error.saveFailed");
        };
    }

    private ActionResult rename(
            ActionRequest request, String admin, UUID actor, CompanyProfile company, JsonNode args) {
        var name = args.path("name").asString("").strip();
        if (name.isEmpty()) return answers.refused(request, INVALID);
        return switch (sources.companies().rename(company, name, actor)) {
            case de.eldoria.companies.services.ActionResult.Refused<String> refused ->
                answers.refused(request, refused.failure());
            case de.eldoria.companies.services.ActionResult.Done<String> done -> {
                log(admin, "rename")
                        .with("company_id", company.id())
                        .with("from", done.value())
                        .with("to", name)
                        .write();
                yield answers.data(request, Map.of("company", name));
            }
        };
    }

    private ActionResult transfer(ActionRequest request, String admin, CompanyProfile company, JsonNode args) {
        var player = sources.player().apply(args.path("player").asString("").strip());
        if (player.isEmpty()) return answers.refused(request, UNKNOWN_PLAYER);
        return switch (sources.companies().transferOwner(company, player.get().uuid())) {
            case de.eldoria.companies.services.ActionResult.Refused<AdminCompanyService.Transfer> refused ->
                answers.refused(request, refused.failure());
            case de.eldoria.companies.services.ActionResult.Done<AdminCompanyService.Transfer> done -> {
                log(admin, "transferowner")
                        .with("company", company.name())
                        .with("company_id", company.id())
                        .with("from", sources.names().apply(done.value().from().uuid()))
                        .with("to", player.get().name())
                        .write();
                yield answers.data(
                        request,
                        Map.of("company", company.name(), "owner", player.get().name()));
            }
        };
    }

    private ActionResult delete(ActionRequest request, String admin, UUID actor, CompanyProfile company) {
        if (!sources.disband().disband(company, actor)) return answers.refused(request, "company.disband.failed");
        log(admin, "delete")
                .with("company", company.name())
                .with("company_id", company.id())
                .write();
        return answers.data(request, Map.of("company", company.name()));
    }

    private AdminLog.Entry log(String admin, String action) {
        return sources.log().entry(admin, action);
    }

    /**
     * What players and companies are looked up and changed with.
     *
     * @param player       a player known to the server by name, with id and exact name
     * @param offline      a player by id
     * @param names        the name of a player by id
     * @param companies    looks companies up and renames them and changes their owner
     * @param companyData  the company repository
     * @param orders       the order repository
     * @param inbox        the inboxes
     * @param inboxText    renders an inbox entry as plain text
     * @param grants       grants money and items
     * @param treasury     the treasuries
     * @param treasuryData the treasury log
     * @param disband      dissolves companies
     * @param mainThread   runs tasks on the main thread
     * @param views        turns stored times into instants
     * @param log          the admin log
     * @param render       renders a refusal as plain text
     */
    public record Sources(
            Function<String, Optional<PlayerRef>> player,
            Function<UUID, OfflinePlayer> offline,
            Function<UUID, String> names,
            AdminCompanyService companies,
            ACompanyData companyData,
            AOrderData orders,
            AInboxData inbox,
            Function<InboxEntry, String> inboxText,
            AdminGrantService grants,
            TreasuryService treasury,
            ATreasuryData treasuryData,
            DisbandService disband,
            Executor mainThread,
            Views views,
            AdminLog log,
            Function<CommandFailure, String> render) {}
}
