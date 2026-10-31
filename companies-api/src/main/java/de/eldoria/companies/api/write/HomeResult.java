/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.api.write;

import de.eldoria.companies.components.area.ICompanyHome;
import net.kyori.adventure.text.Component;

import java.util.Objects;

/**
 * The outcome of {@link de.eldoria.companies.api.CompanyApi#setHome(HomeRequest)}: the stored home or why it was not
 * set.
 */
public sealed interface HomeResult {
    /**
     * Whether the home was set.
     *
     * @return true for {@link Stored}
     */
    boolean stored();

    /**
     * The home was set; it replaced the company's former home.
     *
     * @param home the stored home
     */
    record Stored(ICompanyHome home) implements HomeResult {
        /**
         * Creates the result.
         */
        public Stored {
            Objects.requireNonNull(home, "home");
        }

        @Override
        public boolean stored() {
            return true;
        }
    }

    /**
     * The home was not set.
     *
     * @param problem why it was not set
     * @param message the message a player would have been shown
     */
    record Rejected(HomeProblem problem, Component message) implements HomeResult {
        /**
         * Creates the result.
         */
        public Rejected {
            Objects.requireNonNull(problem, "problem");
            Objects.requireNonNull(message, "message");
        }

        @Override
        public boolean stored() {
            return false;
        }
    }
}
