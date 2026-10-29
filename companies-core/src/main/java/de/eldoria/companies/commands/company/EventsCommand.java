/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.company.order.search.OrderSearch;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.data.repository.AOfferData;
import de.eldoria.companies.data.wrapper.offer.ContestEntry;
import de.eldoria.companies.data.wrapper.offer.EventOffer;
import de.eldoria.companies.data.wrapper.offer.OfferKind;
import de.eldoria.companies.data.wrapper.offer.OfferState;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.util.Texts;
import de.eldoria.companies.util.Threading;
import de.eldoria.companies.util.Times;
import net.kyori.adventure.text.event.ClickEvent;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.arguments.IntArg;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;

/**
 * {@code /company events}: the event offers, goals and contests, that run, are paused or start within a day.
 * <p>
 * Each shows its kind, name, what it asks for, its progress, its time left or the time until its start, and for a
 * contest the place of the player's company. {@code open <id>} opens an offer: the order search showing only the chunks
 * of a goal, the search of orders to work on alone for a player without a company who may work alone, or the info of
 * a contest.
 */
@Subcommand("events")
@PlayerOnly
public final class EventsCommand {
    /**
     * How far ahead scheduled offers are listed.
     */
    public static final Duration UPCOMING = Duration.ofHours(24);

    private static final String PREFIX = "company.events.";

    private final CommandDependencies deps;
    private final OrderSearch search;
    private final ContestCommand contests;

    /**
     * @param deps     what the commands need
     * @param search   the order search of the company, which shows the chunks of a goal
     * @param contests the contest commands, which show a contest
     */
    public EventsCommand(CommandDependencies deps, OrderSearch search, ContestCommand contests) {
        this.deps = deps;
        this.search = search;
        this.contests = contests;
    }

