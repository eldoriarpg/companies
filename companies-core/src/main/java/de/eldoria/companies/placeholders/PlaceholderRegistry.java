/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.placeholders;

import de.eldoria.companies.components.ranking.TimeRange;
import de.eldoria.companies.components.ranking.TopOrder;
import de.eldoria.companies.data.wrapper.company.CompanyRank;
import de.eldoria.companies.data.wrapper.offer.OfferKind;
import de.eldoria.companies.menus.order.EventTexts;
import de.eldoria.companies.util.ProfileColors;
import de.eldoria.companies.util.Texts;
import de.eldoria.companies.util.Times;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.DoubleFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/**
 * Resolves the placeholder ids of the {@code companies} expansion from the caches.
 * <p>
 * Player ids describe the requesting player, company ids the player's company and are empty (or zero for counts)
 * without one. Leaderboard ids read {@code top_<order>[_<range>]_<place>_<field>} and company ranks
 * {@code company_rank_<order>[_<range>]}; the range is {@code all}, {@code 30d} or {@code 7d} and all time when left
 * out. Event offer ids read {@code offer_active_count} and {@code offer_<n>_<field>} for the n-th running goal or
 * contest, the soonest end first, and server event ids {@code event_active_count} and
 * {@code event_<n>_<name|effect|ends|budget_left>} for the n-th running server event, the soonest start first. While a value is loaded for the first time the loading text is answered. No id names a company working on a
 * player's order or the owner of an order a company works on. Ids are matched ignoring case; an unknown id resolves
 * to null.
 */
public final class PlaceholderRegistry {
    private static final String TOP = "top_";
    private static final String COMPANY_RANK = "company_rank_";
    private static final String OFFER = "offer_";
    private static final String OFFER_COUNT = "offer_active_count";
    private static final String EVENT = "event_";
    private static final String EVENT_COUNT = "event_active_count";
    private static final List<String> EVENT_FIELDS = List.of("budget_left", "effect", "ends", "name");
    private static final List<String> OFFER_FIELDS =
            List.of("name", "kind", "progress", "delivered", "amount", "item", "ends", "left", "id");
    private static final DateTimeFormatter ENDS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final List<String> TOP_FIELDS = List.of("tag_colored", "name", "tag", "value");
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character(LegacyComponentSerializer.SECTION_CHAR)
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private final Map<String, Function<PlayerView, String>> playerIds = new LinkedHashMap<>();
    private final Map<String, CompanyId> companyIds = new LinkedHashMap<>();
    private final PlayerPlaceholderCache players;
    private final CompanyPlaceholderCache companies;
    private final LeaderboardCache leaderboards;
    private final OfferPlaceholderCache offers;
    private final EventPlaceholderCache events;
    private final Function<String, String> translate;
    private final DoubleFunction<String> money;
    private final Supplier<String> loadingText;

    /**
     * @param players      values of the players
     * @param companies    values of the companies
     * @param leaderboards the rankings
     * @param money        formats an amount of money
     * @param translate    translates a locale key into the server language
     * @param loadingText  text answered while a value is loaded for the first time
     */
    public PlaceholderRegistry(
            PlayerPlaceholderCache players,
            CompanyPlaceholderCache companies,
            LeaderboardCache leaderboards,
            DoubleFunction<String> money,
            Function<String, String> translate,
            Supplier<String> loadingText) {
        this(
                players,
                companies,
                leaderboards,
                new OfferPlaceholderCache(() -> CompletableFuture.completedFuture(List.of())),
                money,
                translate,
                loadingText);
    }

    /**
     * @param players      values of the players
     * @param companies    values of the companies
     * @param leaderboards the rankings
     * @param offers       the event offers that run
     * @param money        formats an amount of money
     * @param translate    translates a locale key into the server language
     * @param loadingText  text answered while a value is loaded for the first time
     */
    public PlaceholderRegistry(
            PlayerPlaceholderCache players,
            CompanyPlaceholderCache companies,
            LeaderboardCache leaderboards,
            OfferPlaceholderCache offers,
            DoubleFunction<String> money,
            Function<String, String> translate,
            Supplier<String> loadingText) {
        this(
                players,
                companies,
                leaderboards,
                offers,
                new EventPlaceholderCache(() -> CompletableFuture.completedFuture(List.of()), Times.UTC),
                money,
                translate,
                loadingText);
    }

