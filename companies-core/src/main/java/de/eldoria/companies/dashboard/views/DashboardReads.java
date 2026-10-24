/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.views;

import de.eldoria.companies.commands.company.order.search.SearchQuery;
import de.eldoria.companies.commands.company.order.search.SearchResults;
import de.eldoria.companies.commands.company.order.search.SortingType;
import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.company.ICompanyProfile;
import de.eldoria.companies.components.order.IFullOrder;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.components.ranking.TimeRange;
import de.eldoria.companies.components.ranking.TopOrder;
import de.eldoria.companies.configuration.elements.ordersettings.MarketSettings;
import de.eldoria.companies.dashboard.market.MarketHistory;
import de.eldoria.companies.data.repository.AInboxData;
import de.eldoria.companies.data.repository.AMembershipData;
import de.eldoria.companies.data.repository.ATreasuryData;
import de.eldoria.companies.data.wrapper.dashboard.MarketRow;
import de.eldoria.companies.data.wrapper.dashboard.StoredSession;
import de.eldoria.companies.data.wrapper.inbox.InboxEntry;
import de.eldoria.companies.services.notifications.NotificationCategory;
import de.eldoria.companies.web.api.Actions;
import de.eldoria.companies.web.api.Features;
import de.eldoria.companies.web.api.Protocol;
import de.eldoria.companies.web.api.action.ActionRequest;
import de.eldoria.companies.web.api.action.ActionResult;
import de.eldoria.companies.web.api.session.PlayerRef;
import org.bukkit.Material;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Answers the reading actions of the dashboard: search results, order details, own orders, inbox, rankings, market
 * statistics, applications with the company's open invites and the treasury log, a page at a time. Every answer is
 * checked as the command would check it and keeps orders anonymous between owner and company. Reading the inbox does
 * not mark it read.
 * <p>
 * Pages are asked for with {@code page} (from 1) and {@code pageSize} (at most {@value #MAX_PAGE_SIZE}) and answered
 * with {@code page}, {@code pages} and {@code total}. Actions that change something are performed by
 * {@code DashboardWrites}; one this version does not perform yet is refused here with {@code dashboard.readOnly}, one
 * of a switched off feature with {@code dashboard.featureDisabled}, an unknown or unsupported one with
 * {@code dashboard.unsupported}. The methods block on the database and are called off the main thread.
 */
public final class DashboardReads {
    /**
     * The read actions this version answers.
     */
    public static final Set<String> ACTIONS = Set.of(
            Actions.ORDERS_SEARCH,
            Actions.ORDERS_DETAILS,
            Actions.ORDERS_OWN_LIST,
            Actions.INBOX_PAGE,
            Actions.RANKINGS_PAGE,
            Actions.RANKINGS_OWN,
            Actions.MARKET_ITEM,
            Actions.MARKET_SEARCH,
            Actions.MARKET_TOP,
            Actions.MARKET_MOVERS,
            Actions.APPLICATIONS_LIST,
            Actions.TREASURY_LOG);

    /**
     * The features whose pages this version can show.
     */
    public static final List<String> FEATURES = List.of(
            Features.ORDERS_BROWSE,
            Features.ORDERS_ACCEPT,
            Features.ORDERS_OWN,
            Features.COMPANY_MEMBERS,
            Features.COMPANY_APPLICATIONS,
            Features.COMPANY_PROFILE,
            Features.COMPANY_TREASURY,
            Features.INBOX,
            Features.RANKINGS,
            Features.MARKET);

    /**
     * The most entries of one page.
     */
    public static final int MAX_PAGE_SIZE = 50;

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_LISTED = 1000;
    private static final int DEFAULT_HISTORY_DAYS = 30;

    private final Views views;
    private final Sources sources;
    private final JsonMapper mapper = Protocol.mapper();

    /**
     * @param views   builds the views
     * @param sources where the pages are read from
     */
    public DashboardReads(Views views, Sources sources) {
        this.views = views;
        this.sources = sources;
    }

    /**
     * Answers an action of a session.
     *
     * @param request the action
     * @param session the session it came from, checked by the caller
     * @param enabled whether a feature is switched on
     * @return the result
     */
    public ActionResult answer(ActionRequest request, StoredSession session, Predicate<String> enabled) {
        var feature = Actions.feature(request.name());
        if (feature == null || !Actions.ALL.contains(request.name())) return refused(request, "dashboard.unsupported");
        if (!enabled.test(feature)) return refused(request, "dashboard.featureDisabled");
        if (!ACTIONS.contains(request.name())) {
            return refused(request, isRead(request.name()) ? "dashboard.unsupported" : "dashboard.readOnly");
        }
        var args = request.args() == null ? mapper.createObjectNode() : request.args();
        var player = session.player();
        return switch (request.name()) {
            case Actions.ORDERS_SEARCH -> data(request, search(player, args));
            case Actions.ORDERS_DETAILS ->
                details(request, player, args.path("order").asInt(0));
            case Actions.ORDERS_OWN_LIST -> data(request, ownOrders(player, args));
            case Actions.INBOX_PAGE -> data(request, inbox(player, args));
            case Actions.RANKINGS_PAGE -> rankings(request, player, args);
            case Actions.RANKINGS_OWN -> ownRank(request, player, args);
            case Actions.MARKET_TOP -> data(request, market(args, Optional.empty()));
            case Actions.MARKET_MOVERS -> data(request, movers(args));
            case Actions.MARKET_SEARCH ->
                data(request, market(args, Optional.of(args.path("query").asString(""))));
            case Actions.MARKET_ITEM -> marketItem(request, args);
            case Actions.APPLICATIONS_LIST -> applications(request, player);
            case Actions.TREASURY_LOG -> treasuryLog(request, player, args);
            default -> refused(request, "dashboard.unsupported");
        };
    }

    private static boolean isRead(String action) {
        return action.equals(Actions.RANKINGS_HISTORY);
    }

    private Payloads.SearchPage search(UUID player, JsonNode args) {
        var query = new SearchQuery();
        var name = args.path("name").asString("");
        if (!name.isBlank()) query.name(name);
        List<String> materials = new ArrayList<>();
        for (var material : args.path("materials")) {
            var value = material.asString("").toLowerCase(Locale.ROOT).replace("minecraft:", "");
            if (!value.isBlank()) materials.add(value);
        }
        query.materials(materials);
        query.anyMaterial(args.path("anyMaterial").asBoolean(true));
        query.exactMatch(args.path("exactMatch").asBoolean(false));
        query.minPrice(Math.max(0, args.path("minPrice").asDouble(0)));
        query.maxPrice(args.path("maxPrice").asDouble(Double.MAX_VALUE));
        query.minOrderSize(Math.max(0, args.path("minOrderSize").asInt(0)));
        query.maxOrderSize(args.path("maxOrderSize").asInt(Integer.MAX_VALUE));
        sorting(args.path("sort").asString("")).ifPresent(query::sortingType);
        query.asc(args.path("asc").asBoolean(true));
        var results = sources.search().apply(player, query).join();
        var orders = new ArrayList<>(results.orders());
        query.sort(orders);
        var page = page(args);
        var size = size(args);
        var shown = Payloads.slice(orders, page, size).stream()
                .map(order -> views.summary(order, false, false))
                .toList();
        return new Payloads.SearchPage(
                page,
                Payloads.pages(orders.size(), size),
                orders.size(),
                results.taxPercent(),
                results.canAccept(),
                results.limitReached(),
                shown);
    }

    private static Optional<SortingType> sorting(String value) {
        for (var type : SortingType.values()) {
            if (type.name().equalsIgnoreCase(value)) return Optional.of(type);
        }
        return Optional.empty();
    }

    /**
     * The details of an order as the player may see them: the owner sees the items waiting to be received, the
     * company working on it when it was claimed, and anyone an unclaimed order. Every other order is unknown to the
     * player. No answer names the owner, the company or a worker.
     */
    private ActionResult details(ActionRequest request, UUID player, int id) {
        var order = views.order(id);
        if (order.isEmpty()) return refused(request, "dashboard.orderUnknown");
        var found = order.get();
        var owner = found.owner().equals(player);
        var company = views.companyOf(player).map(ICompanyProfile::id).orElse(0);
        var working = company > 0 && found.company() == company;
        if (owner || working || found.state() == OrderState.UNCLAIMED) {
            return data(request, views.summary(found, owner, working));
        }
        return refused(request, "dashboard.orderUnknown");
    }

    private Payloads.OrderPage ownOrders(UUID player, JsonNode args) {
        var page = page(args);
        var size = size(args);
        var orders = views.ordersOf(player, OrderState.UNCLAIMED, OrderState.RECEIVED).stream()
                .sorted(Comparator.comparing(IFullOrder::created).reversed())
                .toList();
        var shown = Payloads.slice(orders, page, size).stream()
                .map(order -> views.summary(order, true, false))
                .toList();
        return new Payloads.OrderPage(page, Payloads.pages(orders.size(), size), orders.size(), shown);
    }

    private Payloads.InboxPage inbox(UUID player, JsonNode args) {
        var page = page(args);
        var size = size(args);
        var query = args.path("query").asString("").toLowerCase(Locale.ROOT).strip();
        var entries = sources.inbox().retrievePage(player, 1, MAX_LISTED).join().stream()
                .map(this::inboxEntry)
                .filter(entry ->
                        query.isEmpty() || entry.text().toLowerCase(Locale.ROOT).contains(query))
                .toList();
        long mask = sources.inbox().retrieveMuted(player).join();
        var categories = List.of(NotificationCategory.values());
        return new Payloads.InboxPage(
                page,
                Payloads.pages(entries.size(), size),
                entries.size(),
                Payloads.slice(entries, page, size),
                categories.stream()
                        .filter(category -> (mask & category.bit()) != 0)
                        .map(Enum::name)
                        .toList(),
                categories.stream()
                        .filter(NotificationCategory::mutable)
                        .map(Enum::name)
                        .toList());
    }

    private Payloads.InboxEntry inboxEntry(InboxEntry entry) {
        return new Payloads.InboxEntry(
                entry.id(),
                entry.type().name(),
                entry.type().category().name(),
                views.instant(entry.created()),
                entry.read(),
                sources.inboxText().apply(entry));
    }

    private ActionResult rankings(ActionRequest request, UUID player, JsonNode args) {
        var ranking = ranking(args);
        var range = range(args);
        if (ranking.isEmpty() || range.isEmpty()) return refused(request, "dashboard.invalid");
        var company = views.companyOf(player).map(ICompanyProfile::id);
        return data(request, views.ranking(ranking.get(), range.get(), page(args), company));
    }

    private ActionResult ownRank(ActionRequest request, UUID player, JsonNode args) {
        var ranking = ranking(args);
        var range = range(args);
        if (ranking.isEmpty() || range.isEmpty()) return refused(request, "dashboard.invalid");
        var company = views.companyOf(player).map(ICompanyProfile::id);
        var entry = company.flatMap(id -> views.rank(ranking.get(), range.get(), id));
        return data(
                request,
                new Payloads.OwnRank(
                        Views.key(ranking.get()), range.get().id(), company.orElse(null), entry.orElse(null)));
    }

    private static Optional<TopOrder> ranking(JsonNode args) {
        var value = args.path("ranking").asString(Views.key(Views.DEFAULT_RANKING));
        for (var order : TopOrder.values()) {
            if (Views.key(order).equalsIgnoreCase(value)) return Optional.of(order);
        }
        return Optional.empty();
    }

    private static Optional<TimeRange> range(JsonNode args) {
        return TimeRange.byId(args.path("range").asString(Views.DEFAULT_RANGE.id()));
    }

    /**
     * The materials of delivered orders, the most ordered first, or those matching a search.
     */
    private Payloads.MarketPage market(JsonNode args, Optional<String> search) {
        var query = search.map(value -> value.toLowerCase(Locale.ROOT).strip().replace("minecraft:", ""));
        var items = marketItems()
                .filter(item -> query.isEmpty()
                        || item.item().name().toLowerCase(Locale.ROOT).contains(query.get())
                        || item.item().icon().contains(query.get().replace(' ', '_')))
                .sorted(Comparator.comparingLong(Payloads.MarketItem::volume)
                        .reversed()
                        .thenComparing(item -> item.item().name()))
                .toList();
        return marketPage(args, items);
    }

    private Payloads.MarketPage movers(JsonNode args) {
        var items = marketItems()
                .filter(item -> item.change() != null)
                .sorted(Comparator.comparingDouble(
                                (Payloads.MarketItem item) -> Math.abs(Objects.requireNonNullElse(item.change(), 0.0)))
                        .reversed()
                        .thenComparing(item -> item.item().name()))
                .toList();
        return marketPage(args, items);
    }

    private Payloads.MarketPage marketPage(JsonNode args, List<Payloads.MarketItem> items) {
        var page = page(args);
        var size = size(args);
        return new Payloads.MarketPage(
                page, Payloads.pages(items.size(), size), items.size(), Payloads.slice(items, page, size));
    }

    private Stream<Payloads.MarketItem> marketItems() {
        var changes = sources.marketChanges().get();
        return sources.market().get().stream()
                .map(row -> marketItem(row, changes))
                .flatMap(Optional::stream);
    }

    private ActionResult marketItem(ActionRequest request, JsonNode args) {
        var key = args.path("material").asString("").toUpperCase(Locale.ROOT).replace("MINECRAFT:", "");
        var item = sources.market().get().stream()
                .filter(row -> row.material().equals(key))
                .findFirst()
                .flatMap(row -> marketItem(row, sources.marketChanges().get()));
        if (item.isEmpty()) return refused(request, "dashboard.invalid");
        var days = Math.clamp(
                args.path("days").asInt(DEFAULT_HISTORY_DAYS),
                1,
                sources.marketSettings().get().historyDays());
        var history = sources.marketHistory().apply(key, days).stream()
                .map(point ->
                        new Payloads.HistoryPoint(point.day().toString(), point.orders(), point.items(), point.avg()))
                .toList();
        return data(
                request,
                new Payloads.MarketDetail(
                        item.get(), days, sources.marketToday().get().toString(), history));
    }

    private Optional<Payloads.MarketItem> marketItem(MarketRow row, Map<String, Double> changes) {
        var material = Material.matchMaterial(row.material());
        if (material == null) return Optional.empty();
        var shown = row.orders() >= sources.marketSettings().get().minOrders();
        return Optional.of(new Payloads.MarketItem(
                Views.item(material, 1),
                shown ? row.avg() : null,
                shown ? row.min() : null,
                shown ? row.max() : null,
                row.volume(),
                row.orders(),
                changes.get(row.material())));
    }

    private ActionResult applications(ActionRequest request, UUID player) {
        var company = views.companyOf(player);
        if (company.isEmpty()) return refused(request, "error.noMember");
        if (!hasPermission(company.get(), player, CompanyPermission.INVITE)) {
            return refused(request, "error.permission.invite");
        }
        var now = LocalDateTime.now(sources.clock().withZone(views.zone()));
        var applications = sources
                .memberships()
                .retrieveApplicationsTo(company.get().id(), now)
                .join()
                .stream()
                .map(application -> new Payloads.Application(
                        new PlayerRef(application.applicant(), sources.names().apply(application.applicant())),
                        application.message(),
                        views.instant(application.created()),
                        views.instant(application.expires())))
                .toList();
        var invites = sources.memberships().retrieveInvitesBy(company.get().id(), now).join().stream()
                .map(invite -> new Payloads.SentInvite(
                        new PlayerRef(invite.target(), sources.names().apply(invite.target())),
                        new PlayerRef(invite.inviter(), sources.names().apply(invite.inviter())),
                        views.instant(invite.created()),
                        views.instant(invite.expires())))
                .toList();
        return data(request, new Payloads.Applications(1, 1, applications.size(), applications, invites));
    }

    private ActionResult treasuryLog(ActionRequest request, UUID player, JsonNode args) {
        var company = views.companyOf(player);
        if (company.isEmpty()) return refused(request, "error.noMember");
        if (!hasPermission(company.get(), player, CompanyPermission.TREASURY)) {
            return refused(request, "error.permission.treasury");
        }
        var page = page(args);
        var size = size(args);
        var entries = sources.treasury().retrieveLog(company.get().id(), 1, MAX_LISTED).join().stream()
                .map(entry -> new Payloads.TreasuryEntry(
                        entry.id(),
                        views.instant(entry.created()),
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
        return data(
                request,
                new Payloads.Page<>(
                        page,
                        Payloads.pages(entries.size(), size),
                        entries.size(),
                        Payloads.slice(entries, page, size)));
    }

    private static boolean hasPermission(ICompanyProfile company, UUID player, CompanyPermission permission) {
        return Views.memberOf(company, player)
                .map(member -> member.hasPermission(permission))
                .orElse(false);
    }

    private static int page(JsonNode args) {
        return Math.max(1, args.path("page").asInt(1));
    }

    private static int size(JsonNode args) {
        return Math.clamp(args.path("pageSize").asInt(DEFAULT_PAGE_SIZE), 1, MAX_PAGE_SIZE);
    }

    private ActionResult data(ActionRequest request, Object value) {
        return new ActionResult(request.id(), Protocol.VERSION, true, null, null, mapper.valueToTree(value));
    }

    private ActionResult refused(ActionRequest request, String reason) {
        return new ActionResult(
                request.id(), Protocol.VERSION, false, reason, sources.refusal().apply(reason), null);
    }

    /**
     * Where the pages are read from.
     *
     * @param search         searches the orders a player could accept, ignoring the in-game only fillable filter
     * @param inbox          the inboxes
     * @param memberships    invites and applications
     * @param treasury       the treasuries
     * @param market         what the delivered orders add up to per material
     * @param marketSettings the current market settings
     * @param marketChanges  the change of each material's price in percent, by material name
     * @param marketHistory  the price history of a material over a number of days
     * @param marketToday    today as the market history counts days
     * @param inboxText      renders an inbox entry as plain text
     * @param refusal        renders the message of a refusal key as plain text
     * @param names          a player's name by id
     * @param clock          the current time
     */
    public record Sources(
            BiFunction<UUID, SearchQuery, CompletableFuture<SearchResults>> search,
            AInboxData inbox,
            AMembershipData memberships,
            ATreasuryData treasury,
            Supplier<List<MarketRow>> market,
            Supplier<MarketSettings> marketSettings,
            Supplier<Map<String, Double>> marketChanges,
            BiFunction<String, Integer, List<MarketHistory.Point>> marketHistory,
            Supplier<LocalDate> marketToday,
            Function<InboxEntry, String> inboxText,
            Function<String, String> refusal,
            Function<UUID, String> names,
            Clock clock) {}
}
