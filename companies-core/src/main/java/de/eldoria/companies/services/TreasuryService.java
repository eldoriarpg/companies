/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.company.ISimpleCompany;
import de.eldoria.companies.components.company.TreasuryKind;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.configuration.elements.companysettings.TreasurySettings;
import de.eldoria.companies.data.repository.ATreasuryData;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.treasury.Treasury;
import de.eldoria.companies.data.wrapper.treasury.TreasuryEntry;
import de.eldoria.companies.data.wrapper.treasury.TreasuryLogEntry;
import de.eldoria.companies.events.company.TreasuryTransactionEvent;
import de.eldoria.companies.services.notifications.NotificationArgs;
import de.eldoria.companies.services.notifications.NotificationService;
import de.eldoria.companies.services.notifications.NotificationType;
import de.eldoria.companies.util.Money;
import de.eldoria.companies.util.Threading;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Moves money into and out of company treasuries.
 * <p>
 * The database decides every change: a debit happens only when the balance covers it, so two servers can never pay
 * out the same money. Money leaving a treasury is debited before Vault pays it, money entering one is taken from the
 * player before it is credited, and a failed second step gives the money back. Vault is called on the main thread only.
 * Every stored transaction fires a {@link TreasuryTransactionEvent}.
 */
public final class TreasuryService {
    private final ATreasuryData data;
    private final Wallet wallet;
    private final Supplier<TreasurySettings> settings;
    private final Notifier notifier;
    private final Consumer<Event> events;
    private final Executor mainThread;
    private final Clock clock;
    private final Logger logger;

    /**
     * @param data       treasury repository
     * @param wallet     the players' money
     * @param settings   current treasury settings
     * @param notifier   notifies players
     * @param events     fires the transaction events
     * @param mainThread runs the wallet calls on the main thread
     * @param clock      clock of the log entries
     * @param logger     logger of failed side effects
     */
    public TreasuryService(
            ATreasuryData data,
            Wallet wallet,
            Supplier<TreasurySettings> settings,
            Notifier notifier,
            Consumer<Event> events,
            Executor mainThread,
            Clock clock,
            Logger logger) {
        this.data = data;
        this.wallet = wallet;
        this.settings = settings;
        this.notifier = notifier;
        this.events = events;
        this.mainThread = mainThread;
        this.clock = clock;
        this.logger = logger;
    }

    /**
     * The service of a running plugin, paying through Vault and notifying through the inbox.
     *
     * @param plugin        the plugin
     * @param data          treasury repository
     * @param economy       Vault economy
     * @param configuration configuration of the plugin
     * @param notifications notification service
     * @return the service
     */
    public static TreasuryService create(
            Plugin plugin,
            ATreasuryData data,
            Economy economy,
            Configuration configuration,
            NotificationService notifications) {
        return new TreasuryService(
                data,
                Wallet.of(economy),
                () -> configuration.companySettings().treasury(),
                notifications::send,
                plugin.getServer().getPluginManager()::callEvent,
                Threading.mainThread(plugin),
                Clock.systemDefaultZone(),
                plugin.getLogger());
    }

    /**
     * @return the current treasury settings
     */
    public TreasurySettings settings() {
        return settings.get();
    }

    /**
     * @param amount amount of money
     * @return the amount as the economy shows it
     */
    public String format(BigDecimal amount) {
        return wallet.format(amount.doubleValue());
    }

    /**
     * The treasury of a company.
     *
     * @param company id of the company
     * @return balance and share
     */
    public CompletableFuture<Treasury> treasury(int company) {
        return data.retrieveTreasury(company);
    }

    /**
     * The share of each payout a company keeps, limited by the current cap. A treasury that cannot be read takes no
     * share.
     *
     * @param company id of the company
     * @return share in percent
     */
    public CompletableFuture<Integer> effectiveShare(int company) {
        return data.retrieveTreasury(company)
                .thenApply(treasury -> settings.get().effectiveShare(treasury.payoutShare()))
                .exceptionally(err -> {
                    logger.log(Level.SEVERE, "Could not read the payout share of company " + company, err);
                    return 0;
                });
    }

    /**
     * One page of a company's treasury log, newest first.
     *
     * @param company id of the company
     * @param page    page, starting at 1
     * @return entries of the page
     */
    public CompletableFuture<List<TreasuryLogEntry>> log(int company, int page) {
        return data.retrieveLog(company, page, settings.get().logPageSize());
    }

