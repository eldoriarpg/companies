/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.company;

import de.eldoria.companies.components.company.ISimpleCompany;
import de.eldoria.companies.components.company.TreasuryKind;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Money moved into or out of a company treasury.
 * <p>
 * Fired after the transaction was saved, usually off the main thread; the event may be asynchronous. It fires only on
 * the server where the transaction happened. Payout shares carry no actor, so workers stay anonymous towards the
 * order owner and the order owner towards the company.
 */
public class TreasuryTransactionEvent extends CompanyEvent<ISimpleCompany> {
    private static final HandlerList HANDLERS = new HandlerList();

    private final TreasuryKind kind;
    private final BigDecimal amount;
    private final BigDecimal balance;

    @Nullable
    private final UUID actor;

    /**
     * Creates a new event. It is asynchronous when created off the main thread.
     *
     * @param company company of the treasury
     * @param kind    what moved the money
     * @param amount  signed amount: positive into the treasury, negative out of it
     * @param balance balance after the transaction
     * @param actor   member or admin who caused it, or null for payout shares and system actions
     */
    public TreasuryTransactionEvent(
            ISimpleCompany company, TreasuryKind kind, BigDecimal amount, BigDecimal balance, @Nullable UUID actor) {
        super(company, !Bukkit.isPrimaryThread());
        this.kind = Objects.requireNonNull(kind, "kind");
        this.amount = Objects.requireNonNull(amount, "amount");
        this.balance = Objects.requireNonNull(balance, "balance");
        this.actor = actor;
    }

    /**
     * The handlers of this event.
     *
     * @return handler list
     */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    /**
     * What moved the money.
     *
     * @return kind of transaction
     */
    public TreasuryKind kind() {
        return kind;
    }

    /**
     * The amount that moved.
     *
     * @return positive when money went into the treasury, negative when it left it, 0 for a share change
     */
    public BigDecimal amount() {
        return amount;
    }

    /**
     * The balance after the transaction.
     *
     * @return balance
     */
    public BigDecimal balance() {
        return balance;
    }

    /**
     * The member or admin who caused the transaction.
     *
     * @return id of the player, or empty for payout shares and system actions
     */
    public Optional<UUID> actor() {
        return Optional.ofNullable(actor);
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
