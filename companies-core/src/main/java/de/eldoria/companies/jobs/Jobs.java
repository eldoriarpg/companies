/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AJobData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.job.Job;
import de.eldoria.companies.data.wrapper.job.JobKind;
import de.eldoria.companies.data.wrapper.job.JobState;
import de.eldoria.companies.events.company.CompanyDisbandEvent;
import de.eldoria.companies.jobs.hooks.JobHooks;
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
import de.eldoria.companies.util.Threading;
import de.eldoria.companies.util.Times;
import net.milkbowl.vault.economy.Economy;
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
            FeeReductions fees) {
        var hooks = JobHooks.create(plugin, name -> configuration.hooks().job(name));
        hooks.load();
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
                            case LAND_LOST -> NotificationType.MY_JOB_LAND_LOST;
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
                        };
                if (type != null) notifications.send(owner, type, args);
            }

            @Override
            public void paid(CompanyProfile company, Job job, double paid) {
                notifications.sendCompany(
                        company, NotificationType.JOB_PAID, args(job).money("amount", economy.format(paid)));
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
            public void landLost(int company, Job job) {
                profile(companies, company)
                        .ifPresent(
                                found -> notifications.sendCompany(found, NotificationType.JOB_LAND_LOST, args(job)));
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
                service::harvested,
                blueprints,
                plugin.getLogger());
        plugin.getServer().getPluginManager().registerEvents(access, plugin);
        plugin.getServer().getPluginManager().registerEvents(new Disbands(service, access, plugin), plugin);
        var ghosts = new BuildGhosts(plugin);
        plugin.getServer().getPluginManager().registerEvents(ghosts, plugin);
        return new JobServices(jobs, service, hooks, access, progress, node, blueprints, ghosts);
    }

    /**
     * Schedules the access of this server's areas and, on the primary, the check that expires jobs.
     *
     * @param services the jobs
     * @param primary  whether this server is the primary
     * @param executor runs the checks
     * @param plugin   the plugin, for the log
     */
    public static void schedule(
            JobServices services, boolean primary, ScheduledExecutorService executor, Plugin plugin) {
        services.access().schedule(executor);
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
}