    /**
     * @param players      values of the players
     * @param companies    values of the companies
     * @param leaderboards the rankings
     * @param offers       the event offers that run
     * @param events       the server events that run
     * @param money        formats an amount of money
     * @param translate    translates a locale key into the server language
     * @param loadingText  text answered while a value is loaded for the first time
     */
    public PlaceholderRegistry(
            PlayerPlaceholderCache players,
            CompanyPlaceholderCache companies,
            LeaderboardCache leaderboards,
            OfferPlaceholderCache offers,
            EventPlaceholderCache events,
            DoubleFunction<String> money,
            Function<String, String> translate,
            Supplier<String> loadingText) {
        this.events = events;
        this.players = players;
        this.companies = companies;
        this.leaderboards = leaderboards;
        this.offers = offers;
        this.translate = translate;
        this.money = money;
        this.loadingText = loadingText;
        playerIds.put("player_open_orders", view -> String.valueOf(view.openOrders()));
        playerIds.put("player_order_limit", view -> String.valueOf(view.orderLimit()));
        playerIds.put("player_items_waiting", view -> String.valueOf(view.itemsWaiting()));
        playerIds.put("player_solo_active", view -> String.valueOf(view.soloActive()));
        playerIds.put("player_solo_completed", view -> String.valueOf(view.soloCompleted()));
        playerIds.put("player_role", view -> view.role()
                .map(role -> translate.apply(role.translationKey()))
                .orElse(""));
        companyIds.put("company_name", CompanyId.text(CompanyView::name));
        companyIds.put("company_size", CompanyId.count(view -> view.members()));
        companyIds.put("company_level", CompanyId.count(view -> view.level()));
        companyIds.put("active_orders", CompanyId.count(view -> view.activeOrders()));
        companyIds.put("company_tag", CompanyId.text(view -> view.tag().orElse("")));
        companyIds.put("company_tag_colored", CompanyId.text(view -> coloredTag(view.tag(), view.color())));
        companyIds.put("company_color", CompanyId.text(view -> view.color().orElse("")));
        companyIds.put("company_level_name", CompanyId.text(CompanyView::levelName));
        companyIds.put("company_owner", CompanyId.text(CompanyView::owner));
        companyIds.put("company_earned", CompanyId.text(view -> money.apply(view.earned())));
        companyIds.put("company_earned_raw", CompanyId.text(view -> raw(view.earned())));
        companyIds.put("company_delivered", CompanyId.count(view -> view.delivered()));
        companyIds.put("company_orders", CompanyId.count(view -> view.orders()));
        companyIds.put("company_treasury", CompanyId.text(view -> money.apply(view.treasury())));
        companyIds.put("company_payout_share", CompanyId.count(view -> view.payoutShare()));
    }

    /**
     * Every fixed id, followed by the patterns of the parsed ids.
     *
     * @return the ids in documentation order
     */
    public List<String> ids() {
        var ids = new ArrayList<String>(companyIds.keySet());
        ids.add(COMPANY_RANK + "<order>[_<range>]");
        ids.addAll(playerIds.keySet());
        ids.add(TOP + "<order>[_<range>]_<place>_<name|tag|tag_colored|value>");
        ids.add(OFFER_COUNT);
        ids.add(OFFER + "<n>_<" + String.join("|", OFFER_FIELDS) + ">");
        ids.add(EVENT_COUNT);
        ids.add(EVENT + "<n>_<" + String.join("|", EVENT_FIELDS) + ">");
        return ids;
    }