    /**
     * A member pays money into the treasury. The money is taken from the member first and given back when it could
     * not be stored.
     *
     * @param company company of the member
     * @param member  paying member
     * @param amount  amount to pay in
     * @return the outcome with the new balance
     */
    public CompletableFuture<Result> deposit(CompanyProfile company, OfflinePlayer member, BigDecimal amount) {
        var refusal = refusal(company, member, null).or(() -> amountRefusal(amount));
        if (refusal.isPresent()) return CompletableFuture.completedFuture(refusal.get());
        var cents = Money.toCents(amount);
        var value = amount.doubleValue();
        var actor = member.getUniqueId();
        return CompletableFuture.supplyAsync(() -> wallet.withdraw(member, value), mainThread)
                .thenCompose(charged -> {
                    if (!charged) return CompletableFuture.completedFuture(Result.of(Outcome.PLAYER_FUNDS));
                    return credit(company, cents, entry(TreasuryKind.DEPOSIT, actor, null, null))
                            .thenCompose(balance -> balance.map(
                                            stored -> CompletableFuture.completedFuture(Result.done(stored)))
                                    .orElseGet(() -> payBack(member, value)));
                });
    }

    /**
     * A member with {@link CompanyPermission#TREASURY} takes money out of the treasury. The money is debited first,
     * so the balance can never be paid out twice, and credited back when Vault refuses the payment.
     *
     * @param company company of the member
     * @param member  member taking the money
     * @param amount  amount to take out
     * @return the outcome with the new balance
     */
    public CompletableFuture<Result> withdraw(CompanyProfile company, OfflinePlayer member, BigDecimal amount) {
        var refusal = refusal(company, member, CompanyPermission.TREASURY).or(() -> amountRefusal(amount));
        if (refusal.isPresent()) return CompletableFuture.completedFuture(refusal.get());
        var cents = Money.toCents(amount);
        var actor = member.getUniqueId();
        return debit(company, cents, entry(TreasuryKind.WITHDRAW, actor, null, null))
                .thenCompose(balance -> {
                    if (balance.isEmpty()) return CompletableFuture.completedFuture(Result.of(Outcome.TREASURY_FUNDS));
                    return CompletableFuture.supplyAsync(() -> wallet.deposit(member, amount.doubleValue()), mainThread)
                            .thenCompose(paid -> paid
                                    ? CompletableFuture.completedFuture(Result.done(balance.get()))
                                    : restore(company, cents, actor, "withdraw")
                                            .thenApply(restored -> Result.of(Outcome.FAILED)));
                });
    }

    /**
     * A member with {@link CompanyPermission#TREASURY} sets the share of payouts that goes to the treasury. Every
     * member is notified.
     *
     * @param company company of the member
     * @param member  member changing the share
     * @param percent new share in percent
     * @return the outcome with the balance
     */
    public CompletableFuture<Result> setShare(CompanyProfile company, OfflinePlayer member, int percent) {
        var refusal = refusal(company, member, CompanyPermission.TREASURY);
        if (refusal.isPresent()) return CompletableFuture.completedFuture(refusal.get());
        if (percent < 0 || percent > settings.get().maxPayoutShare()) {
            return CompletableFuture.completedFuture(Result.of(Outcome.SHARE_CAP));
        }
        var actor = member.getUniqueId();
        return data.submitShare(company.id(), percent, actor, now())
                .thenCompose(saved -> {
                    if (!saved) return CompletableFuture.completedFuture(Result.of(Outcome.FAILED));
                    notifyShare(company, member, percent);
                    return data.retrieveTreasury(company.id()).thenApply(treasury -> {
                        fire(company, TreasuryKind.SHARE_CHANGED, 0, treasury.cents(), actor);
                        return Result.done(treasury.cents());
                    });
                })
                .exceptionally(err -> failed("Could not change the payout share of company " + company.id(), err));
    }

