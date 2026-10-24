/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements;

/**
 * Where fees and taxes go.
 */
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "RedundantNoArgConstructor"})
public class EconomySettings {
    private String collectorAccount = "";

    public EconomySettings() {}

    /**
     * The account receiving fees and taxes: a player UUID or a Vault account name. When empty, the money is destroyed.
     *
     * @return account identifier, empty for none
     */
    public String collectorAccount() {
        return collectorAccount == null ? "" : collectorAccount.trim();
    }

    public void collectorAccount(String collectorAccount) {
        this.collectorAccount = collectorAccount;
    }
}
