/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.api.write;

import de.eldoria.companies.components.event.IServerEvent;

import java.util.Objects;

/**
 * The outcome of {@link de.eldoria.companies.api.ServerEventApi#create}: the created event or why it was not created.
 */
public sealed interface CreateEventResult {
    /**
     * Whether the event was created.
     *
     * @return true for {@link Created}
     */
    boolean created();

    /**
     * The event was created and waits for its start.
     *
     * @param event the stored event
     */
    record Created(IServerEvent event) implements CreateEventResult {
        /**
         * Creates the result.
         */
        public Created {
            Objects.requireNonNull(event, "event");
        }

        @Override
        public boolean created() {
            return true;
        }
    }

    /**
     * The event was not created.
     *
     * @param problem why it was not created
     */
    record Rejected(EventProblem problem) implements CreateEventResult {
        /**
         * Creates the result.
         */
        public Rejected {
            Objects.requireNonNull(problem, "problem");
        }

        @Override
        public boolean created() {
            return false;
        }
    }
}
