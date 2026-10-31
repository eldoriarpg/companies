/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company;

import de.eldoria.companies.areas.AreaPrices;
import de.eldoria.companies.areas.AreaRef;
import de.eldoria.companies.areas.AreaServices;
import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.components.area.AreaCapability;
import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.company.ICompanyMember;
import de.eldoria.companies.components.company.TreasuryKind;
import de.eldoria.companies.data.repository.AAreaData;
import de.eldoria.companies.data.wrapper.area.AreaOffer;
import de.eldoria.companies.data.wrapper.area.AreaSource;
import de.eldoria.companies.data.wrapper.area.CompanyArea;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.menus.MenuComponents;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.services.TreasuryService;
import de.eldoria.companies.util.Money;
import de.eldoria.companies.util.Texts;
import de.eldoria.companies.util.Threading;
import de.eldoria.companies.util.Times;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.arguments.IntArg;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * {@code /company area}: the areas of the player's company, plots and regions where its members build with
 * {@code BUILD} and use containers with {@code CONTAINERS}.
 * <ul>
 *     <li>{@code /company area} lists them;</li>
 *     <li>{@code link} links the plot or region the player stands on and owns, needs {@code manage_areas}; it stays
 *     the player's, and the company loses it when they leave the company or give it up;</li>
 *     <li>{@code unlink [id]} unlinks the area the player stands on or the one with the id, needs {@code manage_areas}
 *     or being the member who linked it;</li>
 *     <li>{@code market} lists the areas the server offers;</li>
 *     <li>{@code buy <offer>} buys or rents an offered area from the treasury, needs {@code manage_areas} and being on
 *     the area's server; the owner of the company becomes its owner;</li>
 *     <li>{@code cancel <id>} lets a rent run out at the end of the period paid, or sells a bought area back for
 *     {@code sellBackPercent} of its price, needs {@code manage_areas}.</li>
 * </ul>
 * A company has at most the {@code maxAreas} of its level.
 * The database is read off the main thread; the answers, and the names the area plugins give, come on the main thread.
 */
@Subcommand("area")
@PlayerOnly
public final class CompanyAreaCommand {
    private static final String PREFIX = "company.area.";

    private final CommandDependencies deps;
    private final Object linking = new Object();

    /**
     * @param deps what the commands need
     */
    public CompanyAreaCommand(CommandDependencies deps) {
        this.deps = deps;
    }

    /**
     * Lists the areas of the player's company.
     *
     * @param sender sender of the command
     */
    @Executes
    public void list(CommandSender sender) {
        var player = Senders.player(sender);
        run(player, () -> areas().areas().ofCompany(company(player).id()), found -> {
            if (found.isEmpty()) {
                deps.messages().sendMessage(player, PREFIX + "none");
                return;
            }
            deps.messages().sendMessage(player, PREFIX + "title");
            for (var area : found) {
                var available = areas().providers().available(area.provider());
                deps.messages()
                        .sendMessage(
                                player,
                                PREFIX + (available ? "entry" : "entryUnavailable"),
                                Values.number("id", area.id()),
                                Values.text(
                                        "area",
                                        areas().providers()
                                                .call(
                                                        area.provider(),
                                                        provider -> provider.name(area.ref()),
                                                        area.ref())),
                                Values.text("provider", area.provider()),
                                Values.text("world", area.world()),
                                Values.component(
                                        "source",
                                        new MenuComponents(deps.messages(), player)
                                                .locale("company.area.source."
                                                        + area.source().name().toLowerCase(Locale.ROOT))));
                if (area.source() == AreaSource.RENTED) rent(player, area);
            }
        });
    }

    private void rent(Player player, CompanyArea area) {
        var until = area.rentUntil();
        var left = until == null ? Duration.ZERO : Duration.between(Times.now(), until);
        var key = area.rentEnded() ? "rent.ended" : area.renew() ? "rent.renewing" : "rent.ending";
        deps.messages()
                .sendMessage(
                        player,
                        PREFIX + key,
                        Values.text("amount", money(area.price())),
                        Values.text("duration", Texts.prettyDuration(left.isNegative() ? Duration.ZERO : left)));
    }

    /**
     * Lists the areas the server offers to buy or rent.
     *
     * @param sender sender of the command
     */
    @Executes("market")
    public void market(CommandSender sender) {
        var player = Senders.player(sender);
        run(player, () -> areas().areas().openOffers(), found -> {
            if (found.isEmpty()) {
                deps.messages().sendMessage(player, PREFIX + "market.none");
                return;
            }
            deps.messages().sendMessage(player, PREFIX + "market.title");
            for (var offer : found) {
                deps.messages()
                        .sendMessage(
                                player,
                                PREFIX + "market." + offer.kind().name().toLowerCase(Locale.ROOT),
                                Values.number("id", offer.id()),
                                Values.text("area", name(offer.provider(), offer.ref())),
                                Values.text("world", offer.world()),
                                Values.text("amount", money(offer.price())),
                                Values.number("days", offer.periodDays()));
            }
        });
    }

