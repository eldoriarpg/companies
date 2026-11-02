/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.job;

import de.eldoria.companies.data.wrapper.job.Job;
import de.eldoria.companies.data.wrapper.job.JobReviewEntry;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.services.events.EventTimes;
import de.eldoria.companies.util.Times;

import java.util.List;
import java.util.Objects;

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

    /**
     * The most steps of a review shown, the latest ones.
     */
    public static final int REVIEWS_SHOWN = 5;

    /**
     * @param prefix  the message prefix of the command showing them, such as {@code order.job.}
     * @param reviews the steps of the review of a job's work, oldest first
     * @return one line for each of the latest {@value #REVIEWS_SHOWN} steps, oldest first
     */
    public static List<MenuText> reviews(String prefix, List<JobReviewEntry> reviews) {
        return reviews.subList(Math.max(0, reviews.size() - REVIEWS_SHOWN), reviews.size()).stream()
                .map(entry -> switch (entry.kind()) {
                    case DONE ->
                        MenuText.create()
                                .locale(
                                        prefix + "info.reviewDone",
                                        Values.text("time", EventTimes.show(entry.at(), Times.zone())));
                    case REJECTED ->
                        MenuText.create()
                                .locale(
                                        prefix + "info.reviewRejected",
                                        Values.text("time", EventTimes.show(entry.at(), Times.zone())),
                                        Values.text("reason", Objects.requireNonNullElse(entry.reason(), "")));
                })
                .toList();
    }
}
