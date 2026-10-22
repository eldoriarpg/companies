/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.company;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * An open application of a player to a company.
 *
 * @param id          id of the application
 * @param company     id of the company applied to
 * @param companyName name of the company applied to
 * @param applicant   applying player
 * @param message     message of the applicant, stored as entered
 * @param created     when the application was sent
 * @param expires     when the application expires
 */
public record Application(
        int id,
        int company,
        String companyName,
        UUID applicant,
        String message,
        LocalDateTime created,
        LocalDateTime expires) {}
