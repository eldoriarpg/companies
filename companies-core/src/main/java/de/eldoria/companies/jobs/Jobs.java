/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs;

import de.eldoria.companies.areas.AreaProviders;
import de.eldoria.companies.components.job.JobKind;
import de.eldoria.companies.components.job.JobState;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AJobData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.job.Job;
import de.eldoria.companies.events.company.CompanyDisbandEvent;
import de.eldoria.companies.jobs.hooks.JobHooks;
import de.eldoria.companies.jobs.hooks.ProviderJobLand;
import de.eldoria.companies.menus.MenuComponents;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.orders.Collector;
import de.eldoria.companies.orders.FeeReductions;
import de.eldoria.companies.services.LevelService;
import de.eldoria.companies.services.TreasuryService;
import de.eldoria.companies.services.events.ActiveEvents;
import de.eldoria.companies.services.events.EventEffects;
import de.eldoria.companies.services.events.EventTimes;
import de.eldoria.companies.services.notifications.NotificationArgs;
import de.eldoria.companies.services.notifications.NotificationService;
import de.eldoria.companies.services.notifications.NotificationType;
import de.eldoria.companies.util.Texts;
import de.eldoria.companies.util.Threading;
import de.eldoria.companies.util.Times;
import de.eldoria.eldoutilities.messages.MessageSender;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Builds the jobs of a running plugin: the land and selection hooks, the service with money through Vault on the main
 * thread and notices through the inbox, and the access of this server's areas.
 */
public final class Jobs {
    /**
     * How often the primary expires jobs and opens overdue ones again, in minutes.
     */
    public static final int CHECK_MINUTES = 1;

    private static final long GHOST_PRUNE_TICKS = 100;

    private Jobs() {}

