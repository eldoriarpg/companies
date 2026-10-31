/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.configuration.elements.jobs.JobSettings;
import de.eldoria.companies.data.repository.AJobData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.job.Job;
import de.eldoria.companies.data.wrapper.job.JobArea;
import de.eldoria.companies.data.wrapper.job.JobExtension;
import de.eldoria.companies.data.wrapper.job.JobHarvest;
import de.eldoria.companies.data.wrapper.job.JobKind;
import de.eldoria.companies.data.wrapper.job.JobLand;
import de.eldoria.companies.data.wrapper.job.JobReview;
import de.eldoria.companies.data.wrapper.job.JobState;
import de.eldoria.companies.data.wrapper.job.JobTarget;
import de.eldoria.companies.orders.Payout;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.DoubleUnaryOperator;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The life of jobs: posting one with its price held, a company taking it with one of its work slots, giving it back,
 * the owner cancelling it while nobody works on it, and paying the company when the work checks out. A contract has no
 * check: the company says it is done and the owner approves or rejects it, and a job waiting longer than
 * {@link JobSettings#approveHours()} is approved by the primary. The owner may approve any job a company works on early,
 * paying it as it stands. Either side may open a dispute about a job a company holds and withdraw it again; meanwhile
 * a job the owner disputes uses none of the company's work slots, the owner may still approve it, and an admin settles it with any split
 * of the price between the company and the owner. Paying and disputing are conditional on the company that was read
 * still holding the job, so a job given back and taken by another company meanwhile is never paid to the first. The primary
 * expires open jobs nobody took and opens taken jobs whose time ran out again; giving a job back or running out of time
 * costs the company the penalties of failed orders. A job whose land changed hands is cancelled with its price given
 * back, and the jobs of a disbanded company are opened again, both without a penalty.
 * <p>
 * The owner pays the price and the fee, lowered by the fee reductions of server events, when posting. An area that
 * overlaps another running job is refused. Cancelling an open job gives the price back and keeps the fee, as for
 * orders; an expired job gives both back. When a job is done the price is split among the company's members by the
 * blocks each removed from the area, the member who finished it taking all when nobody's work was counted; each part is
 * taxed by the company's level and the tax reductions of server events and its treasury share kept as for order
 * payouts, and the fee and the taxes go to the collector. Who gets what is read before the job is closed; a payment
 * that fails afterwards is logged with its amount. Every change is conditional on the job's state, so two servers never
 * move a job twice. The methods block and are called off the main thread; the money hops to the main thread.
 */
public final class JobService {
    /**
     * The highest price of a job.
     */
    public static final double MAX_PRICE = 10_000_000;

    /**
     * The lowest price of a job.
     */
    public static final double MIN_PRICE = 0.01;

    /**
     * What a job closed because its land changed hands notes as who closed it. It names no person.
     */
    public static final String LAND_LOST_BY = "#land";

    /**
     * What a job approved because its owner did not answer in time notes as who closed it. It names no person.
     */
    public static final String AUTO_APPROVED_BY = "#auto";

    /**
     * What a harvest job ended because its time ran out notes as who closed it. It names no person.
     */
    public static final String HARVEST_TIME_BY = "#time";

    /**
     * What a harvest job ended because its budget was used up notes as who closed it. It names no person.
     */
    public static final String BUDGET_BY = "#budget";

    private final AJobData jobs;
    private final AOrderData orders;
    private final Money money;
    private final Notices notices;
    private final Penalties penalties;
    private final IntFunction<Optional<CompanyProfile>> companies;
    private final Supplier<JobSettings> settings;
    private final DoubleUnaryOperator fee;
    private final ToIntFunction<CompanyProfile> maxOrders;
    private final Clock clock;
    private final Logger logger;

    /**
     * @param jobs      job repository
     * @param orders    order repository, for the work slots a company uses
     * @param money     moves the money
     * @param notices   tells owners and companies what happened
     * @param penalties costs a company level progress when it gives a job back or its time runs out
     * @param companies a company by its id, empty when it is gone
     * @param settings  the settings of jobs
     * @param fee       the fee of a price: the order fee, lowered by the fee reductions of server events
     * @param maxOrders the work slots the level of a company allows
     * @param clock     clock giving the current time in UTC
     * @param logger    reports failures
     */
    public JobService(
            AJobData jobs,
            AOrderData orders,
            Money money,
            Notices notices,
            Penalties penalties,
            IntFunction<Optional<CompanyProfile>> companies,
            Supplier<JobSettings> settings,
            DoubleUnaryOperator fee,
            ToIntFunction<CompanyProfile> maxOrders,
            Clock clock,
            Logger logger) {
        this.jobs = jobs;
        this.orders = orders;
        this.money = money;
        this.notices = notices;
        this.penalties = penalties;
        this.companies = companies;
        this.settings = settings;
        this.fee = fee;
        this.maxOrders = maxOrders;
        this.clock = clock;
        this.logger = logger;
    }

    /**
     * Posts a job: checks it, takes its price and fee from the owner, and stores it.
     *
     * @param owner    the player posting it
     * @param kind     what work it asks for
     * @param title    title shown to companies
     * @param price    what the company is paid
     * @param dueHours how long a company has to finish it
     * @param node     the server whose world holds the area
     * @param area     the area of the work
     * @param land     whose land the area lies on
     * @param landRef  the plot or region of the land, null for unowned land
     * @return the stored job, or why it was not posted
     */
    public Posted post(
            UUID owner,
            JobKind kind,
            String title,
            double price,
            int dueHours,
            UUID node,
            JobArea area,
            JobLand land,
            @Nullable String landRef) {
        return post(owner, kind, title, price, dueHours, node, area, land, landRef, Details.NONE);
    }

    /**
     * Posts a job with a target: checks it, takes its price and fee from the owner, and stores it.
     *
     * @param owner    the player posting it
     * @param kind     what work it asks for
     * @param title    title shown to companies
     * @param price    what the company is paid
     * @param dueHours how long a company has to finish it
     * @param node     the server whose world holds the area
     * @param area     the area of the work
     * @param land     whose land the area lies on
     * @param landRef  the plot or region of the land, null for unowned land
     * @param details  what the kind of job asks for besides its area
     * @return the stored job, or why it was not posted
     */
    public Posted post(
            UUID owner,
            JobKind kind,
            String title,
            double price,
            int dueHours,
            UUID node,
            JobArea area,
            JobLand land,
            @Nullable String landRef,
            Details details) {
        var target = details.target();
        var unitPrice = details.unitPrice();
        var blueprint = details.blueprint();
        var description = details.description();
        var current = settings.get();
        if (!current.enabled()) return new Posted.Refused(Refusal.DISABLED);
        if ((kind == JobKind.BUILD) != (blueprint != null)) return new Posted.Refused(Refusal.BLUEPRINT);
        if ((kind == JobKind.HARVEST) != (unitPrice != null)) return new Posted.Refused(Refusal.UNIT_PRICE);
        if (unitPrice != null && (!(cents(unitPrice) >= MIN_PRICE) || cents(unitPrice) > cents(price))) {
            return new Posted.Refused(Refusal.UNIT_PRICE);
        }
        if ((kind == JobKind.TERRAFORM) != (target != null)) return new Posted.Refused(Refusal.TARGET);
        if (target != null && (target.y() < area.minY() || target.y() > area.maxY())) {
            return new Posted.Refused(Refusal.TARGET);
        }
        var name = title.strip();
        if (name.isEmpty() || name.length() > Job.MAX_TITLE) return new Posted.Refused(Refusal.TITLE);
        if (description != null && description.length() > Job.MAX_DESCRIPTION) {
            return new Posted.Refused(Refusal.DESCRIPTION);
        }
        var rounded = cents(price);
        if (!(rounded >= MIN_PRICE) || rounded > MAX_PRICE) return new Posted.Refused(Refusal.PRICE);
        if (dueHours < 1 || dueHours > current.maxDueHours()) return new Posted.Refused(Refusal.DUE);
        if (kind.area() && area.volume() > current.maxVolume(kind)) return new Posted.Refused(Refusal.VOLUME);
        if (kind.area() && area.chunks() > current.maxChunks()) return new Posted.Refused(Refusal.CHUNKS);
        if (jobs.openOf(owner) >= current.maxOpenPerOwner()) return new Posted.Refused(Refusal.TOO_MANY);
        if (kind.area() && !jobs.overlapping(area).isEmpty()) return new Posted.Refused(Refusal.OVERLAP);
        var charged = cents(fee.applyAsDouble(rounded));
        var total = cents(rounded + charged);
        if (!money.withdraw(owner, total)) return new Posted.Refused(Refusal.FUNDS);
        var now = now();
        var draft = Job.draft(
                        owner,
                        kind,
                        name,
                        rounded,
                        charged,
                        node,
                        area,
                        land,
                        landRef,
                        dueHours,
                        now,
                        now.plusDays(current.openDays()))
                .withTarget(target)
                .withHarvest(unitPrice == null ? null : new JobHarvest(cents(unitPrice), 0))
                .withDescription(description);
        Optional<Integer> id;
        try {
            id = jobs.create(draft);
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Could not store a job of " + owner, e);
            id = Optional.empty();
        }
        if (id.isPresent() && blueprint != null && !storedBlueprint(id.get(), blueprint)) {
            if (!jobs.close(id.get(), JobState.OPEN, JobState.CANCELLED, null, now)) {
                logger.severe("Job " + id.get() + " was taken before its blueprint could be stored; settle it by hand");
                return new Posted.Refused(Refusal.STRANDED);
            }
            id = Optional.empty();
        }
        if (id.isEmpty()) {
            money.deposit(owner, total);
            return new Posted.Refused(Refusal.STORAGE);
        }
        var stored = id.get();
        return new Posted.Done(jobs.job(stored).orElseGet(() -> draft.withId(stored)));
    }

    private boolean storedBlueprint(int id, Blueprint blueprint) {
        try {
            return jobs.blueprint(id, blueprint.encode());
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Could not store the blueprint of job " + id, e);
            return false;
        }
    }

    /**
     * What a kind of job asks for besides its area.
     *
     * @param target      the shape a terraform job asks for, its height within the area
     * @param unitPrice   what a harvest job pays for each item, at most its price, which is its budget
     * @param blueprint   what a build job asks to build, placed on its area
     * @param description what the owner writes about the job, stored with it so no company takes it without
     */
    public record Details(
            @Nullable JobTarget target,
            @Nullable Double unitPrice,
            @Nullable Blueprint blueprint,
            @Nullable String description) {
        /**
         * Nothing besides the area: a dig job or a contract.
         */
        public static final Details NONE = new Details(null, null, null, null);

        /**
         * @param target the shape a terraform job asks for
         * @return the details of a terraform job
         */
        public static Details shape(JobTarget target) {
            return new Details(target, null, null, null);
        }

        /**
         * @param unitPrice what a harvest job pays for each item
         * @return the details of a harvest job
         */
        public static Details harvest(double unitPrice) {
            return new Details(null, unitPrice, null, null);
        }

        /**
         * @param blueprint what a build job asks to build
         * @return the details of a build job
         */
        public static Details build(Blueprint blueprint) {
            return new Details(null, null, blueprint, null);
        }

        /**
         * @param description what the owner writes about a contract, blank for nothing
         * @return the details of a contract
         */
        public static Details contract(String description) {
            var text = description.strip();
            return new Details(null, null, null, text.isEmpty() ? null : text);
        }
    }

    /**
     * Lets a member's company take an open job, using one of its work slots.
     *
     * @param company the company
     * @param member  the member taking it
     * @param id      id of the job
     * @return what came of it
     */
    public Change take(CompanyProfile company, UUID member, int id) {
        if (!mayManage(company, member)) return Change.NO_PERMISSION;
        var job = jobs.job(id).orElse(null);
        if (job == null) return Change.UNKNOWN;
        if (job.state() != JobState.OPEN || !job.expires().isAfter(now())) return Change.CLOSED;
        if (company.member(job.owner()).isPresent()) return Change.OWN;
        var used = orders.retrieveCompanySlotCount(company).join();
        if (used == null || used >= maxOrders.applyAsInt(company)) return Change.LIMIT;
        var now = now();
        var due = now.plusHours(job.dueHours());
        if (!jobs.take(id, company.id(), now, due)) return Change.CLOSED;
        notices.owner(job.owner(), Notice.TAKEN, job, due);
        return Change.DONE;
    }

    /**
     * Gives a job the member's company works on back; it is open again for every company.
     *
     * @param company the company
     * @param member  the member giving it back
     * @param id      id of the job
     * @return what came of it
     */
    public Change giveBack(CompanyProfile company, UUID member, int id) {
        if (!mayManage(company, member)) return Change.NO_PERMISSION;
        var job = jobs.job(id).orElse(null);
        if (job == null) return Change.UNKNOWN;
        if (!Integer.valueOf(company.id()).equals(job.company())) return Change.NOT_YOURS;
        if (job.state() == JobState.DISPUTED) return Change.DISPUTED;
        if (job.state() != JobState.TAKEN && job.state() != JobState.DONE) return Change.NOT_YOURS;
        if (!jobs.release(id, company.id())) return Change.CLOSED;
        notices.owner(job.owner(), Notice.RELEASED, job, null);
        penalties.penalize(company.id(), false);
        return Change.DONE;
    }

    /**
     * Opens every job a company works on again, when the company is disbanded. No penalty applies.
     *
     * @param company id of the company
     * @return the jobs opened again
     */
    public List<Integer> releaseAll(int company) {
        List<Integer> released = new ArrayList<>();
        for (var job : jobs.ofCompany(company)) {
            if (!jobs.release(job.id(), company)) continue;
            notices.owner(job.owner(), Notice.RELEASED, job, null);
            released.add(job.id());
        }
        return released;
    }

    /**
     * Closes a job whose land changed hands, such as a plot that was sold. A waiting job gives its price back and keeps
     * the fee. A job a company works on pays the company for the share of the area it cleared, as a finished job pays,
     * and gives the rest of the price back, so an owner can not keep the work by giving the land away; without counted
     * work the company's owner gets the share. No penalty applies.
     *
     * @param job      the job as read
     * @param progress how far the work on a taken job is; a taken job without it is left alone, so it is tried again
     * @return true when the job was running and is now cancelled
     */
    public boolean landLost(Job job, DigCheck.@Nullable Progress progress) {
        var now = now();
        var company = job.company();
        if (job.state() == JobState.OPEN) {
            if (!jobs.close(job.id(), JobState.OPEN, JobState.CANCELLED, LAND_LOST_BY, now)) return false;
            money.deposit(job.owner(), cents(job.unpaid()));
            money.collect(job.fee());
            notices.owner(job.owner(), Notice.LAND_LOST, job, null);
            return true;
        }
        if (job.state() == JobState.TAKEN && job.kind() == JobKind.HARVEST) {
            return ended(job, LAND_LOST_BY, JobState.CANCELLED);
        }
        if (job.state() != JobState.TAKEN || company == null || progress == null) return false;
        var profile = companies.apply(company).orElse(null);
        var earned = profile == null ? 0 : earned(job.price(), progress);
        var parts = earned > 0 && profile != null
                ? parts(earned, jobs.workOf(job.id()), head(profile))
                : Map.<UUID, Double>of();
        var tax = profile == null ? 0 : money.taxPercent(profile);
        var share = profile == null ? 0 : money.share(profile);
        if (!jobs.closeTaken(job.id(), company, JobState.CANCELLED, LAND_LOST_BY, now)) return false;
        var rest = cents(job.price() - earned);
        if (rest > 0) money.deposit(job.owner(), rest);
        if (profile != null && earned > 0) {
            pay(job, profile, parts, tax, share);
        } else {
            money.collect(job.fee());
        }
        notices.owner(job.owner(), Notice.LAND_LOST, job, null);
        notices.landLost(company, job);
        return true;
    }

    /**
     * @param price    the price of a job
     * @param progress how far its work is
     * @return the share of the price the cleared blocks earned, rounded down to the cent
     */
    static double earned(double price, DigCheck.Progress progress) {
        if (progress.total() <= 0) return 0;
        if (progress.done()) return price;
        return BigDecimal.valueOf(price)
                .multiply(BigDecimal.valueOf(progress.cleared()))
                .divide(BigDecimal.valueOf(progress.total()), 2, RoundingMode.FLOOR)
                .doubleValue();
    }

    /**
     * Checks whether a member's company may finish a job, before its area is checked.
     *
     * @param company the company
     * @param member  the member
     * @param id      id of the job
     * @return {@link Change#DONE} when it may
     */
    public Change mayFinish(CompanyProfile company, UUID member, int id) {
        if (!mayManage(company, member)) return Change.NO_PERMISSION;
        var job = jobs.job(id).orElse(null);
        if (job == null) return Change.UNKNOWN;
        if (job.state() != JobState.TAKEN || !Integer.valueOf(company.id()).equals(job.company())) {
            return Change.NOT_YOURS;
        }
        if (!job.kind().area()) return Change.CONTRACT;
        if (job.kind() == JobKind.HARVEST) return Change.HARVEST;
        return Change.DONE;
    }

    /**
     * Cancels the owner's job while nobody works on it: the price goes back, the fee is kept.
     *
     * @param owner the owner
     * @param by    who cancels it, as noted
     * @param id    id of the job
     * @return what came of it
     */
    public Change cancel(UUID owner, String by, int id) {
        var job = jobs.job(id).orElse(null);
        if (job == null) return Change.UNKNOWN;
        if (!job.owner().equals(owner)) return Change.NOT_YOURS;
        if (job.state() != JobState.OPEN) return job.state().closed() ? Change.CLOSED : Change.TAKEN;
        if (!jobs.close(id, JobState.OPEN, JobState.CANCELLED, by, now())) return Change.CLOSED;
        money.deposit(owner, cents(job.unpaid()));
        money.collect(job.fee());
        return Change.DONE;
    }

    /**
     * Finishes a job the member's company works on, when its check passed on the server of its area: the job is paid.
     *
     * @param company  the company
     * @param member   the member finishing it
     * @param id       id of the job
     * @param progress the check of the job's area, made on the main thread of its server
     * @return what came of it
     */
    public Finished finish(CompanyProfile company, UUID member, int id, DigCheck.Progress progress) {
        if (!mayManage(company, member)) return new Finished.Refused(Change.NO_PERMISSION);
        var job = jobs.job(id).orElse(null);
        if (job == null) return new Finished.Refused(Change.UNKNOWN);
        if (job.state() != JobState.TAKEN || !Integer.valueOf(company.id()).equals(job.company())) {
            return new Finished.Refused(Change.NOT_YOURS);
        }
        if (!job.kind().area()) return new Finished.Refused(Change.CONTRACT);
        if (job.kind() == JobKind.HARVEST) return new Finished.Refused(Change.HARVEST);
        if (!progress.done()) return new Finished.NotDone(progress);
        var parts = parts(job.price(), jobs.workOf(id), member);
        var tax = money.taxPercent(company);
        var share = money.share(company);
        if (!jobs.close(id, JobState.TAKEN, JobState.PAID, null, now())) {
            return new Finished.Refused(Change.CLOSED);
        }
        var paid = pay(job, company, parts, tax, share);
        notices.owner(job.owner(), Notice.DONE, job, null);
        notices.paid(company, job, paid);
        return new Finished.Paid(paid);
    }

    /**
     * Stores what a member harvested on a harvest job and pays them for it from the budget, taxed and with the treasury
     * share kept as for order payouts. Items the budget no longer covers, or harvested for a company that no longer has
     * the job, still go into the stock, as they came from the owner's field. When the budget is used up the job ends.
     * A payment that loses against another one of the same job, which the budget check of the database refuses, is
     * worked out again once against the budget as it then stands.
     *
     * @param id      id of the job
     * @param company id of the company the member harvested for
     * @param worker  the member
     * @param items   the items, as amounts by material name
     * @return what the member was paid after tax and treasury share
     */
    public double harvested(int id, int company, UUID worker, Map<String, Integer> items) {
        if (items.isEmpty()) return 0;
        long units = items.values().stream().mapToLong(Integer::longValue).sum();
        var profile = companies.apply(company).orElse(null);
        Job job = null;
        double gross = 0;
        var left = BigDecimal.ZERO;
        var paid = false;
        for (var attempt = 0; attempt < 2 && !paid; attempt++) {
            job = jobs.job(id).orElse(null);
            var harvest = job == null ? null : job.harvest();
            if (job == null || harvest == null) return 0;
            left = BigDecimal.valueOf(job.unpaid());
            gross = BigDecimal.valueOf(harvest.unitPrice())
                    .multiply(BigDecimal.valueOf(units))
                    .min(left)
                    .setScale(2, RoundingMode.FLOOR)
                    .doubleValue();
            var mine = job.state() == JobState.TAKEN && Integer.valueOf(company).equals(job.company());
            if (!mine || profile == null) break;
            if (gross <= 0) {
                ended(job, BUDGET_BY, JobState.PAID);
                break;
            }
            paid = jobs.harvested(id, company, items, gross);
        }
        var harvest = job == null ? null : job.harvest();
        if (!paid || job == null || harvest == null || profile == null) {
            jobs.stock(id, items);
            return 0;
        }
        var payout = Payout.split(gross, money.taxPercent(profile), money.share(profile));
        try {
            money.deposit(worker, payout.net());
            money.collect(payout.tax());
            if (payout.shareCents() > 0) money.keep(profile, id, payout.shareCents());
        } catch (RuntimeException e) {
            logger.log(
                    Level.SEVERE,
                    "Could not pay " + payout.net() + " of harvest job " + id + " to " + worker + "; pay it by hand",
                    e);
        }
        if (left.subtract(BigDecimal.valueOf(gross)).compareTo(BigDecimal.valueOf(harvest.unitPrice())) < 0) {
            jobs.job(id).ifPresent(found -> ended(found, BUDGET_BY, JobState.PAID));
        }
        return payout.net();
    }

    /**
     * Ends a harvest job a member's company works on before its time runs out.
     *
     * @param company the company
     * @param member  the member ending it
     * @param id      id of the job
     * @return what came of it
     */
    public Change endAsCompany(CompanyProfile company, UUID member, int id) {
        if (!mayManage(company, member)) return Change.NO_PERMISSION;
        var job = jobs.job(id).orElse(null);
        if (job == null) return Change.UNKNOWN;
        if (job.state() != JobState.TAKEN || !Integer.valueOf(company.id()).equals(job.company())) {
            return Change.NOT_YOURS;
        }
        if (job.kind() != JobKind.HARVEST) return Change.CLOSED;
        return ended(job, null, JobState.PAID) ? Change.DONE : Change.CLOSED;
    }

    /**
     * Ends the owner's harvest job a company works on before its time runs out. The company keeps what it was paid.
     *
     * @param owner the owner
     * @param by    who ends it, as noted
     * @param id    id of the job
     * @return what came of it
     */
    public Change endAsOwner(UUID owner, String by, int id) {
        var job = jobs.job(id).orElse(null);
        if (job == null || !job.owner().equals(owner)) return Change.UNKNOWN;
        if (job.kind() != JobKind.HARVEST) return Change.CLOSED;
        if (job.state() == JobState.OPEN) return Change.TAKEN;
        if (job.state() != JobState.TAKEN) return Change.CLOSED;
        return ended(job, by, JobState.PAID) ? Change.DONE : Change.CLOSED;
    }

    /**
     * Closes a harvest job while its company still has it and gives the rest of the budget back to the owner. The rest
     * is read after closing, so items paid while it closed are never paid twice. The fee is kept.
     *
     * @param job the job as read, taken
     * @param by  who ended it, null when its company did
     * @param to  its final state
     * @return true when it was taken and is now closed
     */
    private boolean ended(Job job, @Nullable String by, JobState to) {
        var company = job.company();
        if (company == null) return false;
        if (!jobs.closeHeld(job.id(), company, JobState.TAKEN, to, by, now())) return false;
        var closed = jobs.job(job.id()).orElse(job);
        var harvest = closed.harvest();
        var paid = harvest == null ? 0 : harvest.paid();
        var rest = cents(job.price() - paid);
        if (rest > 0) money.deposit(job.owner(), rest);
        money.collect(job.fee());
        var stocked = jobs.stock(job.id()).values().stream()
                .mapToInt(Integer::intValue)
                .sum();
        if (to == JobState.CANCELLED) {
            notices.owner(job.owner(), Notice.LAND_LOST, job, null);
            notices.landLost(company, job);
        } else {
            notices.owner(job.owner(), Notice.HARVEST_ENDED, job, new Harvested(rest, stocked));
            companies.apply(company).ifPresent(profile -> notices.paid(profile, job, paid));
        }
        return true;
    }

    /**
     * Sets the description of the owner's job while it waits for a company.
     *
     * @param owner       the owner
     * @param id          id of the job
     * @param description what the owner writes about it, blank for none
     * @return what came of it
     */
    public Change describe(UUID owner, int id, String description) {
        var text = description.strip();
        if (text.length() > Job.MAX_DESCRIPTION) return Change.TOO_LONG;
        var job = jobs.job(id).orElse(null);
        if (job == null || !job.owner().equals(owner)) return Change.UNKNOWN;
        if (job.state() != JobState.OPEN) return job.state().closed() ? Change.CLOSED : Change.TAKEN;
        return jobs.describe(id, text.isEmpty() ? null : text) ? Change.DONE : Change.CLOSED;
    }

    /**
     * Gives the company holding the owner's job more time, also when it did not ask; an open request of the company is
     * closed by it. The job may have at most {@link JobSettings#maxTotalHours()} in all, counted from when it was taken.
     *
     * @param owner the owner
     * @param id    id of the job
     * @param hours the hours to add
     * @return what came of it
     */
    public Change extend(UUID owner, int id, int hours) {
        if (!settings.get().extensionsEnabled()) return Change.DISABLED;
        var job = jobs.job(id).orElse(null);
        if (job == null || !job.owner().equals(owner)) return Change.UNKNOWN;
        return extend(job, hours, null);
    }

    /**
     * Grants the open request of the company holding the owner's job, with the hours it asked for.
     *
     * @param owner the owner
     * @param id    id of the job
     * @return what came of it
     */
    public Change grantExtension(UUID owner, int id) {
        if (!settings.get().extensionsEnabled()) return Change.DISABLED;
        var job = jobs.job(id).orElse(null);
        if (job == null || !job.owner().equals(owner)) return Change.UNKNOWN;
        if (job.state() != JobState.TAKEN) return Change.CLOSED;
        var asked = job.extension().askedHours();
        if (asked == null) return Change.NO_REQUEST;
        return extend(job, asked, job.extension().askedAt());
    }

    /**
     * Extends a taken job whose time has not run out yet, answering the request made at {@code asked} when it is set.
     */
    private Change extend(Job job, int hours, @Nullable LocalDateTime asked) {
        var company = job.company();
        var due = job.due();
        var now = now();
        if (job.state() != JobState.TAKEN || company == null || due == null || !due.isAfter(now)) {
            return Change.CLOSED;
        }
        if (hours < 1 || hours > hoursLeft(job)) return Change.HOURS;
        var until = due.plusHours(hours);
        if (!jobs.extend(job.id(), company, due, until, hours, asked, now)) return Change.CLOSED;
        notices.extended(company, job, hours, until);
        return Change.DONE;
    }

    /**
     * Refuses the open request of the company holding the owner's job.
     *
     * @param owner  the owner
     * @param id     id of the job
     * @param reason why, blank for no reason
     * @return what came of it
     */
    public Change refuseExtension(UUID owner, int id, String reason) {
        var text = reason.strip();
        if (text.length() > JobExtension.MAX_REASON) return Change.REASON;
        var job = jobs.job(id).orElse(null);
        if (job == null || !job.owner().equals(owner)) return Change.UNKNOWN;
        var company = job.company();
        if (job.state() != JobState.TAKEN || company == null) return Change.CLOSED;
        if (!job.extension().asked()) return Change.NO_REQUEST;
        if (!jobs.refuseExtension(job.id(), company)) return Change.NO_REQUEST;
        notices.extensionRefused(company, job, text.isEmpty() ? null : text);
        return Change.DONE;
    }

    /**
     * Lets a member's company ask the owner of a job it holds for more time. The clock keeps running while the owner
     * decides.
     *
     * @param company the company
     * @param member  the member asking
     * @param id      id of the job
     * @param hours   the hours asked for
     * @param reason  why
     * @return what came of it
     */
    public Change askExtension(CompanyProfile company, UUID member, int id, int hours, String reason) {
        var current = settings.get();
        if (!current.extensionsEnabled()) return Change.DISABLED;
        if (!mayManage(company, member)) return Change.NO_PERMISSION;
        var text = reason.strip();
        if (text.isEmpty() || text.length() > JobExtension.MAX_REASON) return Change.REASON;
        var job = jobs.job(id).orElse(null);
        if (job == null) return Change.UNKNOWN;
        if (job.state() != JobState.TAKEN || !Integer.valueOf(company.id()).equals(job.company())) {
            return job.state() == JobState.TAKEN ? Change.NOT_YOURS : Change.CLOSED;
        }
        var due = job.due();
        var now = now();
        if (due == null || !due.isAfter(now)) return Change.CLOSED;
        if (job.extension().asked()) return Change.ASKED;
        if (job.extension().requests() >= current.maxExtensionRequests()) return Change.REQUESTS;
        if (hours < 1 || hours > hoursLeft(job)) return Change.HOURS;
        if (!jobs.askExtension(id, company.id(), hours, text, now, current.maxExtensionRequests())) {
            return Change.CLOSED;
        }
        notices.owner(job.owner(), Notice.EXTENSION_ASKED, job, new Asked(hours, text, Duration.between(now, due)));
        return Change.DONE;
    }

    /**
     * @param job a taken job
     * @return how many more hours it may still get, 0 when none
     */
    public int hoursLeft(Job job) {
        return Math.max(
                0,
                settings.get().maxTotalHours()
                        - job.dueHours()
                        - job.extension().extendedHours());
    }

    /**
     * Notes that a member's company says the work on a contract is done. It then waits for the owner, who approves or
     * rejects it; without an answer it is approved after {@link JobSettings#approveHours()}.
     *
     * @param company the company
     * @param member  the member saying so
     * @param id      id of the job
     * @return what came of it
     */
    public Change markDone(CompanyProfile company, UUID member, int id) {
        if (!mayManage(company, member)) return Change.NO_PERMISSION;
        var job = jobs.job(id).orElse(null);
        if (job == null) return Change.UNKNOWN;
        if (job.state() != JobState.TAKEN || !Integer.valueOf(company.id()).equals(job.company())) {
            return Change.NOT_YOURS;
        }
        if (job.kind().area()) return Change.AREA;
        var now = now();
        if (!jobs.markDone(id, company.id(), member, now)) return Change.CLOSED;
        notices.owner(
                job.owner(), Notice.AWAITING, job, now.plusHours(settings.get().approveHours()));
        return Change.DONE;
    }

    /**
     * Approves the owner's job and pays the company: a contract the company says is done, or any job a company holds,
     * paid as it stands even when its check would not pass yet or while it is disputed.
     *
     * @param owner the owner
     * @param by    who approves it, as noted
     * @param id    id of the job
     * @return what came of it
     */
    public Change approve(UUID owner, String by, int id) {
        var job = jobs.job(id).orElse(null);
        if (job == null || !job.owner().equals(owner)) return Change.UNKNOWN;
        if (job.kind() == JobKind.HARVEST) return Change.HARVEST;
        if (!JobState.HELD.contains(job.state())) return Change.CLOSED;
        return approved(job, by) ? Change.DONE : Change.CLOSED;
    }

    /**
     * Sends a contract the company says is done back to work. The time the job waited for the owner is added to the
     * company's time, so waiting costs the company nothing.
     *
     * @param owner  the owner
     * @param id     id of the job
     * @param reason why the work is not done
     * @return what came of it
     */
    public Change reject(UUID owner, int id, String reason) {
        var text = reason.strip();
        if (text.isEmpty() || text.length() > JobReview.MAX_REASON) return Change.REASON;
        var job = jobs.job(id).orElse(null);
        if (job == null || !job.owner().equals(owner)) return Change.UNKNOWN;
        var company = job.company();
        if (job.state() != JobState.DONE || company == null) return Change.NOT_DONE;
        var now = now();
        var waited = Duration.between(Objects.requireNonNullElse(job.review().doneAt(), now), now);
        var due = job.due();
        var newDue =
                (due == null ? now.plusHours(job.dueHours()) : due).plus(waited.isNegative() ? Duration.ZERO : waited);
        if (!jobs.reject(id, text, newDue)) return Change.CLOSED;
        notices.rejected(company, job, text);
        return Change.DONE;
    }

    /**
     * Opens a dispute about the owner's job a company holds. Access to its area ends and its time stops until an admin
     * settles it.
     *
     * @param owner  the owner
     * @param id     id of the job
     * @param reason why
     * @return what came of it
     */
    public Change disputeAsOwner(UUID owner, int id, String reason) {
        var job = jobs.job(id).orElse(null);
        if (job == null || !job.owner().equals(owner)) return Change.UNKNOWN;
        return dispute(job, owner, reason);
    }

    /**
     * Opens a dispute about a job a member's company holds.
     *
     * @param company the company
     * @param member  the member opening it
     * @param id      id of the job
     * @param reason  why
     * @return what came of it
     */
    public Change disputeAsCompany(CompanyProfile company, UUID member, int id, String reason) {
        if (!mayManage(company, member)) return Change.NO_PERMISSION;
        var job = jobs.job(id).orElse(null);
        if (job == null) return Change.UNKNOWN;
        if (!Integer.valueOf(company.id()).equals(job.company())) return Change.NOT_YOURS;
        return dispute(job, member, reason);
    }

    private Change dispute(Job job, UUID by, String reason) {
        var text = reason.strip();
        if (text.isEmpty() || text.length() > JobReview.MAX_REASON) return Change.REASON;
        var company = job.company();
        if (job.kind() == JobKind.HARVEST) return Change.HARVEST;
        if (job.state() == JobState.DISPUTED) return Change.DISPUTED;
        if ((job.state() != JobState.TAKEN && job.state() != JobState.DONE) || company == null) return Change.CLOSED;
        if (!jobs.dispute(job.id(), company, job.state(), by, text, now())) return Change.CLOSED;
        if (by.equals(job.owner())) {
            notices.disputed(company, job, text);
        } else {
            notices.owner(job.owner(), Notice.DISPUTED, job, text);
        }
        return Change.DONE;
    }

    /**
     * Withdraws a dispute the owner opened: the job goes back to where it was, the company's time moved on by the time
     * the dispute lasted. A job the company had said is done waits for the owner again, its approval time unchanged.
     *
     * @param owner the owner
     * @param id    id of the job
     * @return what came of it
     */
    public Change withdrawAsOwner(UUID owner, int id) {
        var job = jobs.job(id).orElse(null);
        if (job == null || !job.owner().equals(owner)) return Change.UNKNOWN;
        if (job.state() != JobState.DISPUTED) return Change.CLOSED;
        if (!owner.equals(job.review().disputeBy())) return Change.NOT_YOURS;
        return withdraw(job);
    }

    /**
     * Withdraws a dispute the member's company opened: it works on the job again, its time moved on by the time the
     * dispute lasted.
     *
     * @param company the company
     * @param member  the member withdrawing it
     * @param id      id of the job
     * @return what came of it
     */
    public Change withdrawAsCompany(CompanyProfile company, UUID member, int id) {
        if (!mayManage(company, member)) return Change.NO_PERMISSION;
        var job = jobs.job(id).orElse(null);
        if (job == null) return Change.UNKNOWN;
        if (!Integer.valueOf(company.id()).equals(job.company())) return Change.NOT_YOURS;
        if (job.state() != JobState.DISPUTED) return Change.CLOSED;
        var by = job.review().disputeBy();
        if (by == null || by.equals(job.owner())) return Change.NOT_YOURS;
        return withdraw(job);
    }

    /**
     * Puts a disputed job back where it was. The company's time moves on by the time the dispute lasted. A job the
     * company had said is done waits for the owner again: after a dispute the company opened, its approval time moves
     * on as well, so the owner keeps the time to answer; after one the owner opened it runs on as before, so disputing
     * and withdrawing never puts approval off.
     *
     * @param job the disputed job as read
     * @return what came of it
     */
    private Change withdraw(Job job) {
        var company = job.company();
        var by = job.review().disputeBy();
        if (company == null || by == null) return Change.CLOSED;
        var now = now();
        var lasted = Duration.between(Objects.requireNonNullElse(job.review().disputedAt(), now), now);
        if (lasted.isNegative()) lasted = Duration.ZERO;
        var due = job.due();
        var newDue = (due == null ? now.plusHours(job.dueHours()) : due).plus(lasted);
        var doneAt = job.review().doneAt();
        var byOwner = job.owner().equals(by);
        var newDoneAt = doneAt == null || byOwner ? doneAt : doneAt.plus(lasted);
        if (!jobs.withdraw(job.id(), by, newDue, newDoneAt)) return Change.CLOSED;
        if (byOwner) {
            notices.withdrawn(company, job);
        } else {
            notices.owner(job.owner(), Notice.WITHDRAWN, job, null);
        }
        return Change.DONE;
    }

    /**
     * Settles a dispute: the company gets the given percent of the price, split among its members by their work and
     * paid as a finished job, and the owner gets the rest back. The fee is kept. When the company is gone the owner gets
     * all back.
     *
     * @param by      the admin settling it, as noted
     * @param id      id of the job
     * @param percent the percent of the price the company gets, 0 to 100
     * @return what came of it
     */
    public Change settle(String by, int id, int percent) {
        if (percent < 0 || percent > 100) throw new IllegalArgumentException("percent " + percent);
        var job = jobs.job(id).orElse(null);
        if (job == null) return Change.UNKNOWN;
        if (job.state() != JobState.DISPUTED) return Change.CLOSED;
        var company = job.company();
        var profile = company == null ? null : companies.apply(company).orElse(null);
        if (profile == null && percent > 0) return Change.NO_COMPANY;
        var earned = profile == null ? 0 : share(job.price(), percent);
        var parts = earned > 0 && profile != null
                ? parts(earned, jobs.workOf(id), finisher(job, profile))
                : Map.<UUID, Double>of();
        var tax = profile == null ? 0 : money.taxPercent(profile);
        var cut = profile == null ? 0 : money.share(profile);
        if (!jobs.settle(id, percent, by, now())) return Change.CLOSED;
        var rest = cents(job.price() - earned);
        if (rest > 0) money.deposit(job.owner(), rest);
        double paid = 0;
        if (profile != null && earned > 0) {
            paid = pay(job, profile, parts, tax, cut);
        } else {
            money.collect(job.fee());
        }
        notices.owner(job.owner(), Notice.SETTLED, job, rest);
        if (company != null) notices.settled(company, job, paid);
        return Change.DONE;
    }

    /**
     * Pays a job a company holds in full and closes it, as approved by its owner or after they did not answer.
     *
     * @param job the job as read, taken or done
     * @param by  who approved it
     * @return true when it was paid
     */
    private boolean approved(Job job, String by) {
        var company = job.company();
        if (company == null) return false;
        var profile = companies.apply(company).orElse(null);
        if (profile == null) return false;
        var parts = parts(job.price(), jobs.workOf(job.id()), finisher(job, profile));
        var tax = money.taxPercent(profile);
        var cut = money.share(profile);
        if (!jobs.closeHeld(job.id(), company, job.state(), JobState.PAID, by, now())) return false;
        var paid = pay(job, profile, parts, tax, cut);
        notices.paid(profile, job, paid);
        return true;
    }

    /**
     * @param job     a job
     * @param company the company holding it
     * @return the member who said the work is done, or the company's owner when nobody did
     */
    private static UUID finisher(Job job, CompanyProfile company) {
        var doneBy = job.review().doneBy();
        return doneBy == null ? head(company) : doneBy;
    }

    /**
     * @param price   a price
     * @param percent a percent of it
     * @return the percent of the price, rounded down to the cent
     */
    static double share(double price, int percent) {
        return BigDecimal.valueOf(price)
                .multiply(BigDecimal.valueOf(percent))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.FLOOR)
                .doubleValue();
    }

    /**
     * Expires the open jobs nobody took, giving price and fee back, opens the taken jobs whose time ran out again, and
     * approves the jobs whose owner did not answer in time. Runs on the primary.
     *
     * @return the jobs that expired, were opened again and were approved
     */
    public Check check() {
        var now = now();
        List<Integer> expired = new ArrayList<>();
        List<Integer> reopened = new ArrayList<>();
        List<Integer> approved = new ArrayList<>();
        List<Integer> ended = new ArrayList<>();
        for (var job : jobs.waiting(now.minusHours(settings.get().approveHours()))) {
            try {
                if (!approved(job, AUTO_APPROVED_BY)) continue;
                notices.owner(job.owner(), Notice.AUTO_APPROVED, job, null);
                approved.add(job.id());
            } catch (RuntimeException e) {
                logger.log(Level.SEVERE, "Could not approve job " + job.id(), e);
            }
        }
        for (var job : jobs.expired(now)) {
            try {
                if (!jobs.close(job.id(), JobState.OPEN, JobState.EXPIRED, null, now)) continue;
                var back = cents(job.unpaid() + job.fee());
                money.deposit(job.owner(), back);
                notices.owner(job.owner(), Notice.EXPIRED, job, back);
                expired.add(job.id());
            } catch (RuntimeException e) {
                logger.log(Level.SEVERE, "Could not expire job " + job.id(), e);
            }
        }
        for (var job : jobs.overdue(now)) {
            var company = job.company();
            if (company == null) continue;
            try {
                if (job.kind() == JobKind.HARVEST) {
                    if (ended(job, HARVEST_TIME_BY, JobState.PAID)) ended.add(job.id());
                    continue;
                }
                if (!jobs.release(job.id(), company)) continue;
                notices.owner(job.owner(), Notice.RELEASED, job, null);
                notices.overdue(company, job);
                penalties.penalize(company, true);
                reopened.add(job.id());
            } catch (RuntimeException e) {
                logger.log(Level.SEVERE, "Could not open overdue job " + job.id() + " again", e);
            }
        }
        return new Check(expired, reopened, approved, ended);
    }

    private double pay(Job job, CompanyProfile company, Map<UUID, Double> parts, double tax, int share) {
        var collected = BigDecimal.valueOf(job.fee());
        long kept = 0;
        var paid = BigDecimal.ZERO;
        for (var part : parts.entrySet()) {
            var payout = Payout.split(part.getValue(), tax, share);
            try {
                money.deposit(part.getKey(), payout.net());
                paid = paid.add(BigDecimal.valueOf(payout.net()));
            } catch (RuntimeException e) {
                logger.log(
                        Level.SEVERE,
                        "Could not pay " + payout.net() + " of job " + job.id() + " to " + part.getKey()
                                + "; pay it by hand",
                        e);
            }
            collected = collected.add(BigDecimal.valueOf(payout.tax()));
            kept += payout.shareCents();
        }
        try {
            money.collect(collected.doubleValue());
            if (kept > 0) money.keep(company, job.id(), kept);
        } catch (RuntimeException e) {
            logger.log(
                    Level.SEVERE,
                    "Could not hand the fee and taxes of job " + job.id() + " (" + collected
                            + ") or its treasury share (" + kept + " cents) on",
                    e);
        }
        return paid.doubleValue();
    }

    /**
     * Splits the price of a job among the workers by the blocks each removed, in cents: each gets the rounded down part
     * of their blocks, and the cents left go to the workers with the most blocks first, so the parts add up to the
     * price exactly. Without counted work the finisher gets all.
     *
     * @param price    the price
     * @param work     the blocks of every worker
     * @param finisher the member who finished the job
     * @return the gross of every worker, by worker
     */
    static Map<UUID, Double> parts(double price, Map<UUID, Integer> work, UUID finisher) {
        long total =
                work.values().stream().mapToLong(blocks -> Math.max(0, blocks)).sum();
        long cents = BigDecimal.valueOf(price)
                .movePointRight(2)
                .setScale(0, RoundingMode.FLOOR)
                .longValueExact();
        Map<UUID, Double> parts = new LinkedHashMap<>();
        if (total <= 0) {
            parts.put(finisher, cents / 100.0);
            return parts;
        }
        var sorted = work.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .toList();
        Map<UUID, Long> centsOf = new LinkedHashMap<>();
        long given = 0;
        for (var entry : sorted) {
            var part = cents * entry.getValue() / total;
            centsOf.put(entry.getKey(), part);
            given += part;
        }
        var left = cents - given;
        for (var index = 0; left > 0; index = (index + 1) % sorted.size(), left--) {
            centsOf.merge(sorted.get(index).getKey(), 1L, Long::sum);
        }
        centsOf.forEach((worker, part) -> parts.put(worker, part / 100.0));
        return parts;
    }

    /**
     * @param company a company
     * @return its owner, or another member when it has none
     */
    private static UUID head(CompanyProfile company) {
        UUID first = null;
        for (var member : company.members()) {
            if (member.hasPermission(CompanyPermission.OWNER)) return member.uuid();
            if (first == null) first = member.uuid();
        }
        return first == null ? new UUID(0, 0) : first;
    }

    private static boolean mayManage(CompanyProfile company, UUID member) {
        return company.member(member)
                .map(found -> found.hasPermission(CompanyPermission.MANAGE_ORDERS))
                .orElse(false);
    }

    private static double cents(double amount) {
        return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).withNano(0);
    }

    /**
     * Moves the money of jobs. Vault is only called on the main thread; the methods wait for it.
     */
    public interface Money {
        /**
         * @param player the player
         * @param amount what to take
         * @return true when the player held it and it was taken
         */
        boolean withdraw(UUID player, double amount);

        /**
         * @param player the player
         * @param amount what to give
         */
        void deposit(UUID player, double amount);

        /**
         * @param amount the fee and taxes to hand to the collector
         */
        void collect(double amount);

        /**
         * @param company a company
         * @return the tax of its level in percent
         */
        double taxPercent(CompanyProfile company);

        /**
         * @param company a company
         * @return the share of each payout it keeps in its treasury, in percent
         */
        int share(CompanyProfile company);

        /**
         * Credits the treasury share of a job's payout to the company.
         *
         * @param company the company
         * @param job     id of the job
         * @param cents   the share in cents
         */
        void keep(CompanyProfile company, int job, long cents);
    }

    /**
     * Tells owners and companies what happened to a job.
     */
    public interface Notices {
        /**
         * @param owner  the owner of the job
         * @param notice what happened
         * @param job    the job
         * @param detail the due time of a taken job, the time a waiting one is approved, the money of an expired or
         *               settled one, the reason of a dispute, null otherwise
         */
        void owner(UUID owner, Notice notice, Job job, @Nullable Object detail);

        /**
         * @param company the company
         * @param job     the job
         * @param paid    what its workers were paid in all
         */
        void paid(CompanyProfile company, Job job, double paid);

        /**
         * @param company id of the company whose time ran out
         * @param job     the job
         */
        void overdue(int company, Job job);

        /**
         * @param company id of the company that lost the job, since its land changed hands
         * @param job     the job
         */
        void landLost(int company, Job job);

        /**
         * @param company id of the company whose work the owner rejected
         * @param job     the job
         * @param reason  why
         */
        void rejected(int company, Job job, String reason);

        /**
         * @param company id of the company holding the job
         * @param job     the job
         * @param reason  why the dispute was opened
         */
        void disputed(int company, Job job, String reason);

        /**
         * @param company id of the company that held the job
         * @param job     the job
         * @param paid    what its workers were paid in all, 0 when nothing
         */
        void settled(int company, Job job, double paid);

        /**
         * @param company id of the company holding the job
         * @param job     the job whose owner withdrew their dispute
         */
        void withdrawn(int company, Job job);

        /**
         * @param company id of the company holding the job
         * @param job     the job as it was before
         * @param hours   the hours the owner added
         * @param due     when the company's time runs out now
         */
        void extended(int company, Job job, int hours, LocalDateTime due);

        /**
         * @param company id of the company holding the job
         * @param job     the job
         * @param reason  why the owner refused, null without a reason
         */
        void extensionRefused(int company, Job job, @Nullable String reason);
    }

    /**
     * Costs a company level progress, as failed orders do.
     */
    @FunctionalInterface
    public interface Penalties {
        /**
         * @param company id of the company
         * @param overdue true when its time ran out, false when it gave the job back
         */
        void penalize(int company, boolean overdue);
    }

    /**
     * What happened to a job.
     */
    public enum Notice {
        /**
         * A company took it.
         */
        TAKEN,
        /**
         * It is open again: the company gave it back or its time ran out.
         */
        RELEASED,
        /**
         * It checked out and was paid.
         */
        DONE,
        /**
         * Nobody took it in time.
         */
        EXPIRED,
        /**
         * The job's land changed hands, so it was cancelled.
         */
        LAND_LOST,
        /**
         * The company says the work is done; the owner should approve or reject it. The detail is when it is approved
         * without an answer.
         */
        AWAITING,
        /**
         * The owner did not answer in time, so the job was approved and paid.
         */
        AUTO_APPROVED,
        /**
         * A dispute was opened about it. The detail is the reason.
         */
        DISPUTED,
        /**
         * An admin settled its dispute. The detail is the money the owner got back.
         */
        SETTLED,
        /**
         * The company withdrew its dispute; it works on the job again.
         */
        WITHDRAWN,
        /**
         * A harvest job ended. The detail is a {@link Harvested}.
         */
        HARVEST_ENDED,
        /**
         * The company holding it asks for more time. The detail is an {@link Asked}.
         */
        EXTENSION_ASKED
    }

    /**
     * What an ended harvest job left its owner.
     *
     * @param back    the rest of the budget given back
     * @param stocked the items waiting in the stock
     */
    public record Harvested(double back, int stocked) {}

    /**
     * What the company holding a job asks for.
     *
     * @param hours  the hours it asks for
     * @param reason why
     * @param left   how long its time still runs
     */
    public record Asked(int hours, String reason, Duration left) {}

    /**
     * Why a job was not posted.
     */
    public enum Refusal {
        /**
         * Players may not post jobs on this server.
         */
        DISABLED,
        /**
         * The kind of job can not be posted yet.
         */
        KIND,
        /**
         * The title is blank or too long.
         */
        TITLE,
        /**
         * The price is not above 0 or too high.
         */
        PRICE,
        /**
         * The time to finish is below an hour or above the most the server allows.
         */
        DUE,
        /**
         * The area holds more blocks than the server allows.
         */
        VOLUME,
        /**
         * The owner can not pay price and fee.
         */
        FUNDS,
        /**
         * The area overlaps a job that waits or is worked on.
         */
        OVERLAP,
        /**
         * The area touches more chunks than the server allows.
         */
        CHUNKS,
        /**
         * The owner has as many jobs waiting as the server allows.
         */
        TOO_MANY,
        /**
         * The job could not be stored; the money went back.
         */
        STORAGE,
        /**
         * A terraform job has no target height inside its area, or another kind has one.
         */
        TARGET,
        /**
         * A harvest job has no item price between the lowest price and its budget, or another kind has one.
         */
        UNIT_PRICE,
        /**
         * A build job has no blueprint, or another kind has one.
         */
        BLUEPRINT,
        /**
         * The description is too long.
         */
        DESCRIPTION,
        /**
         * A build job was stored and taken, but its blueprint could not be stored; its money stays held for an admin.
         */
        STRANDED
    }

    /**
     * What came of posting a job.
     */
    public sealed interface Posted {
        /**
         * The job was posted.
         *
         * @param job the stored job
         */
        record Done(Job job) implements Posted {}

        /**
         * The job was not posted; nothing was charged.
         *
         * @param refusal why
         */
        record Refused(Refusal refusal) implements Posted {}
    }

    /**
     * What came of changing a job.
     */
    public enum Change {
        /**
         * The job changed.
         */
        DONE,
        /**
         * There is no such job.
         */
        UNKNOWN,
        /**
         * The member may not manage the company's work.
         */
        NO_PERMISSION,
        /**
         * The job is not open, expired, or another change came first.
         */
        CLOSED,
        /**
         * The company uses all its work slots.
         */
        LIMIT,
        /**
         * The job belongs to a member of the company.
         */
        OWN,
        /**
         * The job is not the company's, or not the player's.
         */
        NOT_YOURS,
        /**
         * A company holds the job, so its owner may not cancel or change it.
         */
        TAKEN,
        /**
         * The job is disputed and waits for an admin.
         */
        DISPUTED,
        /**
         * The reason is blank or too long.
         */
        REASON,
        /**
         * The description is too long.
         */
        TOO_LONG,
        /**
         * An area job is finished by checking its area, not by saying it is done.
         */
        AREA,
        /**
         * A contract is finished by saying it is done and the owner approving it, not by a check.
         */
        CONTRACT,
        /**
         * The company did not say the work is done.
         */
        NOT_DONE,
        /**
         * The company holding the job is gone, so it can get no share.
         */
        NO_COMPANY,
        /**
         * A harvest job is paid item by item and ends when its time or budget runs out or a side ends it; it has no
         * check, approval or dispute.
         */
        HARVEST,
        /**
         * The server lets no job get more time.
         */
        DISABLED,
        /**
         * The hours are below 1 or more than the job may still get.
         */
        HOURS,
        /**
         * The company holding the job asks for nothing right now.
         */
        NO_REQUEST,
        /**
         * The company already waits for an answer to its request.
         */
        ASKED,
        /**
         * The company asked as often as the server allows for this job.
         */
        REQUESTS
    }

    /**
     * What came of finishing a job.
     */
    public sealed interface Finished {
        /**
         * The job checked out and its workers were paid.
         *
         * @param paid what they got in all, after tax and treasury share
         */
        record Paid(double paid) implements Finished {}

        /**
         * The company ended a harvest job; the rest of the budget went back to the owner.
         */
        record Ended() implements Finished {}

        /**
         * The company said the contract is done; it waits for the owner.
         *
         * @param approveAt when it is approved without an answer
         */
        record Waiting(LocalDateTime approveAt) implements Finished {}

        /**
         * The check did not pass.
         *
         * @param progress how far the work is
         */
        record NotDone(DigCheck.Progress progress) implements Finished {}

        /**
         * The job could not be finished.
         *
         * @param change why
         */
        record Refused(Change change) implements Finished {}
    }

    /**
     * What a check of the primary did.
     *
     * @param expired  the open jobs that expired
     * @param reopened the taken jobs opened again
     * @param approved the jobs approved since their owner did not answer in time
     * @param ended    the harvest jobs whose time ran out
     */
    public record Check(List<Integer> expired, List<Integer> reopened, List<Integer> approved, List<Integer> ended) {
        /**
         * @return whether any job changed, so the access to the areas should be checked soon
         */
        public boolean changed() {
            return !expired.isEmpty() || !reopened.isEmpty() || !approved.isEmpty() || !ended.isEmpty();
        }
    }
}
