/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.demo;

import de.eldoria.companies.api.ApiNotReadyException;
import de.eldoria.companies.api.CompaniesApi;
import de.eldoria.companies.api.write.CancelOrderResult;
import de.eldoria.companies.api.write.CreateOrderResult;
import de.eldoria.companies.api.write.DisbandResult;
import de.eldoria.companies.api.write.FoundRequest;
import de.eldoria.companies.api.write.FoundResult;
import de.eldoria.companies.api.write.OrderRequest;
import de.eldoria.companies.components.join.JoinResult;
import de.eldoria.companies.components.order.IFullOrder;
import de.eldoria.companies.components.order.OrderState;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;

/**
 * The helper of the Companies demo network. On every server it offers {@code /demo} with the guide book, the admin
 * sandbox and the clean start {@code reset-me}, and, where {@code commands.enabled}, keeps visitors to the commands of
 * its allow list; payments above {@code pay.maxAmount} are refused. The server with {@code groups} sets up the
 * LuckPerms groups of the demo on each start, since the nightly reset empties their database.
 * <p>
 * The server whose config has {@code seed.enabled} creates the starting state of the demo after every reset, which
 * empties the Companies database. On each start it first writes the market history, which has no API, over a connection
 * to the Companies database of its own ({@code database}), so it lands before the plugin's own recording sweeps; then
 * it completes companies, members and orders through the API. Companies accepts writes only once its economy is set up,
 * so that part waits and tries again every few seconds; any other failure is logged and the next start completes what
 * is missing.
 */
public final class CompaniesDemo extends JavaPlugin {
    private static final long RETRY_TICKS = 20L * 10;
    private static final int ATTEMPTS = 30;
    private static final String DEFAULT_PAY_CAP = "1000";

