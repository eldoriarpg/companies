/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.views;

import de.eldoria.companies.api.CompaniesApi;
import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.company.ICompanyMember;
import de.eldoria.companies.components.company.ICompanyProfile;
import de.eldoria.companies.components.company.ICompanyRank;
import de.eldoria.companies.components.order.IFullOrder;
import de.eldoria.companies.components.order.IOrderContent;
import de.eldoria.companies.components.order.ISimpleOrder;
import de.eldoria.companies.components.ranking.TimeRange;
import de.eldoria.companies.components.ranking.TopOrder;
import de.eldoria.companies.configuration.elements.CompanySettings;
import de.eldoria.companies.configuration.elements.OrderSettings;
import de.eldoria.companies.data.repository.AAlertData;
import de.eldoria.companies.data.repository.AInboxData;
import de.eldoria.companies.data.repository.AMembershipData;
import de.eldoria.companies.data.repository.AStatsData;
import de.eldoria.companies.orders.SystemOwner;
import de.eldoria.companies.web.api.Protocol;
import de.eldoria.companies.web.api.snapshot.AlertView;
import de.eldoria.companies.web.api.snapshot.ClaimedOrderView;
import de.eldoria.companies.web.api.snapshot.CompanyView;
import de.eldoria.companies.web.api.snapshot.InviteView;
import de.eldoria.companies.web.api.snapshot.ItemView;
import de.eldoria.companies.web.api.snapshot.JoinMode;
import de.eldoria.companies.web.api.snapshot.LevelView;
import de.eldoria.companies.web.api.snapshot.MemberPermission;
import de.eldoria.companies.web.api.snapshot.MemberStatsView;
import de.eldoria.companies.web.api.snapshot.MemberView;
import de.eldoria.companies.web.api.snapshot.OrderLineView;
import de.eldoria.companies.web.api.snapshot.OrderState;
import de.eldoria.companies.web.api.snapshot.OwnOrderView;
import de.eldoria.companies.web.api.snapshot.PaletteColor;
import de.eldoria.companies.web.api.snapshot.PlayerView;
import de.eldoria.companies.web.api.snapshot.RankingEntry;
import de.eldoria.companies.web.api.snapshot.RankingPage;
import de.eldoria.companies.web.api.snapshot.Snapshot;
import de.eldoria.companies.web.api.snapshot.TreasuryView;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Builds the views of the dashboard from the plugin's data, as the viewing player may see them in game.
 * <p>
 * Orders stay anonymous between owner and company: an {@link OwnOrderView} never names the company or a worker, a
 * {@link ClaimedOrderView} never names the owner. Inside a company the members are named. The treasury balance is
 * shown to members with the treasury permission only. The methods block on the database and are called off the main
 * thread.
 */
public final class Views {
    /**
     * The ranking shown in the snapshot.
     */
    public static final TopOrder DEFAULT_RANKING = TopOrder.ORDERS;

    /**
     * The range of the ranking shown in the snapshot.
     */
    public static final TimeRange DEFAULT_RANGE = TimeRange.ALL;

    /**
     * Entries of a ranking page.
     */
    public static final int RANKING_PAGE_SIZE = 10;

    private final Sources sources;

    /**
     * @param sources where the views are read from
     */
    public Views(Sources sources) {
        this.sources = sources;
    }

    /**
     * The snapshot of a session.
     *
     * @param id      the id of the request it answers
     * @param session the session id
     * @param player  the session's player
     * @param node    this node's uid
     * @param seq     this node's patch sequence now
     * @return the snapshot
     */
    public Snapshot snapshot(@Nullable String id, String session, UUID player, UUID node, long seq) {
        var company = companyOf(player);
        return new Snapshot(
                id,
                Protocol.VERSION,
                session,
                node,
                seq,
                player(player, company),
                company.map(found -> company(found, player)).orElse(null),
                ownOrders(player),
                ranking(DEFAULT_RANKING, DEFAULT_RANGE, 1, company.map(ICompanyProfile::id)));
    }

    /**
     * The company a player is a member of.
     *
     * @param player the player
     * @return the company, empty without one
     */
    public Optional<ICompanyProfile> companyOf(UUID player) {
        return sources.api().companies().of(player).join().map(ICompanyProfile.class::cast);
    }