    /**
     * Resolves an id.
     *
     * @param player the requesting player, or null without one
     * @param id     id after the expansion prefix
     * @return the value, or null when the id is unknown
     */
    public @Nullable String resolve(@Nullable UUID player, String id) {
        var key = id.toLowerCase(Locale.ROOT);
        if (key.startsWith(TOP)) return top(key.substring(TOP.length()));
        if (key.startsWith(OFFER)) return offer(key);
        if (key.startsWith(EVENT)) return event(key);
        var playerId = playerIds.get(key);
        if (playerId != null)
            return player == null ? "" : players.get(player).map(playerId).orElseGet(loadingText);
        var companyId = companyIds.get(key);
        if (companyId == null && key.startsWith(COMPANY_RANK)) companyId = rank(key.substring(COMPANY_RANK.length()));
        if (companyId == null) return null;
        return company(player, companyId);
    }

    private String company(@Nullable UUID player, CompanyId companyId) {
        if (player == null) return companyId.fallback();
        var view = players.get(player);
        if (view.isEmpty()) return loadingText.get();
        var company = view.get().company();
        if (company.isEmpty()) return companyId.fallback();
        return companies.get(company.getAsInt()).map(companyId.value()).orElseGet(loadingText);
    }

    private @Nullable CompanyId rank(String board) {
        var parsed = Board.parse(board);
        if (parsed.isEmpty()) return null;
        var order = parsed.get().order();
        var range = parsed.get().range();
        return CompanyId.text(view -> {
            if (!leaderboards.loaded(order, range)) return loadingText.get();
            var place = leaderboards.place(view.id(), order, range);
            return place.isPresent() ? String.valueOf(place.getAsInt()) : "";
        });
    }

    /**
     * Resolves {@code offer_active_count} and {@code offer_<n>_<field>}, the n-th running offer by its end.
     */
    private @Nullable String offer(String key) {
        if (!offers.loaded()) return key.equals(OFFER_COUNT) || offerField(key) != null ? loadingText.get() : null;
        if (key.equals(OFFER_COUNT)) return String.valueOf(offers.count());
        var field = offerField(key);
        if (field == null) return null;
        var number = key.substring(OFFER.length(), key.length() - field.length() - 1);
        var place = place(number);
        if (place.isEmpty()) return null;
        var offer = offers.at(place.getAsInt());
        if (offer.isEmpty()) return "";
        var found = offer.get();
        return switch (field) {
            case "name" -> found.name();
            case "kind" ->
                translate.apply(found.kind() == OfferKind.CONTEST ? "offers.kind.contest" : "offers.kind.goal");
            case "progress" -> String.valueOf(found.percent());
            case "delivered" -> String.valueOf(found.delivered());
            case "amount" -> String.valueOf(found.amount());
            case "item" -> found.line().label();
            case "ends" -> ENDS.format(Times.local(found.ends()));
            case "left" -> {
                var left = Duration.between(Times.now(), found.ends());
                yield Texts.prettyDuration(left.isNegative() ? Duration.ZERO : left);
            }
            default -> String.valueOf(found.id());
        };
    }

    /**
     * Resolves {@code event_active_count} and {@code event_<n>_<field>}, the n-th running server event by its start.
     */
    private @Nullable String event(String key) {
        var field = eventField(key);
        if (!events.loaded()) return key.equals(EVENT_COUNT) || field != null ? loadingText.get() : null;
        if (key.equals(EVENT_COUNT)) return String.valueOf(events.count());
        if (field == null) return null;
        var place = place(key.substring(EVENT.length(), key.length() - field.length() - 1));
        if (place.isEmpty()) return null;
        var event = events.at(place.getAsInt());
        if (event.isEmpty()) return "";
        var found = event.get();
        return switch (field) {
            case "name" -> found.name();
            case "effect" -> EventTexts.plainEffect(found, translate::apply);
            case "ends" -> ENDS.format(Times.local(found.ends()));
            default -> {
                var left = found.remaining();
                yield found.kind().funded() && left.isPresent() ? money.apply(left.getAsDouble()) : "";
            }
        };
    }

