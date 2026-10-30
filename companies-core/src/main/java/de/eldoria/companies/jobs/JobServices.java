/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs;

import de.eldoria.companies.data.repository.AJobData;
import de.eldoria.companies.jobs.hooks.JobHooks;

import java.util.UUID;

/**
 * What the commands of jobs work with.
 *
 * @param data       the jobs
 * @param service    posts, takes, gives back, cancels and finishes jobs
 * @param hooks      the land and selection plugins
 * @param access     keeps the access to the areas of this server right
 * @param progress   reads how far the work on a job of this server is
 * @param node       this server, which holds the areas of the jobs posted here
 * @param blueprints the blueprints of build jobs
 * @param ghosts     shows the missing blocks of build jobs
 */
public record JobServices(
        AJobData data,
        JobService service,
        JobHooks hooks,
        JobAccess access,
        JobProgress progress,
        UUID node,
        Blueprints blueprints,
        BuildGhosts ghosts) {}