    /**
     * A company by id.
     *
     * @param company the company id
     * @return the company, empty when it does not exist
     */
    public Optional<ICompanyProfile> company(int company) {
        return sources.api().companies().byId(company).join().map(ICompanyProfile.class::cast);
    }

    /**
     * An order by id, with its lines.
     *
     * @param order the order id
     * @return the order, empty when it does not exist
     */
    public Optional<IFullOrder> order(int order) {
        return sources.api().orders().byId(order).join().map(IFullOrder.class::cast);
    }

    /**
     * The orders a player placed, within a range of states.
     *
     * @param player the owner
     * @param min    the lowest state
     * @param max    the highest state
     * @return the orders with their lines
     */
    public List<IFullOrder> ordersOf(
            UUID player,
            de.eldoria.companies.components.order.OrderState min,
            de.eldoria.companies.components.order.OrderState max) {
        return sources.api().orders().byOwner(player, min, max).join().stream()
                .map(IFullOrder.class::cast)
                .toList();
    }

    /**
     * @return the time zone of the plugin's stored times
     */
    public ZoneId zone() {
        return sources.zone();
    }

    /**
     * The player view.
     *
     * @param player  the player
     * @param company the player's company
     * @return the view
     */
    public PlayerView player(UUID player, Optional<ICompanyProfile> company) {
        var permissions = company.flatMap(found -> memberOf(found, player))
                .map(Views::permissions)
                .orElse(List.of());
        return new PlayerView(
                player,
                sources.names().apply(player),
                company.map(ICompanyProfile::id).orElse(null),
                permissions,
                invites(player),
                unreadInbox(player),
                null);
    }

    /**
     * The open invites of a player.
     *
     * @param player the player
     * @return the invites
     */
    public List<InviteView> invites(UUID player) {
        return sources.memberships().retrieveInvitesOf(player, now()).join().stream()
                .map(invite -> new InviteView(invite.company(), invite.companyName()))
                .toList();
    }

    /**
     * The number of unread inbox entries outside of muted categories.
     *
     * @param player the player
     * @return the number of unread entries
     */
    public int unreadInbox(UUID player) {
        var inbox = sources.inbox();
        var muted = inbox.retrieveMuted(player).join();
        return inbox.retrieveUnreadCount(player, muted).join();
    }

    /**
     * The company view as a member sees it.
     *
     * @param company the company
     * @param viewer  the member viewing it
     * @return the view
     */
    public CompanyView company(ICompanyProfile company, UUID viewer) {
        var claimed = claimedFullOrders(company.id());
        var stats = memberStats(company.id(), claimed);
        var members = company.members().stream()
                .sorted(Comparator.comparing((ICompanyMember member) -> !member.isOwner())
                        .thenComparing(member -> sources.names().apply(member.uuid()), String.CASE_INSENSITIVE_ORDER))
                .map(member -> member(member, stats))
                .toList();
        return new CompanyView(
                company.id(),
                company.name(),
                company.tag().orElse(null),
                PlainText.hexColor(company.color().orElse(null)),
                company.description().map(PlainText::ofMiniMessage).orElse(null),
                joinMode(company.joinMode()),
                instant(company.founded()),
                level(company),
                members,
                treasury(company, viewer).orElse(null),
                claimed.stream().map(this::claimed).toList(),
                alerts(company.id()),
                company.color().orElse(null),
                palette());
    }

    private List<PaletteColor> palette() {
        return sources.companySettings().get().profile().colors().stream()
                .map(name -> name.toLowerCase(Locale.ROOT))
                .flatMap(name ->
                        Optional.ofNullable(PlainText.hexColor(name)).map(hex -> new PaletteColor(name, hex)).stream())
                .toList();
    }

    /**
     * One member of a company with statistics.
     *
     * @param company the company
     * @param player  the member
     * @return the member, empty when the player is no member
     */
    public Optional<MemberView> member(ICompanyProfile company, UUID player) {
        var member = memberOf(company, player);
        if (member.isEmpty()) return Optional.empty();
        var stats = memberStats(company.id(), claimedFullOrders(company.id()));
        return Optional.of(member(member.get(), stats));
    }

