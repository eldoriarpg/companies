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
import de.eldoria.companies.data.wrapper.job.JobKind;
import de.eldoria.companies.data.wrapper.job.JobLand;
import de.eldoria.companies.data.wrapper.job.JobState;
import de.eldoria.companies.orders.Payout;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * the owner cancelling it while nobody works on it, and paying the company when the work checks out. The primary
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
        var current = settings.get();
        if (!current.enabled()) return new Posted.Refused(Refusal.DISABLED);
        if (kind != JobKind.DIG) return new Posted.Refused(Refusal.KIND);
        var name = title.strip();
        if (name.isEmpty() || name.length() > Job.MAX_TITLE) return new Posted.Refused(Refusal.TITLE);
        var rounded = cents(price);
        if (!(rounded >= MIN_PRICE) || rounded > MAX_PRICE) return new Posted.Refused(Refusal.PRICE);
        if (dueHours < 1 || dueHours > current.maxDueHours()) return new Posted.Refused(Refusal.DUE);
        if (area.volume() > current.digMaxVolume()) return new Posted.Refused(Refusal.VOLUME);
        if (area.chunks() > current.maxChunks()) return new Posted.Refused(Refusal.CHUNKS);
        if (jobs.openOf(owner) >= current.maxOpenPerOwner()) return new Posted.Refused(Refusal.TOO_MANY);
        if (!jobs.overlapping(area).isEmpty()) return new Posted.Refused(Refusal.OVERLAP);
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
                now.plusDays(current.openDays()));
        Optional<Integer> id;
        try {
            id = jobs.create(draft);
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Could not store a job of " + owner, e);
            id = Optional.empty();
        }
        if (id.isEmpty()) {
            money.deposit(owner, total);
            return new Posted.Refused(Refusal.STORAGE);
        }
        var stored = id.get();
        return new Posted.Done(jobs.job(stored).orElseGet(() -> draft.withId(stored)));
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
        if (job.state() != JobState.TAKEN || !Integer.valueOf(company.id()).equals(job.company())) {
            return Change.NOT_YOURS;
        }
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
            money.deposit(job.owner(), job.price());
            money.collect(job.fee());
            notices.owner(job.owner(), Notice.LAND_LOST, job, null);
            return true;
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
        if (job.state() == JobState.TAKEN) return Change.TAKEN;
        if (job.state() != JobState.OPEN) return Change.CLOSED;
        if (!jobs.close(id, JobState.OPEN, JobState.CANCELLED, by, now())) return Change.CLOSED;
        money.deposit(owner, job.price());
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
     * Expires the open jobs nobody took, giving price and fee back, and opens the taken jobs whose time ran out again.
     * Runs on the primary.
     *
     * @return the jobs that expired and the jobs opened again
     */
    public Check check() {
        var now = now();
        List<Integer> expired = new ArrayList<>();
        List<Integer> reopened = new ArrayList<>();
        for (var job : jobs.expired(now)) {
            try {
                if (!jobs.close(job.id(), JobState.OPEN, JobState.EXPIRED, null, now)) continue;
                var back = cents(job.price() + job.fee());
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
                if (!jobs.release(job.id(), company)) continue;
                notices.owner(job.owner(), Notice.RELEASED, job, null);
                notices.overdue(company, job);
                penalties.penalize(company, true);
                reopened.add(job.id());
            } catch (RuntimeException e) {
                logger.log(Level.SEVERE, "Could not open overdue job " + job.id() + " again", e);
            }
        }
        return new Check(expired, reopened);
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
         * @param detail the due time of a taken job, the money of an expired one, null otherwise
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
        LAND_LOST
    }

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
        STORAGE
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
         * A company works on the job, so its owner may not cancel it.
         */
        TAKEN
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
     */
    public record Check(List<Integer> expired, List<Integer> reopened) {}
}
