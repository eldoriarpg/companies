/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements.systemorders;

/**
 * Where the money of a rule's orders comes from, and how much of it the rule may spend per day of the server's
 * calendar.
 */
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "RedundantNoArgConstructor"})
public class MoneySource {
    private SourceType type = SourceType.MINTED;
    private String account = "";
    private double dailyBudget = 0.0;

    public MoneySource() {}

    /**
     * @param type        where the money comes from
     * @param account     the economy account of {@link SourceType#ACCOUNT}
     * @param dailyBudget what the rule may spend per day, 0 for no budget of its own
     */
    public MoneySource(SourceType type, String account, double dailyBudget) {
        this.type = type;
        this.account = account;
        this.dailyBudget = dailyBudget;
    }

    /**
     * @return where the money comes from
     */
    public SourceType type() {
        return type == null ? SourceType.MINTED : type;
    }

    /**
     * The economy account paying the orders: a player's UUID, or an account name for economy plugins that know
     * accounts that are not players.
     *
     * @return the account, trimmed, empty when none is set
     */
    public String account() {
        return account == null ? "" : account.trim();
    }

    /**
     * @return what the rule may spend per day, 0 when only the account balance or the minted cap limits it
     */
    public double dailyBudget() {
        return Math.max(0, dailyBudget);
    }

    /**
     * Where money comes from.
     */
    public enum SourceType {
        /**
         * Withdrawn from an economy account when the order is placed and given back to it when the order is removed.
         */
        ACCOUNT,
        /**
         * Created when the workers are paid, within the daily cap of minted money.
         */
        MINTED
    }
}
