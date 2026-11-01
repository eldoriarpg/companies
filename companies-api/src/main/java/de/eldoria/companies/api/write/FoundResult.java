/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.api.write;

import de.eldoria.companies.components.company.ICompanyProfile;
import net.kyori.adventure.text.Component;

import java.util.Objects;

/**
 * The outcome of {@link de.eldoria.companies.api.CompanyApi#found(FoundRequest)}: the new company or why it was not
 * founded.
 */
public sealed interface FoundResult {
    /**
     * Whether the company was founded.
     *
     * @return true for {@link Founded}
     */
    boolean founded();

    /**
     * The company was founded.
     *
     * @param company the new company with its owner
     */
    record Founded(ICompanyProfile company) implements FoundResult {
        /**
         * Creates the result.
         */
        public Founded {
            Objects.requireNonNull(company, "company");
        }

        @Override
        public boolean founded() {
            return true;
        }
    }

    /**
     * The company was not founded and nothing was charged.
     *
     * @param problem why it was not founded
     * @param message the message a player would have been shown
     */
    record Rejected(FoundProblem problem, Component message) implements FoundResult {
        /**
         * Creates the result.
         */
        public Rejected {
            Objects.requireNonNull(problem, "problem");
            Objects.requireNonNull(message, "message");
        }

        @Override
        public boolean founded() {
            return false;
        }
    }
}
