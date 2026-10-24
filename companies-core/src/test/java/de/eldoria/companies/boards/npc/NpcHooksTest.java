/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.boards.npc;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import de.eldoria.companies.configuration.elements.generalsettings.Hooks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcHooksTest {
    private static final NpcClickHandler IGNORE = (player, hook, npc) -> {};

    private final List<String> created = new ArrayList<>();
    private final List<LogRecord> warnings = new ArrayList<>();
    private final Logger logger = logger();

    @AfterEach
    void tearDown() {
        if (MockBukkit.isMocked()) MockBukkit.unmock();
    }

    private Logger logger() {
        var log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        log.addHandler(new Handler() {
            @Override
            public void publish(LogRecord logRecord) {
                if (logRecord.getLevel().intValue() > Level.INFO.intValue()) warnings.add(logRecord);
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        });
        return log;
    }

    private NpcHooks hooks(Set<String> available, Map<String, NpcHook> byClass) {
        return new NpcHooks(
                available::contains,
                className -> {
                    created.add(className);
                    var hook = byClass.get(className);
                    if (hook == null) throw new ClassNotFoundException(className);
                    return hook;
                },
                logger);
    }

    @Test
    void nothingIsLoadedWithoutNpcPlugins() {
        var hooks = hooks(Set.of(), Map.of());

        hooks.load(null, IGNORE);

        assertTrue(created.isEmpty());
        assertTrue(hooks.names().isEmpty());
        assertEquals(Optional.empty(), hooks.hook("Citizens"));
        assertTrue(warnings.isEmpty());
    }

    @Test
    void onlyTheEnabledPluginIsHooked() {
        var fancy = new FakeHook("FancyNpcs", Set.of("Trader"));
        var hooks = hooks(Set.of("FancyNpcs"), Map.of(NpcHooks.KNOWN.get("FancyNpcs"), fancy));

        hooks.load(null, IGNORE);

        assertEquals(List.of(NpcHooks.KNOWN.get("FancyNpcs")), created);
        assertEquals(List.of("FancyNpcs"), hooks.names());
        assertSame(fancy, hooks.hook("fancynpcs").orElseThrow());
        assertEquals(List.of(IGNORE), fancy.handlers());
    }

    @Test
    void brokenHookIsSkipped() {
        var fancy = new FakeHook("FancyNpcs", Set.of());
        var hooks = new NpcHooks(
                name -> true,
                className -> {
                    if (className.equals(NpcHooks.KNOWN.get("Citizens"))) {
                        throw new NoClassDefFoundError("net/citizensnpcs/api/event/NPCRightClickEvent");
                    }
                    return fancy;
                },
                logger);

        hooks.load(null, IGNORE);

        assertEquals(List.of("FancyNpcs"), hooks.names());
        assertEquals(1, warnings.size());
        assertInstanceOf(NoClassDefFoundError.class, warnings.getFirst().getThrown());
    }

    @Test
    void missingHookClassIsSkipped() {
        var hooks = hooks(Set.of("Citizens"), Map.of());

        hooks.load(null, IGNORE);

        assertTrue(hooks.names().isEmpty());
        assertEquals(1, warnings.size());
        assertInstanceOf(ClassNotFoundException.class, warnings.getFirst().getThrown());
    }

    @Test
    void loadingAgainKeepsTheLoadedHook() {
        var citizens = new FakeHook();
        var hooks = hooks(Set.of("Citizens"), Map.of(NpcHooks.KNOWN.get("Citizens"), citizens));

        hooks.load(null, IGNORE);
        hooks.load(null, IGNORE);

        assertEquals(1, created.size());
        assertEquals(1, citizens.handlers().size());
    }

    @Test
    void unloadDisablesTheHooks() {
        var citizens = new FakeHook();
        var hooks = hooks(Set.of("Citizens"), Map.of(NpcHooks.KNOWN.get("Citizens"), citizens));
        hooks.load(null, IGNORE);

        hooks.unload();

        assertEquals(1, citizens.disabled());
        assertTrue(hooks.names().isEmpty());
    }

    @Test
    void reflectiveFactoryCreatesTheHook() throws ReflectiveOperationException {
        var factory = NpcHooks.HookFactory.reflective(getClass().getClassLoader());

        assertInstanceOf(FakeHook.class, factory.create(FakeHook.class.getName()));
        assertThrows(ClassNotFoundException.class, () -> factory.create("de.eldoria.companies.hooks.Missing"));
        assertThrows(ClassCastException.class, () -> factory.create(String.class.getName()));
    }

    @Test
    void serverWithoutNpcPluginsHooksNothing() {
        MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        var hooks = NpcHooks.create(plugin, name -> true);

        hooks.load(plugin, IGNORE);

        assertTrue(hooks.names().isEmpty());
    }

    @Test
    void hooksAreSwitchedOnByDefault() {
        var settings = new Hooks();

        assertTrue(settings.npc("Citizens"));
        assertTrue(settings.npc("fancynpcs"));
        assertFalse(settings.npc("Unknown"));
    }

    @Test
    void switchedOffHookIsNotAvailable() {
        var mapper = YAMLMapper.builder()
                .changeDefaultVisibility(visibility -> visibility.withFieldVisibility(JsonAutoDetect.Visibility.ANY))
                .build();

        var settings = mapper.readValue("citizens: false", Hooks.class);

        assertFalse(settings.npc("Citizens"));
        assertTrue(settings.npc("FancyNpcs"));
    }
}
