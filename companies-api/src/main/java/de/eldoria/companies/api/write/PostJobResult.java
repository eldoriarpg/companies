/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.api.write;

import de.eldoria.companies.components.job.IJob;

import java.util.Objects;

/**
 * The outcome of {@link de.eldoria.companies.api.JobApi#post(JobRequest)}: the posted job or why it was not posted.
 */
public sealed interface PostJobResult {
    /**
     * Whether the job was posted.
     *
     * @return true for {@link Posted}
     */
    boolean posted();

    /**
     * The job was posted and waits for a company.
     *
     * @param job the stored job
     */
    record Posted(IJob job) implements PostJobResult {
        /**
         * Creates the result.
         */
        public Posted {
            Objects.requireNonNull(job, "job");
        }

        @Override
        public boolean posted() {
            return true;
        }
    }

    /**
     * The job was not posted. Nothing was charged, except for {@link JobProblem#STRANDED}.
     *
     * @param problem why it was not posted
     */
    record Rejected(JobProblem problem) implements PostJobResult {
        /**
         * Creates the result.
         */
        public Rejected {
            Objects.requireNonNull(problem, "problem");
        }

        @Override
        public boolean posted() {
            return false;
        }
    }
}