    private MemberView member(ICompanyMember member, Map<UUID, MemberStatsView> stats) {
        var view = stats.getOrDefault(member.uuid(), new MemberStatsView(0, 0, 0));
        return new MemberView(member.uuid(), sources.names().apply(member.uuid()), permissions(member), view);
    }

    /**
     * What each worker achieved for a company: the completed orders with their payouts, plus the items delivered to
     * the orders the company works on now, so a delivery shows at once.
     *
     * @param company the company id
     * @param claimed the orders the company works on now
     * @return the statistics by worker
     */
    private Map<UUID, MemberStatsView> memberStats(int company, List<IFullOrder> claimed) {
        Map<UUID, MemberStatsView> stats = new HashMap<>();
        for (var stat :
                sources.api().companies().memberStats(company, TimeRange.ALL).join()) {
            stats.put(stat.worker(), new MemberStatsView(stat.orders(), stat.items(), stat.earned()));
        }
        for (var order : claimed) {
            for (var line : order.contents()) {
                for (var part : line.parts()) {
                    stats.merge(
                            part.worker(),
                            new MemberStatsView(0, part.amount(), 0),
                            (have, more) ->
                                    new MemberStatsView(have.orders(), have.items() + more.items(), have.earned()));
                }
            }
        }
        return stats;
    }

    /**
     * The treasury as a member sees it.
     *
     * @param company the company
     * @param viewer  the member viewing it
     * @return the treasury, empty for members without the treasury permission
     */
    public Optional<TreasuryView> treasury(ICompanyProfile company, UUID viewer) {
        var allowed = memberOf(company, viewer)
                .map(member -> member.hasPermission(CompanyPermission.TREASURY))
                .orElse(false);
        if (!allowed) return Optional.empty();
        return sources.api().companies().treasury(company.id()).join().map(treasury -> {
            var settings = sources.companySettings().get().treasury();
            return new TreasuryView(
                    treasury.balance().doubleValue(),
                    settings.effectiveShare(treasury.payoutShare()),
                    settings.maxPayoutShare(),
                    settings.minTransaction());
        });
    }

    private LevelView level(ICompanyProfile company) {
        var settings = sources.companySettings().get();
        var current = settings.level(company.level());
        var name = current.map(level -> PlainText.ofMiniMessage(level.levelName()))
                .orElse(String.valueOf(company.level()));
        var next = settings.level(company.level() + 1);
        if (next.isEmpty()) return new LevelView(company.level(), name, null);
        var stats = sources.api().companies().stats(company.id()).join();
        if (stats.isEmpty()) return new LevelView(company.level(), name, 0.0);
        var requirement = next.get().requirement();
        var progress = 1.0;
        progress = Math.min(progress, share(stats.get().orderCount(), requirement.orderCount()));
        progress = Math.min(progress, share(stats.get().price(), requirement.earnedMoney()));
        progress = Math.min(progress, share(stats.get().deliveredItems(), requirement.deliveredItems()));
        progress = Math.min(progress, share(stats.get().memberCount(), requirement.memberCount()));
        return new LevelView(company.level(), name, progress);
    }

    private static double share(double have, double need) {
        if (need <= 0) return 1.0;
        return Math.clamp(have / need, 0.0, 1.0);
    }

    /**
     * The orders a company has claimed and works on.
     *
     * @param company the company id
     * @return the orders with their lines
     */
    private List<IFullOrder> claimedFullOrders(int company) {
        return sources
                .api()
                .orders()
                .byCompany(
                        company,
                        de.eldoria.companies.components.order.OrderState.CLAIMED,
                        de.eldoria.companies.components.order.OrderState.CLAIMED)
                .join()
                .stream()
                .map(IFullOrder.class::cast)
                .toList();
    }

    /**
     * An order as the company working on it sees it, without its owner.
     *
     * @param order the order
     * @return the view
     */
    public ClaimedOrderView claimed(IFullOrder order) {
        var claimed = order.claimed() == null ? order.created() : order.claimed();
        return new ClaimedOrderView(
                order.id(),
                order.name(),
                state(order),
                lines(order),
                instant(claimed),
                deadline(order).orElse(null));
    }

    private List<AlertView> alerts(int company) {
        return sources.alerts().retrieveByCompany(company).join().stream()
                .map(alert -> new AlertView(alert.name(), alert.enabled()))
                .toList();
    }

