/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.api.write;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * An order to create with {@link de.eldoria.companies.api.OrderApi#create(OrderRequest)}.
 * <p>
 * Build one with {@link #builder(UUID, String)}.
 *
 * @param owner         id of the player who owns the order; they receive the items and may cancel it
 * @param name          name of the order
 * @param lines         requested items, at least one
 * @param charge        whether the owner pays for the order, see {@link #charge()}
 * @param respectLimits whether the limits of a player apply, see {@link #respectLimits()}
 */
public record OrderRequest(
        UUID owner, String name, List<OrderLineRequest> lines, boolean charge, boolean respectLimits) {
    /**
     * Validates the request and copies the lines.
     *
     * @throws IllegalArgumentException when the name is blank or there are no lines
     */
    public OrderRequest {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) throw new IllegalArgumentException("The name must not be blank");
        lines = List.copyOf(lines);
        if (lines.isEmpty()) throw new IllegalArgumentException("An order needs at least one line");
    }

    /**
     * Starts a request that charges the owner and respects the limits, like an order a player creates.
     *
     * @param owner id of the player who owns the order
     * @param name  name of the order
     * @return a new builder
     */
    public static Builder builder(UUID owner, String name) {
        return new Builder(owner, name);
    }

    /**
     * Whether the owner pays for the order.
     * <p>
     * <b>{@code true}:</b> the owner pays the price and the order fee like for {@code /order create}. The money is
     * withdrawn on the main thread; an owner who can not afford it gets the order rejected with
     * {@link RejectReason#INSUFFICIENT_FUNDS}. When the order can not be saved afterwards the money is refunded.
     * Cancelling and removing the order refund the owner as usual.
     * <p>
     * <b>{@code false}:</b> the server funds the order. Nothing is withdrawn and the order stores a fee of 0. The
     * workers are still paid the full price when the order is completed, so the money is created at that point.
     * Cancelling or removing the order refunds nothing, because nothing was paid. Use this for rewards and quests.
     *
     * @return whether the owner is charged
     */
    @Override
    public boolean charge() {
        return charge;
    }

    /**
     * Whether the limits of a player apply to the order.
     * <p>
     * <b>{@code true}:</b> the owner's limit of active orders applies (their permission-based limit while they are
     * online, otherwise the configured one), as well as the configured maximum of lines and items per order. A
     * request over a limit is rejected with {@link RejectReason#LIMIT_REACHED}, {@link RejectReason#TOO_MANY_LINES}
     * or {@link RejectReason#TOO_MANY_ITEMS}.
     * <p>
     * <b>{@code false}:</b> none of these limits apply. The name length and the validity of the lines are checked
     * either way.
     *
     * @return whether the limits apply
     */
    @Override
    public boolean respectLimits() {
        return respectLimits;
    }

    /**
     * The sum of the line prices.
     *
     * @return price of the order before the server rounds it, without the fee
     */
    public double price() {
        return lines.stream().mapToDouble(OrderLineRequest::price).sum();
    }

    /**
     * Builds an {@link OrderRequest}. It charges the owner and respects the limits unless told otherwise.
     */
    public static final class Builder {
        private final UUID owner;
        private final String name;
        private final List<OrderLineRequest> lines = new ArrayList<>();
        private boolean charge = true;
        private boolean respectLimits = true;

        private Builder(UUID owner, String name) {
            this.owner = Objects.requireNonNull(owner, "owner");
            this.name = Objects.requireNonNull(name, "name");
        }

        /**
         * Adds a line.
         *
         * @param line line to add
         * @return this builder
         */
        public Builder line(OrderLineRequest line) {
            lines.add(Objects.requireNonNull(line, "line"));
            return this;
        }

        /**
         * Sets who pays for the order. The default is {@code true}.
         *
         * @param charge true to charge the owner price and fee, false to fund the order by the server
         * @return this builder
         * @see OrderRequest#charge()
         */
        public Builder charge(boolean charge) {
            this.charge = charge;
            return this;
        }

        /**
         * Sets whether the limits of a player apply. The default is {@code true}.
         *
         * @param respectLimits true to apply the limits
         * @return this builder
         * @see OrderRequest#respectLimits()
         */
        public Builder respectLimits(boolean respectLimits) {
            this.respectLimits = respectLimits;
            return this;
        }

        /**
         * Builds the request.
         *
         * @return the request
         * @throws IllegalArgumentException when the name is blank or no line was added
         */
        public OrderRequest build() {
            return new OrderRequest(owner, name, lines, charge, respectLimits);
        }
    }
}
