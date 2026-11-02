/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.job;

import de.eldoria.companies.data.wrapper.job.Job;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;

/**
 * Texts the job menus share.
 */
public final class JobMenus {
    private JobMenus() {}

    /**
     * @param job a job
     * @return the job in a few words: its id and title
     */
    public static MenuText name(Job job) {
        return MenuText.create()
                .text("#<id> <job_title>", Values.number("id", job.id()), Values.text("job_title", job.title()));
    }
}