    /**
     * The orders a player placed that are not received yet.
     *
     * @param player the player
     * @return the orders, oldest first
     */
    public List<OwnOrderView> ownOrders(UUID player) {
        return sources
                .api()
                .orders()
                .byOwner(
                        player,
                        de.eldoria.companies.components.order.OrderState.UNCLAIMED,
                        de.eldoria.companies.components.order.OrderState.DELIVERED)
                .join()
                .stream()
                .map(this::own)
                .toList();
    }

    /**
     * An order as its owner sees it, without the company or worker.
     *
     * @param order the order
     * @return the view
     */
    public OwnOrderView own(IFullOrder order) {
        var waiting = order.state() == de.eldoria.companies.components.order.OrderState.DELIVERED
                ? order.contents().stream()
                        .map(line -> item(line, line.delivered()))
                        .toList()
                : List.<ItemView>of();
        return new OwnOrderView(
                order.id(),
                order.name(),
                state(order),
                lines(order),
                instant(order.created()),
                deadline(order).orElse(null),
                waiting);
    }

    /**
     * An order as a player sees it in the search, the details and the own order list. It never names the owner, the
     * company or a worker.
     *
     * @param order   the order
     * @param owner   whether the player placed it, which adds the items waiting to be received
     * @param company whether the player's company works on it, which adds when it was claimed
     * @return the summary
     */
    public Payloads.OrderSummary summary(IFullOrder order, boolean owner, boolean company) {
        Instant expires = null;
        if (order.state() == de.eldoria.companies.components.order.OrderState.UNCLAIMED) {
            var hours = sources.orderSettings().get().maxUnclaimedHours();
            expires = hours > 0 ? instant(order.created().plusHours(hours)) : null;
        }
        var claimed = company && order.claimed() != null ? instant(order.claimed()) : null;
        return new Payloads.OrderSummary(
                order.id(),
                order.name(),
                state(order),
                lines(order),
                instant(order.created()),
                deadline(order).orElse(null),
                expires,
                order.price(),
                order.amount(),
                claimed,
                owner ? own(order).waiting() : null,
                sources.marked().getAsBoolean() && SystemOwner.is(order.owner()));
    }

    /**
     * The lines of an order.
     *
     * @param order the order
     * @return the lines in their order
     */
    public List<OrderLineView> lines(IFullOrder order) {
        return order.contents().stream()
                .sorted(Comparator.comparingInt(IOrderContent::line))
                .map(line -> new OrderLineView(item(line, line.amount()), line.delivered(), line.price()))
                .toList();
    }

    /**
     * Describes the item of a line.
     *
     * @param line   the line
     * @param amount the amount to show
     * @return the item
     */
    public ItemView item(IOrderContent line, int amount) {
        return line.exact() ? item(line.stack(), amount) : item(line.material(), amount);
    }

    /**
     * Describes an item with the name it carries.
     *
     * @param stack  the item
     * @param amount the amount to show
     * @return the item
     */
    public static ItemView item(ItemStack stack, int amount) {
        var view = item(stack.getType(), amount);
        var meta = stack.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return view;
        var displayName = meta.displayName();
        if (displayName == null) return view;
        return new ItemView(view.material(), PlainText.of(displayName), amount, view.icon());
    }

    /**
     * Describes a material.
     *
     * @param material the material
     * @param amount   the amount to show
     * @return the item
     */
    public static ItemView item(Material material, int amount) {
        var key = material.getKey();
        return new ItemView(key.toString(), PlainText.materialName(material), amount, key.getKey());
    }

    private Optional<Instant> deadline(ISimpleOrder order) {
        if (order.state() != de.eldoria.companies.components.order.OrderState.CLAIMED || order.claimed() == null) {
            return Optional.empty();
        }
        var hours = sources.companySettings().get().deliveryHours();
        return Optional.of(instant(order.claimed().plusHours(hours)));
    }