    /**
     * Buys or rents an offered area for the player's company, paid from the treasury.
     *
     * @param sender sender of the command
     * @param id     id of the offer
     */
    @Executes("buy")
    public void buy(CommandSender sender, @IntArg(min = 1) int id) {
        var player = Senders.player(sender);
        run(player, () -> bought(player, id), area -> {
            areas().sync().checkSoon();
            deps.messages()
                    .sendMessage(
                            player,
                            PREFIX + (area.source() == AreaSource.RENTED ? "rented" : "bought"),
                            Values.number("id", area.id()),
                            Values.text("area", name(area.provider(), area.ref())),
                            Values.text("amount", money(area.price())));
        });
    }

    /**
     * Cancels the rent of an area or sells a bought area back to the server.
     *
     * @param sender sender of the command
     * @param id     id of the area
     */
    @Executes("cancel")
    public void cancel(CommandSender sender, @IntArg(min = 1) int id) {
        var player = Senders.player(sender);
        run(player, () -> cancelled(player, id), result -> deps.messages()
                .sendMessage(
                        player,
                        PREFIX + (result.sold() ? "sold" : "rentCancelled"),
                        Values.number("id", id),
                        Values.text(
                                "amount", money(Money.fromCents(result.cents()).doubleValue()))));
    }

    /**
     * What cancelling an area did.
     *
     * @param sold  whether a bought area was sold back, else a rent stops renewing
     * @param cents what the treasury got back in cents
     */
    private record Cancelled(boolean sold, long cents) {}

    private Cancelled cancelled(Player player, int id) {
        var company = company(player);
        requireManage(company, player);
        var area = areas().areas()
                .area(id)
                .filter(found -> found.company() == company.id())
                .orElseThrow(() -> CommandFailure.of(PREFIX + "error.notYours"));
        if (area.source() == AreaSource.LINKED) throw CommandFailure.of(PREFIX + "error.linkedCancel");
        if (area.source() == AreaSource.RENTED) {
            if (area.rentEnded() || !areas().areas().stopRenewing(area.id())) {
                throw CommandFailure.of(PREFIX + "error.notRenewing");
            }
            return new Cancelled(false, 0);
        }
        if (!area.node().equals(areas().node())) {
            throw CommandFailure.of(PREFIX + "error.otherServer", Values.text("world", area.world()));
        }
        var cents =
                AreaPrices.sellBack(area, deps.configuration().areaSettings().sellBackPercent());
        if (!areas().sync().giveBack(area)) throw CommandFailure.of(PREFIX + "error.failed");
        if (cents <= 0) return new Cancelled(true, 0);
        var paid = deps.services()
                .treasury()
                .sale(company, player.getUniqueId(), cents, "#" + area.id())
                .join();
        if (!paid.done()) throw CommandFailure.of(PREFIX + "error.saleUnpaid", Values.number("id", area.id()));
        return new Cancelled(true, cents);
    }

