/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.boards;

import de.eldoria.companies.commands.company.order.search.SearchQuery;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.elements.BoardSettings;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.orders.offers.OfferMarks;
import de.eldoria.companies.orders.query.OrderFilter;
import de.eldoria.companies.util.Permission;
import de.eldoria.companies.util.Threading;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Resolves boards and opens them.
 * <p>
 * A board is given as a preset name of the configuration, as {@code event} or {@code event <id>} for the chunks of
 * all event offers or of one, as materials separated by spaces (matched by a part of their name), or as nothing for
 * every open order. Contests are no orders and show on no board; {@code /company events} lists them. A material has to be part of the name of at least one material, so
 * a mistyped preset name is refused instead of showing an empty board.
 * <p>
 * Opening needs {@link Permission.Boards#USE} and replaces the player's search query with the board's filter. A player
 * opens at most one board per second, so a sign clicked twice or an NPC firing twice shows one menu. The view showing
 * the results is {@link #bind(ResultsView) bound} by the command tree, which owns the order search.
 */
public final class Boards implements BoardOpener {
    private static final Duration OPEN_COOLDOWN = Duration.ofSeconds(1);
    private static final int PRUNE_SIZE = 256;
    private static final Pattern MATERIAL_PART = Pattern.compile("[a-z0-9_]+");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private final Supplier<BoardSettings> settings;
    private final AOrderData orderData;
    private final Predicate<String> knownMaterial;
    private final Clock clock;
    private final AtomicReference<ResultsView> view = new AtomicReference<>();
    private final List<Runnable> presetListeners = new CopyOnWriteArrayList<>();
    private final Map<UUID, Instant> lastOpened = new ConcurrentHashMap<>();
    private volatile OfferMarks offerMarks = OfferMarks.NONE;
    private volatile IntFunction<Integer> unclaimedChunks = offer -> 0;

    /**
     * @param settings      the current board settings
     * @param orderData     order repository
     * @param knownMaterial whether a text is part of the name of a material, in lower case
     * @param clock         clock of the open cooldown
     */
    public Boards(
            Supplier<BoardSettings> settings, AOrderData orderData, Predicate<String> knownMaterial, Clock clock) {
        this.settings = settings;
        this.orderData = orderData;
        this.knownMaterial = knownMaterial;
        this.clock = clock;
    }

    /**
     * Boards checking material names against the materials of the server.
     *
     * @param settings  the current board settings
     * @param orderData order repository
     * @return the boards
     */
    public static Boards create(Supplier<BoardSettings> settings, AOrderData orderData) {
        return new Boards(settings, orderData, MaterialNames::containsPart, Clock.systemUTC());
    }

    /**
     * Binds the view that shows the results of a board. A later binding replaces the earlier one, since the command
     * tree is built again when the server reloads its commands.
     *
     * @param results the view of the results
     */
    public void bind(ResultsView results) {
        view.set(results);
    }

    /**
     * Lets the boards of event offers count their chunks, once the offers can be read.
     *
     * @param marks  finds the event offers of orders, for boards that filter more than the offer
     * @param chunks counts the chunks of an offer waiting to be taken, 0 for every offer
     */
    public void useOffers(OfferMarks marks, IntFunction<Integer> chunks) {
        offerMarks = marks;
        unclaimedChunks = chunks;
    }

    /**
     * Resolves what a board shows.
     *
     * @param presetOrMaterials a preset name, materials separated by spaces, or null or blank for every open order
     * @return the board, empty when the text is neither a preset nor known materials
     */
    public Optional<BoardTarget> resolve(@Nullable String presetOrMaterials) {
        if (presetOrMaterials == null || presetOrMaterials.isBlank()) return Optional.of(BoardTarget.ALL);
        var text = presetOrMaterials.strip().toLowerCase(Locale.ROOT);
        var preset = settings.get().preset(text);
        if (preset.isPresent()) {
            return Optional.of(new BoardTarget(
                    text,
                    preset.get().filter(),
                    preset.get().label(text),
                    preset.get().offers()));
        }
        var event = event(text);
        if (event != null) return Optional.of(event);
        var materials = List.of(SPACES.split(text));
        if (!materials.stream().allMatch(this::isMaterial)) return Optional.empty();
        return Optional.of(
                new BoardTarget(null, OrderFilter.ALL.withMaterials(materials), String.join(" ", materials)));
    }

    /**
     * @param text a board text in lower case
     * @return the board of event offers it names, {@code event} or {@code event <id>}, null for another text
     */
    private static @Nullable BoardTarget event(String text) {
        if (text.equals(BoardTarget.EVENT)) return new BoardTarget(null, OrderFilter.ALL, BoardTarget.EVENT, 0);
        if (!text.startsWith(BoardTarget.EVENT + " ")) return null;
        try {
            var id = Integer.parseInt(
                    text.substring(BoardTarget.EVENT.length() + 1).strip());
            return id > 0 ? new BoardTarget(null, OrderFilter.ALL, BoardTarget.EVENT + " " + id, id) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isMaterial(String part) {
        return MATERIAL_PART.matcher(part).matches() && knownMaterial.test(part);
    }

    /**
     * Resolves a board or refuses.
     *
     * @param presetOrMaterials a preset name, materials separated by spaces, or null or blank for every open order
     * @return the board
     * @throws CommandFailure {@code boards.error.unknown} when the text is neither a preset nor known materials
     */
    public BoardTarget require(@Nullable String presetOrMaterials) {
        return resolve(presetOrMaterials)
                .orElseThrow(() -> CommandFailure.of(
                        "boards.error.unknown", Placeholder.unparsed("board", String.valueOf(presetOrMaterials))));
    }

    /**
     * @return the names of the configured presets in lower case
     */
    public List<String> presetNames() {
        return List.copyOf(settings.get().presets().keySet());
    }

    /**
     * Counts the orders a board shows: the open orders matching its filter, regardless of who looks, and for a board of
     * event offers only the chunks of those offers.
     *
     * @param target what the board shows
     * @return number of open orders the board shows
     */
    public CompletableFuture<Integer> openCount(BoardTarget target) {
        var offers = target.offers();
        if (offers == null) return openCount(target.filter());
        if (target.filter().equals(OrderFilter.ALL)) {
            var chunks = unclaimedChunks;
            return CompletableFuture.supplyAsync(() -> chunks.apply(offers), Threading.VIRTUAL);
        }
        var query = target.query();
        var marks = offerMarks;
        return orderData
                .retrieveOrdersByQuery(query, OrderState.UNCLAIMED, OrderState.UNCLAIMED)
                .asFuture()
                .thenApply(found -> {
                    var shown =
                            marks.marks(found.stream().map(order -> order.id()).toList());
                    return (int) found.stream()
                            .filter(order -> query.showsOffer(shown.get(order.id())))
                            .count();
                });
    }

    /**
     * Counts the open orders matching a filter.
     *
     * @param filter filter of the board
     * @return number of open orders matching the filter
     */
    public CompletableFuture<Integer> openCount(OrderFilter filter) {
        return orderData
                .retrieveOrdersByQuery(SearchQuery.of(filter), OrderState.UNCLAIMED, OrderState.UNCLAIMED)
                .asFuture()
                .thenApply(List::size);
    }

    @Override
    public void open(Player player, BoardTarget target) {
        if (!player.hasPermission(Permission.Boards.USE)) throw CommandFailure.of("boards.error.permission");
        var results = view.get();
        if (results == null) throw CommandFailure.of("error.notReady");
        if (!startOpening(player.getUniqueId())) return;
        results.show(player, target.query());
    }

    private boolean startOpening(UUID player) {
        var now = clock.instant();
        var previous = lastOpened.get(player);
        if (previous != null && now.isBefore(previous.plus(OPEN_COOLDOWN))) return false;
        if (lastOpened.size() >= PRUNE_SIZE)
            lastOpened.values().removeIf(opened -> opened.isBefore(now.minus(OPEN_COOLDOWN)));
        lastOpened.put(player, now);
        return true;
    }

    /**
     * @param listener told when the presets may have changed, like the board signs that show their labels
     */
    public void onPresetsChanged(Runnable listener) {
        presetListeners.add(listener);
    }

    /**
     * Tells the listeners that the presets may have changed, after the configuration was reloaded.
     */
    public void presetsChanged() {
        presetListeners.forEach(Runnable::run);
    }

    /**
     * Shows the results of a board to a player.
     */
    @FunctionalInterface
    public interface ResultsView {
        /**
         * Makes the query the player's search and shows its first page of results. Main thread.
         *
         * @param player player using the board
         * @param query  the board's filter as a new query
         */
        void show(Player player, SearchQuery query);
    }

    /**
     * The names of the materials of the server in lower case, read once when first needed.
     */
    private static final class MaterialNames {
        private static final Set<String> NAMES = Arrays.stream(Material.values())
                .filter(material -> !material.isLegacy())
                .map(material -> material.name().toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());

        private MaterialNames() {
            throw new UnsupportedOperationException("This is a utility class.");
        }

        static boolean containsPart(String part) {
            return NAMES.stream().anyMatch(name -> name.contains(part));
        }
    }
}