    /**
     * Pays a cost of the company from its treasury. The cost is destroyed, like a cost paid by a player.
     *
     * @param company company paying
     * @param actor   member who chose to pay from the treasury
     * @param amount  cost
     * @param kind    what is paid, such as {@link TreasuryKind#RENAME}
     * @param note    note of the log entry, such as the new name
     * @return the outcome with the new balance; {@link Outcome#TREASURY_FUNDS} when the balance is too low
     */
    public CompletableFuture<Result> spend(
            ISimpleCompany company, UUID actor, BigDecimal amount, TreasuryKind kind, String note) {
        if (!settings.get().enabled()) return CompletableFuture.completedFuture(Result.of(Outcome.DISABLED));
        var cents = cents(amount);
        if (cents.isEmpty()) return CompletableFuture.completedFuture(Result.of(Outcome.INVALID_AMOUNT));
        return debit(company, cents.get(), entry(kind, actor, null, note))
                .thenApply(balance -> balance.map(Result::done).orElseGet(() -> Result.of(Outcome.TREASURY_FUNDS)));
    }

    /**
     * Gives money back to a treasury after what it paid for failed, such as a rename whose name was taken meanwhile.
     *
     * @param company company to refund
     * @param actor   member who caused the refund
     * @param amount  amount to give back
     * @param note    note of the log entry
     * @return the outcome with the new balance
     */
    public CompletableFuture<Result> refund(ISimpleCompany company, UUID actor, BigDecimal amount, String note) {
        var cents = cents(amount);
        if (cents.isEmpty()) return CompletableFuture.completedFuture(Result.of(Outcome.INVALID_AMOUNT));
        return restore(company, cents.get(), actor, note);
    }

    /**
     * Credits the company's share of the payouts of a completed order. A failed credit is logged with the amount; the
     * money is lost then.
     *
     * @param company company that completed the order
     * @param order   id of the order
     * @param cents   sum of the shares in cents
     * @return the outcome with the new balance
     */
    public CompletableFuture<Result> keepShare(ISimpleCompany company, int order, long cents) {
        if (cents <= 0) return CompletableFuture.completedFuture(Result.of(Outcome.INVALID_AMOUNT));
        return credit(company, cents, entry(TreasuryKind.PAYOUT_SHARE, null, order, null))
                .thenApply(balance -> balance.map(Result::done).orElseGet(() -> {
                    logger.severe(
                            "Could not credit the treasury share of %s of order %d to company %d. The money is lost."
                                    .formatted(Money.fromCents(cents), order, company.id()));
                    return Result.of(Outcome.FAILED);
                }));
    }

    /**
     * Empties the treasury of a company that is about to be disbanded or deleted.
     *
     * @param company company to empty
     * @param actor   owner or admin dissolving it
     * @return the balance that was taken out, in cents; 0 for an empty treasury or when it could not be emptied
     */
    public CompletableFuture<Long> drain(ISimpleCompany company, @Nullable UUID actor) {
        return data.submitDrain(company.id(), entry(TreasuryKind.DISBAND, actor, null, null))
                .exceptionally(err -> {
                    logger.log(Level.SEVERE, "Could not empty the treasury of company " + company.id(), err);
                    return Optional.empty();
                })
                .thenApply(drained -> {
                    var cents = drained.orElse(0L);
                    if (cents > 0) fire(company, TreasuryKind.DISBAND, -cents, 0, actor);
                    return cents;
                });
    }

    /**
     * Pays the emptied treasury of a disbanded or deleted company to its owner, who is notified. The payout is logged;
     * when Vault refuses it, the amount is logged as lost.
     *
     * @param company dissolved company
     * @param cents   amount taken out of its treasury
     * @return true when the owner was paid
     */
    public CompletableFuture<Boolean> payOwner(CompanyProfile company, long cents) {
        if (cents <= 0) return CompletableFuture.completedFuture(false);
        var owner = company.owner().player();
        var amount = Money.fromCents(cents);
        return CompletableFuture.supplyAsync(
                () -> {
                    if (!wallet.deposit(owner, amount.doubleValue())) {
                        logger.severe(
                                "Could not pay the treasury of %s of company %s (%d) to its owner %s. The money is lost."
                                        .formatted(amount, company.name(), company.id(), owner.getUniqueId()));
                        return false;
                    }
                    logger.info("Paid the treasury of %s of company %s (%d) to its owner %s (%s)."
                            .formatted(amount, company.name(), company.id(), owner.getName(), owner.getUniqueId()));
                    notifier.send(
                            owner.getUniqueId(),
                            NotificationType.TREASURY_DISBAND_PAYOUT,
                            NotificationArgs.create()
                                    .money("amount", wallet.format(amount.doubleValue()))
                                    .text("company_name", company.name()));
                    return true;
                },
                mainThread);
    }