    @Override
    public void onEnable() {
        saveDefaultConfig();
        var luckPerms = LuckPermsProvider.get();
        var messages = getConfig().getConfigurationSection("messages");
        var sandbox = new AdminSandbox(
                luckPerms,
                Duration.ofMinutes(getConfig().getInt("sandbox.minutes", 30)),
                Duration.ofMinutes(getConfig().getInt("sandbox.pauseMinutes", 0)));
        var sessions = new SandboxSessions(
                this,
                luckPerms,
                new SandboxLedger(getDataFolder().toPath().resolve("sandbox-players.yml"), getLogger()),
                Clock.systemUTC());
        sessions.listen();
        getServer().getPluginManager().registerEvents(sessions, this);
        var guideSection = getConfig().getConfigurationSection("guide");
        var guide = GuideBook.of(guideSection == null ? new MemoryConfiguration() : guideSection);
        getServer().getPluginManager().registerEvents(guide, this);
        var resetMe = new ResetMe(
                new ApiReset(),
                Clock.systemUTC(),
                Duration.ofMinutes(getConfig().getInt("reset.cooldownMinutes", 10)));
        var command = new DemoCommand(
                sandbox,
                sessions,
                guide,
                resetMe,
                task -> getServer().getScheduler().runTaskAsynchronously(this, task),
                messages == null ? new MemoryConfiguration() : messages,
                getLogger());
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar()
                .register("demo", "The Companies demo", command));
        if (getConfig().getBoolean("commands.enabled")) {
            getServer()
                    .getPluginManager()
                    .registerEvents(
                            new CommandAllowList(
                                    Set.copyOf(getConfig().getStringList("commands.allowed")),
                                    getConfig().getString("messages.refused", "")),
                            this);
        }
        getServer()
                .getPluginManager()
                .registerEvents(
                        new PayCap(
                                Set.copyOf(getConfig().getStringList("pay.commands")),
                                payCap(),
                                getConfig().getString("messages.pay", "")),
                        this);
        setUpGroups(new DemoGroups(luckPerms));
        if (!getConfig().getBoolean("seed.enabled")) return;
        var section = getConfig().getConfigurationSection("seed");
        var plan = SeedPlan.read(section == null ? new MemoryConfiguration() : section, getLogger()::warning);
        MariaDbMarket store;
        try {
            store = MariaDbMarket.of(
                    getConfig().getString("database.host", "localhost"),
                    getConfig().getInt("database.port", 3306),
                    getConfig().getString("database.database", "companies"),
                    getConfig().getString("database.user", "root"),
                    getConfig().getString("database.password", ""));
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "The database address of the market history is invalid", e);
            return;
        }
        var market = new MarketSeeder(store, Clock.systemUTC());
        getServer().getScheduler().runTaskAsynchronously(this, () -> seedMarket(market, plan));
        attempt(new Seeder(new ApiWrites(), getLogger()), plan, 1);
    }

    private BigDecimal payCap() {
        var text = Objects.requireNonNullElse(getConfig().getString("pay.maxAmount"), DEFAULT_PAY_CAP);
        var cap = PayCap.amount(text);
        if (cap != null) return cap;
        getLogger().warning("pay.maxAmount is no amount: " + text + "; using " + DEFAULT_PAY_CAP);
        return new BigDecimal(DEFAULT_PAY_CAP);
    }

    private void setUpGroups(DemoGroups groups) {
        var section = getConfig().getConfigurationSection("groups");
        if (section == null) return;
        for (var group : section.getKeys(false)) {
            groups.setUp(group, section.getStringList(group)).exceptionally(err -> {
                getLogger().log(Level.SEVERE, "Could not set up the group " + group, err);
                return null;
            });
        }
    }

    private void seedMarket(MarketSeeder market, SeedPlan plan) {
        try {
            var rows = market.seed(plan.market(), plan.marketDays());
            if (rows > 0) {
                getLogger()
                        .info("Seeded the market history: %d materials, %d rows."
                                .formatted(plan.market().size(), rows));
            }
        } catch (RuntimeException e) {
            getLogger().log(Level.SEVERE, "Could not seed the market history", e);
        }
    }

    private void attempt(Seeder seeder, SeedPlan plan, int attempt) {
        getServer()
                .getScheduler()
                .runTaskLaterAsynchronously(
                        this,
                        () -> {
                            try {
                                var created = seeder.seed(plan);
                                if (created.companies() + created.members() + created.orders() > 0) {
                                    getLogger()
                                            .info("Seeded %d companies, %d members and %d orders."
                                                    .formatted(
                                                            created.companies(), created.members(), created.orders()));
                                }
                            } catch (RuntimeException e) {
                                var cause = e instanceof CompletionException && e.getCause() != null ? e.getCause() : e;
                                if (cause instanceof ApiNotReadyException && attempt < ATTEMPTS) {
                                    attempt(seeder, plan, attempt + 1);
                                    return;
                                }
                                getLogger()
                                        .log(
                                                Level.SEVERE,
                                                "Could not seed the demo; the next start completes it",
                                                cause);
                            }
                        },
                        RETRY_TICKS);
    }

    /**
     * The writes of the seed through the registered Companies API.
     */
    private static final class ApiWrites implements Seeder.Writes {
        @Override
        public OptionalInt companyId(String name) {
            return CompaniesApi.get()
                    .companies()
                    .byName(name)
                    .join()
                    .map(company -> OptionalInt.of(company.id()))
                    .orElse(OptionalInt.empty());
        }

        @Override
        public FoundResult found(FoundRequest request) {
            return CompaniesApi.get().companies().found(request).join();
        }

        @Override
        public JoinResult addMember(int company, UUID player) {
            return CompaniesApi.get().companies().addMember(company, player).join();
        }

        @Override
        public boolean hasOrder(UUID owner, String name) {
            return CompaniesApi.get().orders().byOwner(owner, OrderState.UNCLAIMED, OrderState.RECEIVED).join().stream()
                    .anyMatch(order -> order.name().equals(name));
        }

        @Override
        public Optional<String> createOrder(OrderRequest request) {
            return switch (CompaniesApi.get().orders().create(request).join()) {
                case CreateOrderResult.Created created -> Optional.empty();
                case CreateOrderResult.Rejected rejected ->
                    Optional.of(rejected.reason().name());
            };
        }
    }

    /**
     * The reads and writes of {@code /demo reset-me} through the registered Companies API.
     */
    private static final class ApiReset implements ResetMe.Writes {
        @Override
        public Optional<ResetMe.Company> company(UUID player) {
            return CompaniesApi.get()
                    .companies()
                    .of(player)
                    .join()
                    .map(company -> new ResetMe.Company(
                            company.id(),
                            company.name(),
                            company.owner().uuid().equals(player),
                            company.members().size()));
        }

        @Override
        public List<Integer> openOrders(UUID player) {
            return CompaniesApi.get()
                    .orders()
                    .byOwner(player, OrderState.UNCLAIMED, OrderState.UNCLAIMED)
                    .join()
                    .stream()
                    .map(IFullOrder::id)
                    .toList();
        }

        @Override
        public int takenOrders(UUID player) {
            return CompaniesApi.get()
                    .orders()
                    .byOwner(player, OrderState.CLAIMED, OrderState.DELIVERED)
                    .join()
                    .size();
        }

        @Override
        public DisbandResult disband(int company, UUID player) {
            return CompaniesApi.get().companies().disband(company, player).join();
        }

        @Override
        public CancelOrderResult cancel(int order, UUID player) {
            return CompaniesApi.get().orders().cancel(order, player).join();
        }
    }
}
