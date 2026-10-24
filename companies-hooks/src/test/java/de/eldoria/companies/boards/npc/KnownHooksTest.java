/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.boards.npc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KnownHooksTest {
    @Test
    void everyKnownHookIsCreatedWithoutItsPlugin() throws ReflectiveOperationException {
        var factory = NpcHooks.HookFactory.reflective(getClass().getClassLoader());

        for (var known : NpcHooks.KNOWN.entrySet()) {
            assertEquals(known.getKey(), factory.create(known.getValue()).pluginName());
        }
    }
}