    private CompanyArea bought(Player player, int id) {
        var settings = deps.configuration().areaSettings();
        if (!settings.enabled()) throw CommandFailure.of(PREFIX + "error.disabled");
        var company = company(player);
        requireManage(company, player);
        var store = areas().areas();
        var offer = store.openOffers().stream()
                .filter(open -> open.id() == id)
                .findFirst()
                .orElseThrow(() -> CommandFailure.of(PREFIX + "error.noOffer"));
        if (!offer.node().equals(areas().node())) {
            throw CommandFailure.of(PREFIX + "error.otherServer", Values.text("world", offer.world()));
        }
        var owner = company.members().stream()
                .filter(member -> member.hasPermission(CompanyPermission.OWNER))
                .map(ICompanyMember::uuid)
                .findFirst()
                .orElseThrow(() -> CommandFailure.of(PREFIX + "error.failed"));
        var now = Times.now();
        var area = new CompanyArea(
                0,
                company.id(),
                offer.provider(),
                offer.ref(),
                offer.world(),
                offer.node(),
                offer.kind().source(),
                null,
                offer.price(),
                offer.kind() == AreaOffer.Kind.RENT ? now.plusDays(offer.periodDays()) : null,
                now,
                offer.id(),
                true,
                false,
                false);
        synchronized (linking) {
            var max = deps.configuration()
                    .companySettings()
                    .levelSettings(company.level())
                    .maxAreas();
            if (store.ofCompany(company.id()).size() >= max) {
                throw CommandFailure.of(PREFIX + "error.limit", Values.number("max", max));
            }
            var stored = new AtomicReference<CompanyArea>();
            Supplier<CompletableFuture<Boolean>> purchase =
                    () -> CompletableFuture.supplyAsync(() -> claim(area, owner, stored), Threading.VIRTUAL);
            if (offer.price() <= 0) {
                if (!purchase.get().join()) throw CommandFailure.of(PREFIX + "error.taken");
                return stored.get();
            }
            var kind = offer.kind() == AreaOffer.Kind.RENT ? TreasuryKind.AREA_RENT : TreasuryKind.AREA_BUY;
            var treasury = deps.services().treasury();
            var result = treasury.spend(
                            company,
                            player.getUniqueId(),
                            BigDecimal.valueOf(offer.price()),
                            kind,
                            "#" + offer.id(),
                            purchase)
                    .join();
            if (result.done()) return stored.get();
            if (result.outcome() == TreasuryService.Outcome.PURCHASE_FAILED) {
                throw CommandFailure.of(PREFIX + "error.taken");
            }
            throw treasury.explain(result, Money.currencyName(deps.services().economy()))
                    .failure();
        }
    }

    /**
     * Stores an area the company buys or rents, which claims it, then makes the owner of the company its owner on the
     * main thread. When that fails, the area is dropped again.
     */
    private boolean claim(CompanyArea area, UUID owner, AtomicReference<CompanyArea> stored) {
        var id = areas().areas().add(area);
        if (id.isEmpty()) return false;
        var providers = areas().providers();
        var moved = CompletableFuture.supplyAsync(
                        () -> providers.call(
                                area.provider(),
                                provider -> provider.capabilities().contains(AreaCapability.TRANSFER)
                                        && provider.exists(area.ref())
                                        && provider.transferTo(area.ref(), owner),
                                false),
                        Threading.mainThread(deps.plugin()))
                .join();
        if (!moved) {
            areas().areas().remove(id.get());
            return false;
        }
        stored.set(new CompanyArea(
                id.get(),
                area.company(),
                area.provider(),
                area.ref(),
                area.world(),
                area.node(),
                area.source(),
                area.linkedBy(),
                area.price(),
                area.rentUntil(),
                area.created(),
                area.offer(),
                area.renew(),
                area.rentWarned(),
                area.rentEnded()));
        return true;
    }

    private void requireManage(CompanyProfile company, Player player) {
        var member = company.member(player.getUniqueId());
        if (member.isEmpty() || !member.get().hasPermission(CompanyPermission.MANAGE_AREAS)) {
            throw CommandFailure.of(PREFIX + "error.manage");
        }
    }

    private String name(String provider, String ref) {
        return areas().providers().call(provider, found -> found.name(ref), ref);
    }

    private String money(double amount) {
        return deps.services().treasury().format(BigDecimal.valueOf(amount));
    }

    /**
     * Links the plot or region the player stands on and owns to their company.
     *
     * @param sender sender of the command
     */
    @Executes("link")
    public void link(CommandSender sender) {
        var player = Senders.player(sender);
        var providers = areas().providers();
        var here = providers.areasAt(player.getLocation());
        if (here.isEmpty()) {
            deps.messages().sendError(player, PREFIX + "error.noArea");
            return;
        }
        var linkable = here.stream()
                .filter(area -> providers.call(
                        area.provider(),
                        provider -> provider.capabilities().contains(AreaCapability.LINK_OWNED),
                        false))
                .toList();
        if (linkable.isEmpty()) {
            deps.messages().sendError(player, PREFIX + "error.notLinkable");
            return;
        }
        var uuid = player.getUniqueId();
        var owned = linkable.stream()
                .filter(area -> providers.call(area.provider(), provider -> provider.isOwner(uuid, area.ref()), false))
                .findFirst();
        if (owned.isEmpty()) {
            deps.messages().sendError(player, PREFIX + "error.notOwner");
            return;
        }
        var ref = owned.get();
        var name = providers.call(ref.provider(), provider -> provider.name(ref.ref()), ref.ref());
        var world = player.getWorld().getName();
        run(player, () -> linked(player, ref, world), id -> {
            areas().sync().checkSoon();
            deps.messages().sendMessage(player, PREFIX + "linked", Values.number("id", id), Values.text("area", name));
        });
    }