    /**
     * Lists the event offers.
     *
     * @param sender sender of the command
     */
    @Executes
    public void list(CommandSender sender) {
        var player = Senders.player(sender);
        deps.requireReady();
        var offers = deps.services().offerData();
        CompletableFuture.supplyAsync(() -> listing(player, offers), Threading.VIRTUAL)
                .thenAccept(listing -> {
                    if (listing.offers().isEmpty()) {
                        deps.messages().sendMessage(player, PREFIX + "none");
                        return;
                    }
                    deps.messages()
                            .sendMessage(
                                    player, MenuText.create().text("<heading>").locale(PREFIX + "title"));
                    for (var offer : listing.offers()) {
                        deps.messages()
                                .sendMessage(
                                        player, line(offer, listing.places().get(offer.id())));
                    }
                })
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Could not list the event offers", err);
                    deps.messages().sendErrorActionBar(player, PREFIX + "error.failed");
                    return null;
                });
    }

    /**
     * Opens an event offer: the chunks of a goal in the order search, or the info of a contest.
     *
     * @param sender sender of the command
     * @param id     id of the offer
     */
    @Executes("open")
    public void open(CommandSender sender, @IntArg(min = 0) int id) {
        var player = Senders.player(sender);
        deps.requireReady();
        var offers = deps.services().offerData();
        CompletableFuture.supplyAsync(() -> offers.offer(id), Threading.VIRTUAL)
                .thenAccept(found -> {
                    if (found.isEmpty()) {
                        deps.messages().sendErrorActionBar(player, PREFIX + "error.unknown");
                        return;
                    }
                    if (found.get().kind() == OfferKind.CONTEST) {
                        contests.info(player, id);
                        return;
                    }
                    var company = deps.companyData()
                            .retrievePlayerCompanyProfile(player)
                            .asFuture()
                            .join();
                    var solo = deps.soloSearch();
                    var alone = solo.isPresent()
                            && alone(company != null && company.isPresent(), () -> deps.services()
                                    .soloOrders()
                                    .eligibility(player)
                                    .join()
                                    .isEmpty());
                    var target = alone ? solo.get() : search;
                    Threading.mainThread(deps.plugin()).execute(() -> {
                        if (player.isOnline()) target.showOffer(player, id);
                    });
                })
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Could not open the event offer " + id, err);
                    deps.messages().sendErrorActionBar(player, PREFIX + "error.failed");
                    return null;
                });
    }

    /**
     * Whether a goal opens in the search of orders to work on alone: for a player without a company who may work
     * alone. Everyone else gets the company search.
     *
     * @param hasCompany   whether the player is in a company
     * @param mayWorkAlone whether the player may take orders alone, asked only without a company
     * @return true for the search of orders to work on alone
     */
    static boolean alone(boolean hasCompany, BooleanSupplier mayWorkAlone) {
        return !hasCompany && mayWorkAlone.getAsBoolean();
    }

    private Listing listing(Player player, AOfferData offers) {
        var shown = shown(offers.running(), Times.now());
        var company = deps.companyData()
                .retrievePlayerCompanyProfile(player)
                .asFuture()
                .join();
        Map<Integer, OptionalInt> places = new HashMap<>();
        if (company != null && company.isPresent()) {
            for (var offer : shown) {
                if (offer.kind() != OfferKind.CONTEST) continue;
                places.put(
                        offer.id(),
                        place(offers.ranking(offer.id()), company.get().id()));
            }
        }
        return new Listing(shown, places);
    }

    /**
     * The offers a player sees: those running or paused, and those starting within {@link #UPCOMING}, the soonest end
     * first.
     *
     * @param running the offers that have not closed
     * @param now     the current time
     * @return the offers shown
     */
    public static List<EventOffer> shown(List<EventOffer> running, LocalDateTime now) {
        return running.stream()
                .filter(offer ->
                        offer.state() != OfferState.SCHEDULED || !offer.starts().isAfter(now.plus(UPCOMING)))
                .toList();
    }

    /**
     * @param ranking the places of a contest
     * @param company id of a company
     * @return the company's place among the companies that delivered, empty when it has none
     */
    public static OptionalInt place(List<ContestEntry> ranking, int company) {
        var place = 0;
        for (var entry : ranking) {
            if (entry.delivered() <= 0 || entry.name() == null) continue;
            place++;
            if (entry.company() == company) return OptionalInt.of(place);
        }
        return OptionalInt.empty();
    }

    private MenuText line(EventOffer offer, OptionalInt place) {
        var now = Times.now();
        var kind = offer.kind() == OfferKind.CONTEST ? "offers.kind.contest" : "offers.kind.goal";
        var text = MenuText.create()
                .text("<neutral>[<i18n:" + kind + ">]</neutral> ")
                .locale(
                        PREFIX + "entry",
                        Values.number("id", offer.id()),
                        Values.text("offer_name", offer.name()),
                        Values.text("item", offer.line().label()),
                        Values.number("delivered", offer.delivered()),
                        Values.number("amount", offer.amount()))
                .space()
                .text(Texts.progressBar(offer.amount() <= 0 ? 1 : (double) offer.delivered() / offer.amount()))
                .space();
        switch (offer.state()) {
            case SCHEDULED ->
                text.locale(PREFIX + "startsIn", Values.text("time", pretty(Duration.between(now, offer.starts()))));
            case PAUSED -> text.locale(PREFIX + "paused");
            default ->
                text.locale(PREFIX + "timeLeft", Values.text("time", pretty(Duration.between(now, offer.ends()))));
        }
        if (place != null && place.isPresent()) {
            text.space().locale(PREFIX + "place", Values.number("place", place.getAsInt()));
        }
        return text.text(
                " <act_open><show>[<i18n:" + PREFIX + "open>]</act_open>",
                Values.click("act_open", ClickEvent.runCommand("/company events open " + offer.id())));
    }

    private static String pretty(Duration duration) {
        return Texts.prettyDuration(duration.isNegative() ? Duration.ZERO : duration);
    }

    /**
     * What the list shows.
     *
     * @param offers the offers shown
     * @param places the place of the player's company in each contest shown, when it has one
     */
    private record Listing(List<EventOffer> offers, Map<Integer, OptionalInt> places) {}
}