    /**
     * @param plugin        the plugin
     * @param configuration the settings
     * @param jobs          job repository
     * @param companies     company repository
     * @param orders        order repository
     * @param economy       Vault economy
     * @param treasuries    the company treasuries
     * @param notifications sends notices to the inbox
     * @param levels        recalculates the level of a company after a penalty
     * @param events        the server events this node sees, for their tax reductions
     * @param fees          the fee reductions of the server events running now
     * @param areaProviders the area providers, whose land jobs may lie on
     * @return the jobs, hooks loaded, nothing scheduled yet
     */
    public static JobServices create(
            Plugin plugin,
            Configuration configuration,
            AJobData jobs,
            ACompanyData companies,
            AOrderData orders,
            Economy economy,
            TreasuryService treasuries,
            NotificationService notifications,
            LevelService levels,
            ActiveEvents events,
            FeeReductions fees,
            AreaProviders areaProviders) {
        var hooks = JobHooks.create(plugin, name -> configuration.hooks().land(name));
        hooks.load();
        hooks.add(new ProviderJobLand(areaProviders));
        var mainThread = Threading.mainThread(plugin);
        var collector = new Collector(plugin, economy, configuration);
        var money = new JobService.Money() {
            @Override
            public boolean withdraw(UUID player, double amount) {
                var offline = plugin.getServer().getOfflinePlayer(player);
                return CompletableFuture.supplyAsync(
                                () -> economy.has(offline, amount)
                                        && economy.withdrawPlayer(offline, amount)
                                                .transactionSuccess(),
                                mainThread)
                        .join();
            }

            @Override
            public void deposit(UUID player, double amount) {
                if (!(amount > 0)) return;
                var offline = plugin.getServer().getOfflinePlayer(player);
                Runnable pay = () -> {
                    var response = economy.depositPlayer(offline, amount);
                    if (!response.transactionSuccess()) {
                        plugin.getLogger()
                                .severe("The economy refused to pay %s to %s: %s; pay it by hand"
                                        .formatted(amount, player, response.errorMessage));
                    }
                };
                try {
                    mainThread.execute(pay);
                } catch (RuntimeException e) {
                    pay.run();
                }
            }

            @Override
            public void collect(double amount) {
                if (amount > 0) collector.collect(amount);
            }

            @Override
            public double taxPercent(CompanyProfile company) {
                var base = configuration.companySettings().taxPercent(company.level());
                var running = events.matching(Times.now(), List.of(), OptionalInt.of(company.level()));
                return EventEffects.taxPercent(base, running);
            }

            @Override
            public int share(CompanyProfile company) {
                return treasuries.effectiveShare(company.id()).join();
            }

            @Override
            public void keep(CompanyProfile company, int job, long cents) {
                treasuries.keepJobShare(company, job, cents);
            }
        };
        var notices = new JobService.Notices() {
            @Override
            public void owner(UUID owner, JobService.Notice notice, Job job, @Nullable Object detail) {
                var args = args(job);
                var type =
                        switch (notice) {
                            case TAKEN -> {
                                if (detail instanceof LocalDateTime due)
                                    args.text("due", EventTimes.show(due, Times.zone()));
                                yield NotificationType.MY_JOB_TAKEN;
                            }
                            case RELEASED -> NotificationType.MY_JOB_RELEASED;
                            case DONE -> NotificationType.MY_JOB_DONE;
                            case EXPIRED -> {
                                if (detail instanceof Double amount) args.money("amount", economy.format(amount));
                                yield NotificationType.MY_JOB_EXPIRED;
                            }
                            case LAND_LOST -> {
                                if (!(detail instanceof JobService.Worked worked))
                                    yield NotificationType.MY_JOB_LAND_LOST;
                                worked(args, worked, worked.back());
                                yield NotificationType.MY_JOB_LAND_LOST_WORKED;
                            }
                            case AWAITING -> {
                                if (detail instanceof LocalDateTime at)
                                    args.text("time", EventTimes.show(at, Times.zone()));
                                yield NotificationType.MY_JOB_AWAITING;
                            }
                            case AUTO_APPROVED -> NotificationType.MY_JOB_AUTO_APPROVED;
                            case DISPUTED -> {
                                if (detail instanceof String reason) args.text("reason", reason);
                                yield NotificationType.MY_JOB_DISPUTED;
                            }
                            case WITHDRAWN -> NotificationType.MY_JOB_WITHDRAWN;
                            case HARVEST_ENDED -> {
                                if (detail instanceof JobService.Harvested harvested) {
                                    args.money("amount", economy.format(harvested.back()))
                                            .number("items", harvested.stocked());
                                }
                                yield NotificationType.MY_JOB_HARVEST_ENDED;
                            }
                            case SETTLED -> {
                                if (detail instanceof Double amount) args.money("amount", economy.format(amount));
                                yield NotificationType.MY_JOB_SETTLED;
                            }
                            case EXTENSION_ASKED -> {
                                if (detail instanceof JobService.Asked asked) {
                                    args.number("hours", asked.hours())
                                            .text("reason", asked.reason())
                                            .text("duration", Texts.prettyDuration(asked.left()));
                                }
                                yield NotificationType.MY_JOB_EXTENSION_ASKED;
                            }
                            case CANCEL_ACCEPTED -> {
                                if (detail instanceof Double amount) args.money("amount", economy.format(amount));
                                yield NotificationType.MY_JOB_CANCEL_ACCEPTED;
                            }
                            case CANCEL_REFUSED -> NotificationType.MY_JOB_CANCEL_REFUSED;
                        };
                if (type != null) notifications.send(owner, job.charged() ? type : uncharged(type), args);
            }

            @Override
            public void paid(CompanyProfile company, Job job, double paid) {
                notifications.sendCompany(
                        company, NotificationType.JOB_PAID, args(job).money("amount", economy.format(paid)));
            }

            @Override
            public void extended(int company, Job job, int hours, LocalDateTime due) {
                profile(companies, company)
                        .ifPresent(found -> notifications.sendCompany(
                                found,
                                NotificationType.JOB_EXTENDED,
                                args(job).number("hours", hours).text("due", EventTimes.show(due, Times.zone()))));
            }

            @Override
            public void extensionRefused(int company, Job job, @Nullable String reason) {
                profile(companies, company)
                        .ifPresent(found -> notifications.sendCompany(
                                found,
                                NotificationType.JOB_EXTENSION_REFUSED,
                                args(job).text("reason", reason == null ? "" : reason)));
            }

            @Override
            public void cancelAsked(int company, Job job) {
                profile(companies, company)
                        .ifPresent(found ->
                                notifications.sendCompany(found, NotificationType.JOB_CANCEL_ASKED, args(job)));
            }

            @Override
            public void overdue(int company, Job job) {
                profile(companies, company)
                        .ifPresent(found -> notifications.sendCompany(found, NotificationType.JOB_OVERDUE, args(job)));
            }

            @Override
            public void rejected(int company, Job job, String reason) {
                profile(companies, company)
                        .ifPresent(found -> notifications.sendCompany(
                                found, NotificationType.JOB_REJECTED, args(job).text("reason", reason)));
            }

            @Override
            public void disputed(int company, Job job, String reason) {
                profile(companies, company)
                        .ifPresent(found -> notifications.sendCompany(
                                found, NotificationType.JOB_DISPUTED, args(job).text("reason", reason)));
            }

            @Override
            public void settled(int company, Job job, double paid) {
                profile(companies, company)
                        .ifPresent(found -> notifications.sendCompany(
                                found, NotificationType.JOB_SETTLED, args(job).money("amount", economy.format(paid))));
            }

            @Override
            public void withdrawn(int company, Job job) {
                profile(companies, company)
                        .ifPresent(
                                found -> notifications.sendCompany(found, NotificationType.JOB_WITHDRAWN, args(job)));
            }

            @Override
            public void landLost(int company, Job job, JobService.@Nullable Worked worked) {
                var args = args(job);
                if (worked != null) worked(args, worked, worked.paid());
                var type = worked == null ? NotificationType.JOB_LAND_LOST : NotificationType.JOB_LAND_LOST_WORKED;
                profile(companies, company).ifPresent(found -> notifications.sendCompany(found, type, args));
            }

            private void worked(NotificationArgs args, JobService.Worked worked, double amount) {
                args.number("percent", worked.percent())
                        .number("blocks", worked.left())
                        .money("amount", economy.format(amount));
            }
        };
        JobService.Penalties penalties =
                (company, overdue) -> profile(companies, company).ifPresent(found -> {
                    var settings = configuration.companySettings();
                    var penalty = overdue ? settings.expiredOrderPenalty() : settings.abortedOrderPenalty();
                    companies
                            .submitFailedOrder(found, penalty)
                            .asFuture()
                            .thenRun(() -> levels.updateCompanyLevel(found))
                            .exceptionally(err -> {
                                plugin.getLogger()
                                        .log(
                                                Level.SEVERE,
                                                "Could not apply the job penalty of company " + company,
                                                err);
                                return null;
                            });
                });
        var service = new JobService(
                jobs,
                orders,
                money,
                notices,
                penalties,
                company -> profile(companies, company),
                configuration::jobSettings,
                price -> fees.fee(configuration.orderSetting().fee().of(price), List.of()),
                company -> configuration
                        .companySettings()
                        .levelSettings(company.level())
                        .maxOrders(),
                Times.UTC,
                plugin.getLogger());
        service.events(new JobApiEvents(
                plugin.getServer().getPluginManager()::callEvent, plugin.getServer()::getOfflinePlayer));
        var node = configuration.nodeSettings().nodeUid();
        var blueprints = new Blueprints(jobs, plugin.getLogger());
        var progress = new JobProgress(
                plugin, node, blueprints, () -> configuration.jobSettings().buildMatchPercent());
        var access = new JobAccess(
                jobs,
                company -> members(companies, company),
                hooks,
                node,
                mainThread,
                job -> service.landLost(
                        job,
                        job.state() == JobState.TAKEN && job.kind() != JobKind.HARVEST ? checked(progress, job) : null),
                job -> progress.of(job, true)
                        .copy()
                        .orTimeout(JobAccess.MAIN_THREAD_SECONDS * 3L, TimeUnit.SECONDS)
                        .thenAcceptAsync(
                                found -> found.ifPresent(scan -> jobs.airAtPost(job.id(), scan.cleared())),
                                Threading.VIRTUAL),
                service::harvested,
                blueprints,
                plugin.getLogger());
        plugin.getServer().getPluginManager().registerEvents(access, plugin);
        plugin.getServer().getPluginManager().registerEvents(new Disbands(service, access, plugin), plugin);
        var ghosts = new BuildGhosts(plugin);
        plugin.getServer().getPluginManager().registerEvents(ghosts, plugin);
        var costs = new MaterialCosts(blueprints, type -> price(orders, type, plugin));
        var supplies = new Supplies(jobs, blueprints);
        service.supplies(supplies);
        var barTexts = MessageSender.getPluginMessageSender(plugin);
        var magicItems = new MagicItems(plugin);
        var stocks = new JobStocks(jobs, plugin.getLogger());
        var magic = new MagicBuilding(plugin, access, blueprints, stocks, magicItems, ghosts, barTexts);
        plugin.getServer().getPluginManager().registerEvents(magic, plugin);
        service.closing(stocks::close);
        var dug = new DugStocks(plugin.getServer(), stocks);
        plugin.getServer().getPluginManager().registerEvents(dug, plugin);
        access.digging(dug::dug);
        stocks.late(service::returnLate);
        progress.counted(access::counted);
        ghosts.counted(access::counted);
        var bars = new ProgressBars(
                plugin,
                access::active,
                job -> progress.of(job, false),
                mainThread,
                (player, job, work) -> new MenuComponents(barTexts, player)
                        .locale(
                                "jobs.progressBar",
                                Values.number("id", job.id()),
                                Values.text("job_title", job.title()),
                                Values.number("percent", work.percent())));
        plugin.getServer().getPluginManager().registerEvents(bars, plugin);
        var uploads = new SchematicUploads(jobs, hooks, configuration::jobSettings, Times::now, plugin.getLogger());
        var areas = new StoredAreas(jobs, configuration::jobSettings, Times::now);
        var tool = new AreaTool(plugin, areas, barTexts);
        plugin.getServer().getPluginManager().registerEvents(tool, plugin);
        var checks = new AreaChecks(plugin.getServer(), hooks, configuration::jobSettings, mainThread);
        var schematics = new BuildSchematics(
                plugin.getDataFolder().toPath().resolve("schematics"), uploads, hooks, plugin.getLogger());
        return new JobServices(
                jobs,
                service,
                hooks,
                access,
                progress,
                node,
                blueprints,
                ghosts,
                costs,
                bars,
                uploads,
                areas,
                tool,
                checks,
                schematics,
                new AreaWorld(plugin.getServer(), mainThread),
                supplies,
                magicItems,
                stocks,
                magic);
    }