    /**
     * Unlinks the area of the player's company the player stands on, the first of them when there are several.
     *
     * @param sender sender of the command
     */
    @Executes("unlink")
    public void unlinkHere(CommandSender sender) {
        var player = Senders.player(sender);
        var here = areas().providers().areasAt(player.getLocation());
        if (here.isEmpty()) {
            deps.messages().sendError(player, PREFIX + "error.noArea");
            return;
        }
        unlink(player, () -> {
            var company = company(player).id();
            return here.stream()
                    .flatMap(ref -> areas().areas().byRef(ref.provider(), ref.ref()).stream())
                    .filter(area -> area.company() == company)
                    .findFirst()
                    .orElseThrow(() -> CommandFailure.of(PREFIX + "error.notYours"));
        });
    }

    /**
     * Unlinks an area of the player's company by its id.
     *
     * @param sender sender of the command
     * @param id     id of the area
     */
    @Executes("unlink")
    public void unlink(CommandSender sender, @IntArg(min = 1) int id) {
        var player = Senders.player(sender);
        unlink(player, () -> areas().areas().area(id).orElseThrow(() -> CommandFailure.of(PREFIX + "error.notYours")));
    }

    private void unlink(Player player, Supplier<CompanyArea> area) {
        run(
                player,
                () -> {
                    var company = company(player);
                    var found = area.get();
                    if (found.company() != company.id()) throw CommandFailure.of(PREFIX + "error.notYours");
                    if (found.source() != AreaSource.LINKED) throw CommandFailure.of(PREFIX + "error.notLinked");
                    if (!found.node().equals(areas().node())) {
                        throw CommandFailure.of(PREFIX + "error.otherServer", Values.text("world", found.world()));
                    }
                    var member = company.member(player.getUniqueId());
                    var mayManage = member.isPresent() && member.get().hasPermission(CompanyPermission.MANAGE_AREAS);
                    if (!mayManage && !player.getUniqueId().equals(found.linkedBy())) {
                        throw CommandFailure.of(PREFIX + "error.manage");
                    }
                    if (!areas().sync().unlink(found)) throw CommandFailure.of(PREFIX + "error.failed");
                    return found;
                },
                found -> deps.messages().sendMessage(player, PREFIX + "unlinked", Values.number("id", found.id())));
    }

    /**
     * Links an area after the checks that need the database. Counting the company's areas and adding one hold a lock,
     * so two links on this server at once can not pass the limit together.
     */
    private int linked(Player player, AreaRef ref, String world) {
        var settings = deps.configuration().areaSettings();
        if (!settings.enabled()) throw CommandFailure.of(PREFIX + "error.disabled");
        var company = company(player);
        requireManage(company, player);
        var max = deps.configuration()
                .companySettings()
                .levelSettings(company.level())
                .maxAreas();
        var store = areas().areas();
        synchronized (linking) {
            return add(store, player, company, max, ref, world);
        }
    }

    private int add(AAreaData store, Player player, CompanyProfile company, int max, AreaRef ref, String world) {
        if (store.ofCompany(company.id()).size() >= max) {
            throw CommandFailure.of(PREFIX + "error.limit", Values.number("max", max));
        }
        if (store.byRef(ref.provider(), ref.ref()).isPresent()) throw CommandFailure.of(PREFIX + "error.taken");
        var area = new CompanyArea(
                0,
                company.id(),
                ref.provider(),
                ref.ref(),
                world,
                areas().node(),
                AreaSource.LINKED,
                player.getUniqueId(),
                0,
                null,
                Times.now());
        return store.add(area).orElseThrow(() -> CommandFailure.of(PREFIX + "error.taken"));
    }

    private CompanyProfile company(Player player) {
        var found = deps.companyData()
                .retrievePlayerCompanyProfile(player)
                .asFuture()
                .join();
        return Optional.ofNullable(found)
                .flatMap(profile -> profile)
                .orElseThrow(() -> CommandFailure.of(PREFIX + "error.noCompany"));
    }

    private AreaServices areas() {
        return deps.services().areas();
    }

    private <T> void run(Player player, Supplier<T> query, Consumer<T> answer) {
        deps.requireReady();
        CompletableFuture.supplyAsync(query, Threading.VIRTUAL)
                .thenAcceptAsync(answer, Threading.mainThread(deps.plugin()))
                .exceptionally(err -> {
                    if (err.getCause() instanceof CommandFailure failure) {
                        failure.sendTo(player, deps.messages());
                        return null;
                    }
                    deps.plugin().getLogger().log(Level.SEVERE, "Could not read or change a company area", err);
                    deps.messages().sendErrorActionBar(player, PREFIX + "error.failed");
                    return null;
                });
    }
}