    private static @Nullable String eventField(String key) {
        for (var field : EVENT_FIELDS) {
            if (key.endsWith("_" + field) && key.length() > EVENT.length() + field.length() + 1) return field;
        }
        return null;
    }

    private static @Nullable String offerField(String key) {
        for (var field : OFFER_FIELDS) {
            if (key.endsWith("_" + field) && key.length() > OFFER.length() + field.length() + 1) return field;
        }
        return null;
    }

    private @Nullable String top(String spec) {
        String field = null;
        for (var candidate : TOP_FIELDS) {
            if (spec.endsWith("_" + candidate)) {
                field = candidate;
                break;
            }
        }
        if (field == null) return null;
        var rest = spec.substring(0, spec.length() - field.length() - 1);
        var split = rest.lastIndexOf('_');
        if (split < 0) return null;
        var place = place(rest.substring(split + 1));
        var board = Board.parse(rest.substring(0, split));
        if (place.isEmpty() || board.isEmpty()) return null;
        var order = board.get().order();
        var range = board.get().range();
        if (!leaderboards.loaded(order, range)) return loadingText.get();
        var rank = leaderboards.at(order, range, place.getAsInt());
        if (rank.isEmpty()) return "";
        return field(field, order, rank.get());
    }

    private String field(String field, TopOrder order, CompanyRank rank) {
        return switch (field) {
            case "name" -> rank.name();
            case "tag" -> rank.tag().orElse("");
            case "tag_colored" -> coloredTag(rank.tag(), rank.color());
            default -> value(order, rank);
        };
    }

    private String value(TopOrder order, CompanyRank rank) {
        return switch (order) {
            case ORDERS -> String.valueOf(rank.orderCount());
            case EARNED -> money.apply(rank.price());
            case MEMBER -> String.valueOf(rank.memberCount());
            case ITEMS_DELIVERED -> String.valueOf(rank.deliveredItems());
            case LEVEL -> String.valueOf(rank.level());
        };
    }

    /**
     * The tag in the company colour as legacy text, hex colours in the {@code §x§r§r§g§g§b§b} form.
     *
     * @param tag   tag of the company
     * @param color stored colour of the company
     * @return the coloured tag, the plain tag without a colour, or empty without a tag
     */
    static String coloredTag(Optional<String> tag, Optional<String> color) {
        if (tag.isEmpty()) return "";
        return LEGACY.serialize(
                Component.text(tag.get(), color.flatMap(ProfileColors::resolve).orElse(null)));
    }

    private static String raw(double amount) {
        return String.format(Locale.ROOT, "%.2f", amount);
    }

    private static OptionalInt place(String text) {
        try {
            var place = Integer.parseInt(text);
            return place < 1 ? OptionalInt.empty() : OptionalInt.of(place);
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    /**
     * A company id: how the value is read and what it shows without a company.
     *
     * @param value    reads the value from the company
     * @param fallback value without a company
     */
    private record CompanyId(Function<CompanyView, String> value, String fallback) {
        static CompanyId text(Function<CompanyView, String> value) {
            return new CompanyId(value, "");
        }

        static CompanyId count(ToIntFunction<CompanyView> value) {
            return new CompanyId(view -> String.valueOf(value.applyAsInt(view)), "0");
        }
    }

    /**
     * An ordering with an optional range suffix, as in {@code items_delivered_30d}.
     *
     * @param order ordering
     * @param range range, all time when there is no suffix
     */
    private record Board(TopOrder order, TimeRange range) {
        static Optional<Board> parse(String text) {
            var split = text.lastIndexOf('_');
            if (split > 0) {
                var range = TimeRange.byId(text.substring(split + 1));
                if (range.isPresent())
                    return order(text.substring(0, split)).map(order -> new Board(order, range.get()));
            }
            return order(text).map(order -> new Board(order, TimeRange.ALL));
        }

        private static Optional<TopOrder> order(String text) {
            for (var order : TopOrder.values()) {
                if (order.name().equalsIgnoreCase(text)) return Optional.of(order);
            }
            return Optional.empty();
        }
    }
}