    /**
     * Puts the balance of a company back after it could not be dissolved.
     *
     * @param company company that still exists
     * @param cents   amount taken out of its treasury
     * @param actor   owner or admin who tried to dissolve it
     * @return the outcome with the new balance
     */
    public CompletableFuture<Result> undoDrain(ISimpleCompany company, long cents, @Nullable UUID actor) {
        if (cents <= 0) return CompletableFuture.completedFuture(Result.of(Outcome.INVALID_AMOUNT));
        return restore(company, cents, actor, "disband");
    }

    /**
     * An admin adds money to or removes money from a treasury.
     *
     * @param company company of the treasury
     * @param admin   admin changing it
     * @param give    true to add money, false to remove it
     * @param amount  amount to add or remove
     * @return the outcome with the new balance; {@link Outcome#TREASURY_FUNDS} when more is removed than there is
     */
    public CompletableFuture<Result> adminAdjust(ISimpleCompany company, UUID admin, boolean give, BigDecimal amount) {
        var cents = cents(amount);
        if (cents.isEmpty()) return CompletableFuture.completedFuture(Result.of(Outcome.INVALID_AMOUNT));
        var change = give
                ? credit(company, cents.get(), entry(TreasuryKind.ADMIN_GIVE, admin, null, null))
                : debit(company, cents.get(), entry(TreasuryKind.ADMIN_TAKE, admin, null, null));
        return change.thenApply(balance ->
                balance.map(Result::done).orElseGet(() -> Result.of(give ? Outcome.FAILED : Outcome.TREASURY_FUNDS)));
    }

    /**
     * Converts an amount to cents when it is above zero and has at most two decimals.
     *
     * @param amount amount to convert
     * @return the amount in cents, or empty when it is not a valid amount of money
     */
    static Optional<Long> cents(BigDecimal amount) {
        if (amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 2) return Optional.empty();
        try {
            return Optional.of(Money.toCents(amount));
        } catch (ArithmeticException e) {
            return Optional.empty();
        }
    }

    private Optional<Result> refusal(
            CompanyProfile company, OfflinePlayer member, @Nullable CompanyPermission permission) {
        if (!settings.get().enabled()) return Optional.of(Result.of(Outcome.DISABLED));
        var found = company.member(member);
        if (found.isEmpty()) return Optional.of(Result.of(Outcome.NO_MEMBER));
        if (permission != null && !found.get().hasPermission(permission)) {
            return Optional.of(Result.of(Outcome.NO_PERMISSION));
        }
        return Optional.empty();
    }

    private Optional<Result> amountRefusal(BigDecimal amount) {
        var minimum = BigDecimal.valueOf(settings.get().minTransaction());
        if (cents(amount).isEmpty() || amount.compareTo(minimum) < 0) {
            return Optional.of(Result.of(Outcome.INVALID_AMOUNT));
        }
        return Optional.empty();
    }

    private CompletableFuture<Result> payBack(OfflinePlayer member, double value) {
        return CompletableFuture.supplyAsync(
                () -> {
                    if (!wallet.deposit(member, value)) {
                        logger.severe("Could not give %s back to %s after a failed treasury deposit."
                                .formatted(value, member.getUniqueId()));
                    }
                    return Result.of(Outcome.FAILED);
                },
                mainThread);
    }

    private CompletableFuture<Result> restore(ISimpleCompany company, long cents, @Nullable UUID actor, String note) {
        return credit(company, cents, entry(TreasuryKind.REFUND, actor, null, note))
                .thenApply(balance -> balance.map(Result::done).orElseGet(() -> {
                    logger.severe("Could not give %s back to the treasury of company %d (%s). The money is lost."
                            .formatted(Money.fromCents(cents), company.id(), note));
                    return Result.of(Outcome.FAILED);
                }));
    }

    private CompletableFuture<Optional<Long>> credit(ISimpleCompany company, long cents, TreasuryEntry entry) {
        return stored(company, cents, entry, data.submitCredit(company.id(), cents, entry));
    }

    private CompletableFuture<Optional<Long>> debit(ISimpleCompany company, long cents, TreasuryEntry entry) {
        return stored(company, -cents, entry, data.submitDebit(company.id(), cents, entry));
    }

    private CompletableFuture<Optional<Long>> stored(
            ISimpleCompany company, long signed, TreasuryEntry entry, CompletableFuture<Optional<Long>> change) {
        return change.exceptionally(err -> {
                    logger.log(Level.SEVERE, "Could not change the treasury of company " + company.id(), err);
                    return Optional.empty();
                })
                .thenApply(balance -> {
                    balance.ifPresent(cents -> fire(company, entry.kind(), signed, cents, entry.actor()));
                    return balance;
                });
    }