    private static double price(AOrderData orders, Material type, Plugin plugin) {
        try {
            return orders.retrieveMaterialPrice(type.name()).asFuture().join().price();
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Could not read the price of " + type, e);
            return 0;
        }
    }

    /**
     * Schedules the access of this server's areas, the progress bars, the removal of ghost blocks and last progress of
     * jobs that ended and, on the primary, the check that expires jobs.
     *
     * @param services the jobs
     * @param primary  whether this server is the primary
     * @param executor runs the checks
     * @param plugin   the plugin, for the log
     */
    public static void schedule(
            JobServices services, boolean primary, ScheduledExecutorService executor, Plugin plugin) {
        services.access().schedule(executor);
        services.magic().schedule();
        plugin.getServer()
                .getScheduler()
                .runTaskTimer(plugin, services.ghosts()::follow, BuildGhosts.FOLLOW_TICKS, BuildGhosts.FOLLOW_TICKS);
        plugin.getServer()
                .getScheduler()
                .runTaskTimer(
                        plugin,
                        () -> {
                            services.ghosts().prune(services.access()::working);
                            services.progress().prune(services.access()::working);
                        },
                        GHOST_PRUNE_TICKS,
                        GHOST_PRUNE_TICKS);
        plugin.getServer()
                .getScheduler()
                .runTaskTimer(plugin, services.bars()::refresh, ProgressBars.REFRESH_TICKS, ProgressBars.REFRESH_TICKS);
        if (!primary) return;
        executor.scheduleWithFixedDelay(
                () -> {
                    try {
                        var check = services.service().check();
                        if (check.changed()) {
                            services.access().checkSoon();
                        }
                    } catch (RuntimeException e) {
                        plugin.getLogger().log(Level.SEVERE, "Could not check the jobs", e);
                    }
                },
                CHECK_MINUTES,
                CHECK_MINUTES,
                TimeUnit.MINUTES);
    }

