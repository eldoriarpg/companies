/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs.hooks;

import de.eldoria.companies.data.wrapper.job.JobLand;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * How the land plugins' answers decide where a job may lie.
 */
class JobHooksTest {
    @Test
    void foreignLandRefusesWhateverElseOwnsTheArea() {
        var decision =
                JobHooks.decide(List.of(new Claim.Owned(JobLand.REGION, "home"), new Claim.Foreign("spawn")), true);

        assertEquals(new JobHooks.Decision.Refused("spawn"), decision);
    }

    @Test
    void theOwnersLandIsUsedBeforeUnownedLand() {
        var decision = JobHooks.decide(
                List.of(new Claim.None(), new Claim.Owned(JobLand.AREA, "plotsquared:plots|1;2")), false);

        assertEquals(new JobHooks.Decision.Land(JobLand.AREA, "plotsquared:plots|1;2"), decision);
    }

    @Test
    void unownedLandNeedsTheWorldToAllowIt() {
        assertEquals(new JobHooks.Decision.Unowned(), JobHooks.decide(List.of(new Claim.None()), false));
        assertEquals(new JobHooks.Decision.Land(JobLand.UNOWNED, null), JobHooks.decide(List.of(), true));
    }

    @Test
    void withoutPluginsNothingIsSelectedOrOwned() {
        var hooks = new JobHooks(name -> false, className -> new Object(), Logger.getAnonymousLogger());
        hooks.load();

        assertFalse(hooks.selects());
        assertEquals(List.of(), hooks.landPlugins());
    }
}
