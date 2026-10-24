/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements.companysettings;

/**
 * The company treasury: whether it is used, how much of each payout companies may keep in it, and how long its log
 * is kept.
 */
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "RedundantNoArgConstructor"})
public class TreasurySettings {
    private boolean enabled = true;
    private int maxPayoutShare = 25;
    private double minTransaction = 1.0;
    private boolean payRename = true;
    private int logRetentionDays = 365;
    private int logPageSize = 10;

    public TreasurySettings() {}

    /**
     * Whether companies can use the treasury. When disabled the bank commands are hidden and the payout share counts
     * as 0; balances are kept.
     *
     * @return true when the treasury is enabled
     */
    public boolean enabled() {
        return enabled;
    }

    public void enabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * The highest share of each worker's payout a company may keep in its treasury.
     *
     * @return percentage from 0 to 100
     */
    public int maxPayoutShare() {
        return Math.clamp(maxPayoutShare, 0, 100);
    }

    public void maxPayoutShare(int maxPayoutShare) {
        this.maxPayoutShare = maxPayoutShare;
    }

    /**
     * A company's payout share limited by the current cap, so lowering the cap applies at once. A disabled treasury
     * takes no share.
     *
     * @param share share the company chose
     * @return share to apply, in percent
     */
    public int effectiveShare(int share) {
        if (!enabled) return 0;
        return Math.clamp(share, 0, maxPayoutShare());
    }

    /**
     * The smallest amount that can be deposited or withdrawn.
     *
     * @return minimum amount
     */
    public double minTransaction() {
        return Math.max(0.01, minTransaction);
    }

    public void minTransaction(double minTransaction) {
        this.minTransaction = minTransaction;
    }

    /**
     * Whether the owner may pay a rename from the treasury.
     *
     * @return true when renames can be paid from the treasury
     */
    public boolean payRename() {
        return payRename;
    }

    public void payRename(boolean payRename) {
        this.payRename = payRename;
    }

    /**
     * How long treasury log entries are kept.
     *
     * @return days, 0 to keep them forever
     */
    public int logRetentionDays() {
        return Math.max(0, logRetentionDays);
    }

    public void logRetentionDays(int logRetentionDays) {
        this.logRetentionDays = logRetentionDays;
    }

    /**
     * How many log entries one page shows.
     *
     * @return entries per page, from 1 to 50
     */
    public int logPageSize() {
        return Math.clamp(logPageSize, 1, 50);
    }

    public void logPageSize(int logPageSize) {
        this.logPageSize = logPageSize;
    }
}