    /**
     * @param progress reads how far the work on a job is
     * @param job      a taken job
     * @return a fresh check of the job's area, null when it could not be read within half a minute
     */
    private static DigCheck.@Nullable Progress checked(JobProgress progress, Job job) {
        try {
            return progress.of(job, true)
                    .copy()
                    .orTimeout(30, TimeUnit.SECONDS)
                    .join()
                    .orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static NotificationArgs args(Job job) {
        return NotificationArgs.create().text("job_title", job.title()).number("job_id", job.id());
    }

    private static Set<UUID> members(ACompanyData companies, int company) {
        return profile(companies, company)
                .map(found -> {
                    Set<UUID> members = new HashSet<>();
                    for (var member : found.members()) members.add(member.uuid());
                    return members;
                })
                .orElse(Set.of());
    }

    private static Optional<CompanyProfile> profile(ACompanyData companies, int company) {
        var simple = companies.retrieveCompanyById(company).asFuture().join();
        if (simple == null || simple.isEmpty()) return Optional.empty();
        var profile = companies.retrieveCompanyProfile(simple.get()).asFuture().join();
        return profile == null ? Optional.empty() : profile;
    }

    /**
     * Opens the jobs of a disbanded company again.
     *
     * @param service the jobs
     * @param access  the access of this server's areas
     * @param plugin  the plugin, for the log
     */
    private record Disbands(JobService service, JobAccess access, Plugin plugin) implements Listener {
        @EventHandler
        public void onDisband(CompanyDisbandEvent event) {
            var company = event.company().id();
            CompletableFuture.runAsync(
                            () -> {
                                if (!service.releaseAll(company).isEmpty()) access.checkSoon();
                            },
                            Threading.VIRTUAL)
                    .exceptionally(err -> {
                        plugin.getLogger()
                                .log(Level.SEVERE, "Could not open the jobs of disbanded company " + company, err);
                        return null;
                    });
        }
    }

    /**
     * @param type a notice to the owner of a job
     * @return the notice for an uncharged job, which says nothing went back where the other says money did
     */
    static NotificationType uncharged(NotificationType type) {
        return switch (type) {
            case MY_JOB_EXPIRED -> NotificationType.MY_JOB_EXPIRED_UNCHARGED;
            case MY_JOB_LAND_LOST -> NotificationType.MY_JOB_LAND_LOST_UNCHARGED;
            case MY_JOB_LAND_LOST_WORKED -> NotificationType.MY_JOB_LAND_LOST_WORKED_UNCHARGED;
            case MY_JOB_HARVEST_ENDED -> NotificationType.MY_JOB_HARVEST_ENDED_UNCHARGED;
            case MY_JOB_SETTLED -> NotificationType.MY_JOB_SETTLED_UNCHARGED;
            case MY_JOB_CANCEL_ACCEPTED -> NotificationType.MY_JOB_CANCEL_ACCEPTED_UNCHARGED;
            default -> type;
        };
    }
}