    private void fire(ISimpleCompany company, TreasuryKind kind, long signed, long balance, @Nullable UUID actor) {
        try {
            events.accept(new TreasuryTransactionEvent(
                    company, kind, Money.fromCents(signed), Money.fromCents(balance), actor));
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "A listener of a treasury transaction failed", e);
        }
    }

    private void notifyShare(CompanyProfile company, OfflinePlayer member, int percent) {
        var args = NotificationArgs.create()
                .text("player_name", String.valueOf(member.getName()))
                .number("percent", percent);
        for (var recipient : company.members()) {
            notifier.send(recipient.uuid(), NotificationType.TREASURY_SHARE_CHANGED, args);
        }
    }

    private TreasuryEntry entry(
            TreasuryKind kind, @Nullable UUID actor, @Nullable Integer order, @Nullable String note) {
        return new TreasuryEntry(kind, actor, order, note, now());
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private Result failed(String message, Throwable err) {
        logger.log(Level.SEVERE, message, err);
        return Result.of(Outcome.FAILED);
    }

    /**
     * How a treasury action ended.
     */
    public enum Outcome {
        /**
         * The action was stored.
         */
        DONE,
        /**
         * The treasury is disabled.
         */
        DISABLED,
        /**
         * The player is not a member of the company.
         */
        NO_MEMBER,
        /**
         * The member lacks {@link CompanyPermission#TREASURY}.
         */
        NO_PERMISSION,
        /**
         * The amount is below the minimum, not positive or has more than two decimals.
         */
        INVALID_AMOUNT,
        /**
         * The player cannot afford the amount.
         */
        PLAYER_FUNDS,
        /**
         * The treasury balance does not cover the amount.
         */
        TREASURY_FUNDS,
        /**
         * The share is above the configured cap.
         */
        SHARE_CAP,
        /**
         * Saving or paying failed; nothing was charged.
         */
        FAILED
    }

    /**
     * The outcome of a treasury action.
     *
     * @param outcome how it ended
     * @param cents   balance after the action in cents, 0 unless it is {@link Outcome#DONE}
     */
    public record Result(Outcome outcome, long cents) {
        /**
         * @param outcome how it ended
         * @return a result without balance
         */
        public static Result of(Outcome outcome) {
            return new Result(outcome, 0);
        }

        /**
         * @param cents balance after the action in cents
         * @return a successful result
         */
        public static Result done(long cents) {
            return new Result(Outcome.DONE, cents);
        }

        /**
         * @return true when the action was stored
         */
        public boolean done() {
            return outcome == Outcome.DONE;
        }

        /**
         * @return balance after the action
         */
        public BigDecimal balance() {
            return Money.fromCents(cents);
        }
    }

    /**
     * Sends notifications.
     */
    @FunctionalInterface
    public interface Notifier {
        /**
         * @param recipient player to notify
         * @param type      kind of notification
         * @param args      values of the notification
         */
        void send(UUID recipient, NotificationType type, NotificationArgs args);
    }

    /**
     * The players' money. Called on the main thread.
     */
    public interface Wallet {
        /**
         * A wallet backed by the Vault economy.
         *
         * @param economy the economy
         * @return the wallet
         */
        static Wallet of(Economy economy) {
            return new Wallet() {
                @Override
                public boolean withdraw(OfflinePlayer player, double amount) {
                    return economy.has(player, amount)
                            && economy.withdrawPlayer(player, amount).transactionSuccess();
                }

                @Override
                public boolean deposit(OfflinePlayer player, double amount) {
                    return economy.depositPlayer(player, amount).transactionSuccess();
                }

                @Override
                public String format(double amount) {
                    return economy.format(amount);
                }
            };
        }

        /**
         * Takes money from a player.
         *
         * @param player player to charge
         * @param amount amount to take
         * @return false when the player cannot afford it; nothing was taken then
         */
        boolean withdraw(OfflinePlayer player, double amount);

        /**
         * Gives money to a player.
         *
         * @param player player to pay
         * @param amount amount to give
         * @return false when the economy refused the payment
         */
        boolean deposit(OfflinePlayer player, double amount);

        /**
         * @param amount amount of money
         * @return the amount as the economy shows it
         */
        String format(double amount);
    }
}
