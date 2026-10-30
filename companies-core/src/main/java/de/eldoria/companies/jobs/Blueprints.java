/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs;

import de.eldoria.companies.data.repository.AJobData;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntPredicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The blueprints of build jobs, read from the database once each, as a job's blueprint never changes, and forgotten
 * when their job no longer runs on this server. Blocks; call it
 * off the main thread the first time for a job, see {@link #known(int)} for the main thread.
 */
public final class Blueprints {
    private final AJobData jobs;
    private final Logger logger;
    private final Map<Integer, Optional<Blueprint>> read = new ConcurrentHashMap<>();

    /**
     * @param jobs   job repository
     * @param logger reports blueprints that can not be read
     */
    public Blueprints(AJobData jobs, Logger logger) {
        this.jobs = jobs;
        this.logger = logger;
    }

    /**
     * @param job id of a job
     * @return its blueprint, empty for a job without one or when it can not be read now, which is tried again next
     *     time
     */
    public Optional<Blueprint> of(int job) {
        var known = read.get(job);
        if (known != null) return known;
        Optional<Blueprint> found;
        try {
            found = jobs.blueprint(job).map(Blueprint::decode);
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "Could not read the blueprint of job " + job, e);
            return Optional.empty();
        }
        if (found.isPresent()) read.put(job, found);
        return found;
    }

    /**
     * @param job id of a job
     * @return its blueprint when it was read before, without touching the database
     */
    public Optional<Blueprint> known(int job) {
        return read.getOrDefault(job, Optional.empty());
    }

    /**
     * Forgets the blueprints of jobs that ended.
     *
     * @param running whether a job still runs, by its id
     */
    public void prune(IntPredicate running) {
        read.keySet().removeIf(id -> !running.test(id));
    }
}
