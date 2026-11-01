/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs;

import de.eldoria.companies.services.notifications.NotificationType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JobsTest {
    @Test
    void ownersOfUnchargedJobsAreNotToldMoneyWentBack() {
        assertEquals(NotificationType.MY_JOB_EXPIRED_UNCHARGED, Jobs.uncharged(NotificationType.MY_JOB_EXPIRED));
        assertEquals(NotificationType.MY_JOB_LAND_LOST_UNCHARGED, Jobs.uncharged(NotificationType.MY_JOB_LAND_LOST));
        assertEquals(
                NotificationType.MY_JOB_HARVEST_ENDED_UNCHARGED, Jobs.uncharged(NotificationType.MY_JOB_HARVEST_ENDED));
        assertEquals(NotificationType.MY_JOB_SETTLED_UNCHARGED, Jobs.uncharged(NotificationType.MY_JOB_SETTLED));
        assertEquals(NotificationType.MY_JOB_TAKEN, Jobs.uncharged(NotificationType.MY_JOB_TAKEN));
    }
}
