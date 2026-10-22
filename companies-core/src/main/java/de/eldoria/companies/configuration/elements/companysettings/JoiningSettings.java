/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements.companysettings;

import de.eldoria.companies.components.company.JoinMode;

/**
 * How players become members of a company: the join mode of new companies and the limits of applications.
 */
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "RedundantNoArgConstructor"})
public class JoiningSettings {
    private JoinMode defaultMode = JoinMode.INVITE_ONLY;
    private int maxApplicationLength = 120;
    private int maxPendingApplications = 3;

    public JoiningSettings() {}

    /**
     * The join mode of companies founded from now on. Existing companies keep their mode.
     *
     * @return join mode
     */
    public JoinMode defaultMode() {
        return defaultMode == null ? JoinMode.INVITE_ONLY : defaultMode;
    }

    public void defaultMode(JoinMode defaultMode) {
        this.defaultMode = defaultMode;
    }

    /**
     * The longest message a player may send with an application.
     *
     * @return maximum number of characters
     */
    public int maxApplicationLength() {
        return maxApplicationLength;
    }

    public void maxApplicationLength(int maxApplicationLength) {
        this.maxApplicationLength = maxApplicationLength;
    }

    /**
     * How many applications a player may have open at the same time, across all companies.
     *
     * @return maximum number of open applications
     */
    public int maxPendingApplications() {
        return maxPendingApplications;
    }

    public void maxPendingApplications(int maxPendingApplications) {
        this.maxPendingApplications = maxPendingApplications;
    }
}