    /**
     * A page of a ranking with the place of the player's company. Only the page and the number of ranked companies are
     * read. A page past the last one shows the last page.
     *
     * @param ranking what the ranking is sorted by
     * @param range   the time range
     * @param page    the page, starting at 1
     * @param company the player's company
     * @return the page
     */
    public RankingPage ranking(TopOrder ranking, TimeRange range, int page, Optional<Integer> company) {
        var total = sources.stats().retrieveRankedCount(range, now()).join();
        var pages = Payloads.pages(total, RANKING_PAGE_SIZE);
        var shown = Math.clamp(page, 1, pages);
        var entries = sources.api().ranking().page(ranking, range, shown, RANKING_PAGE_SIZE).join().stream()
                .map(rank -> new RankingEntry(rank.rank(), rank.id(), rank.name(), value(ranking, rank)))
                .toList();
        var own = company.flatMap(id -> rank(ranking, range, id)).orElse(null);
        return new RankingPage(key(ranking), range.id(), shown, pages, entries, own);
    }

    /**
     * The place of a company in a ranking.
     *
     * @param ranking what the ranking is sorted by
     * @param range   the time range
     * @param company the company id
     * @return the place, empty when the company is not ranked
     */
    public Optional<RankingEntry> rank(TopOrder ranking, TimeRange range, int company) {
        return sources.api()
                .ranking()
                .rankOf(company, ranking, range)
                .join()
                .map(rank -> new RankingEntry(rank.rank(), rank.id(), rank.name(), value(ranking, rank)));
    }

    /**
     * The key of a ranking in the protocol.
     *
     * @param ranking the ranking
     * @return e.g. {@code orders} or {@code items_delivered}
     */
    public static String key(TopOrder ranking) {
        return ranking.name().toLowerCase(Locale.ROOT);
    }

    private static double value(TopOrder ranking, ICompanyRank rank) {
        return switch (ranking) {
            case ORDERS -> rank.orderCount();
            case EARNED -> rank.price();
            case MEMBER -> rank.memberCount();
            case ITEMS_DELIVERED -> rank.deliveredItems();
            case LEVEL -> rank.level();
        };
    }

    /**
     * The membership of a player in a company.
     *
     * @param company the company
     * @param player  the player
     * @return the member, empty when the player is no member
     */
    public static Optional<ICompanyMember> memberOf(ICompanyProfile company, UUID player) {
        return company.members().stream()
                .filter(member -> member.uuid().equals(player))
                .map(ICompanyMember.class::cast)
                .findFirst();
    }

    private static List<MemberPermission> permissions(ICompanyMember member) {
        return member.permissions().stream()
                .map(permission -> MemberPermission.valueOf(permission.name()))
                .sorted()
                .toList();
    }

    private static JoinMode joinMode(de.eldoria.companies.components.company.JoinMode mode) {
        return switch (mode) {
            case OPEN -> JoinMode.OPEN;
            case APPLICATIONS -> JoinMode.APPLICATIONS;
            case INVITE_ONLY -> JoinMode.INVITE_ONLY;
        };
    }

    private static OrderState state(ISimpleOrder order) {
        return switch (order.state()) {
            case UNCLAIMED -> OrderState.UNCLAIMED;
            case CLAIMED -> OrderState.CLAIMED;
            case DELIVERED -> OrderState.DELIVERED;
            case RECEIVED -> OrderState.RECEIVED;
        };
    }

    /**
     * A time of the plugin's data, which is stored in UTC.
     *
     * @param time the time
     * @return the instant
     */
    public Instant instant(LocalDateTime time) {
        return time.atZone(sources.zone()).toInstant();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(sources.clock().withZone(sources.zone()));
    }

    /**
     * Where the views are read from.
     *
     * @param api             the plugin's API
     * @param stats           the statistics, which count the ranked companies
     * @param memberships     invites and applications
     * @param alerts          the companies' order alerts
     * @param inbox           the players' inboxes
     * @param companySettings the current company settings
     * @param orderSettings   the current order settings
     * @param names           a player's name by id
     * @param zone            the time zone of the plugin's stored times
     * @param clock           the current time
     * @param marked          whether the server marks the orders it placed itself
     */
    public record Sources(
            CompaniesApi api,
            AStatsData stats,
            AMembershipData memberships,
            AAlertData alerts,
            AInboxData inbox,
            Supplier<CompanySettings> companySettings,
            Supplier<OrderSettings> orderSettings,
            Function<UUID, String> names,
            ZoneId zone,
            Clock clock,
            BooleanSupplier marked) {}
}
