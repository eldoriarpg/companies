/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.actions;

import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.configuration.RulesEditor;
import de.eldoria.companies.configuration.elements.systemorders.RuleEntry;
import de.eldoria.companies.dashboard.DashboardWorld;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.dashboard.StoredSession;
import de.eldoria.companies.data.wrapper.order.OrderContent;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.data.wrapper.order.SystemDecision;
import de.eldoria.companies.orders.SystemOwner;
import de.eldoria.companies.orders.system.GuessedLearning;
import de.eldoria.companies.util.Permission;
import de.eldoria.companies.util.Times;
import de.eldoria.companies.web.api.Actions;
import de.eldoria.companies.web.api.Protocol;
import de.eldoria.companies.web.api.action.ActionRequest;
import de.eldoria.companies.web.api.action.ActionResult;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.node.JsonNodeFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The dashboard's view of the server's own orders: allowed with the permission of {@code /companyadmin order}, the
 * open ones listed, why one was placed read with its stored numbers and written to the admin log, and what guessed
 * orders learned per material.
 */
class AdminSystemOrderWritesTest {
    @TempDir
    Path directory;

    private static final String RULE = "{\"id\":\"iron\",\"name\":\"Royal smithy\",\"material\":\"IRON_INGOT\","
            + "\"amount\":256,\"price\":{\"mode\":\"FIXED\",\"value\":2.0}}";

    private DashboardWorld world;
    private UUID admin;

    @BeforeEach
    void setUp() throws Exception {
        world = DashboardWorld.create(directory);
        admin = world.player("Ada");
    }

    @AfterEach
    void tearDown() {
        world.close();
    }

    @Test
    void withoutThePermissionNothingIsShown() {
        var result = perform(Actions.ADMIN_SYSTEM_ORDERS_LIST, "{}");

        assertFalse(result.ok());
        assertEquals("companyadmin.error.permission", result.reason());
    }

    @Test
    void theOpenSystemOrdersAreListed() {
        var open = systemOrder("iron", "{\"rule\":\"iron\"}");
        var delivered = systemOrder("iron", "{}");
        SqLiteTestDatabase.setState(delivered, OrderState.DELIVERED.stateId(), 1);

        var result = perform(Actions.ADMIN_SYSTEM_ORDERS_LIST, "{}", Permission.Admin.ORDERS);

        assertTrue(result.ok());
        var orders = result.data().path("orders");
        assertEquals(1, orders.size());
        assertEquals(open, orders.get(0).path("order").asInt());
        assertEquals("iron", orders.get(0).path("rule").asString());
        assertEquals("UNCLAIMED", orders.get(0).path("state").asString());
        assertEquals("bank", orders.get(0).path("account").asString());
    }

    @Test
    void whyAnOrderWasPlacedIsReadWithItsNumbersAndLogged() {
        var order = systemOrder("iron", "{\"rule\":\"iron\",\"unit\":2.5}");

        var result = perform(Actions.ADMIN_SYSTEM_ORDERS_INFO, "{\"order\":" + order + "}", Permission.Admin.ORDERS);

        assertTrue(result.ok());
        assertEquals(2.5, result.data().path("record").path("unit").asDouble());
        assertEquals("DEFINED", result.data().path("kind").asString());
        assertTrue(world.adminActions.stream().anyMatch(line -> line.contains("system order info")));
    }

    @Test
    void anOrderWithoutADecisionIsUnknown() {
        var result = perform(Actions.ADMIN_SYSTEM_ORDERS_INFO, "{\"order\":999}", Permission.Admin.ORDERS);

        assertFalse(result.ok());
        assertEquals("companyadmin.systemorders.error.unknown", result.reason());
    }

    @Test
    void whatGuessedOrdersLearnedIsShownPerMaterial() {
        var order = place();
        world.orders.saveSystemDecision(new SystemDecision(
                order,
                "guessed",
                "GUESSED",
                Times.now(),
                null,
                10,
                "{\"material\":\"DIRT\",\"amount\":64,\"factor\":0.8,\"average\":2.0,\"unit\":1.6,\"step\":0}"));
        world.orders.systemOutcome(order, GuessedLearning.TAKEN, Times.now().plusMinutes(5), null);

        var result = perform(Actions.ADMIN_SYSTEM_ORDERS_STATS, "{}", Permission.Admin.ORDERS);

        assertTrue(result.ok());
        var dirt = result.data().path("materials").get(0);
        assertEquals("DIRT", dirt.path("material").asString());
        assertEquals(1, dirt.path("taken").asInt());
        assertEquals(0.75, dirt.path("factor").asDouble(), 0.000_1);
    }

    @Test
    void theRulesAreReadAsTheLatestRevision() {
        var result = perform(Actions.ADMIN_SYSTEM_ORDERS_RULES, "{}", Permission.Admin.ORDERS);

        assertTrue(result.ok(), result.reason());
        assertEquals(0, result.data().path("revision").asInt());
        assertFalse(result.data().path("editable").asBoolean());
        assertEquals(0, result.data().path("rules").size());
    }

    @Test
    void savedRulesBecomeTheNextRevision() {
        var saved = perform(
                Actions.ADMIN_SYSTEM_ORDERS_RULES_SAVE,
                "{\"base\":0,\"rules\":[" + RULE + "]}",
                Permission.Admin.ORDERS,
                Permission.Admin.RELOAD);

        assertTrue(saved.ok(), saved.reason());
        assertEquals("saved", saved.data().path("result").asString());
        var read = perform(Actions.ADMIN_SYSTEM_ORDERS_RULES, "{}", Permission.Admin.ORDERS, Permission.Admin.RELOAD);
        assertEquals(1, read.data().path("revision").asInt());
        assertTrue(read.data().path("editable").asBoolean());
        assertEquals("iron", read.data().path("rules").get(0).path("id").asString());
        assertEquals(256, read.data().path("rules").get(0).path("amount").asInt());
        assertTrue(world.adminActions.stream().anyMatch(line -> line.contains("system order rules save")));
    }

    @Test
    void rulesWithAProblemAreRefusedWithItsPlace() {
        var saved = perform(
                Actions.ADMIN_SYSTEM_ORDERS_RULES_SAVE,
                "{\"base\":0,\"rules\":[" + RULE + "," + RULE + ","
                        + "{\"id\":\"guessed\",\"material\":\"NOT_AN_ITEM\"},"
                        + "{\"id\":\"Event-Offer\",\"material\":\"IRON_INGOT\"}]}",
                Permission.Admin.ORDERS,
                Permission.Admin.RELOAD);

        assertEquals("invalid", saved.data().path("result").asString());
        var errors = saved.data().path("errors");
        assertEquals("duplicate", errors.path("rules.1.id").asString());
        assertEquals("reserved", errors.path("rules.2.id").asString());
        assertEquals("unknown", errors.path("rules.2.material").asString());
        assertEquals("reserved", errors.path("rules.3.id").asString());
    }

    @Test
    void rulesWithEntriesAreCheckedEntryByEntry() {
        var rule = "{\"id\":\"wood\",\"pick\":\"SIDEWAYS\",\"items\":["
                + "{\"material\":\"OAK_LOG\"},"
                + "{\"criteria\":\"#logs\",\"resolve\":\"ONE\"},"
                + "{\"criteria\":\"#logs sharpness:1\"},"
                + "{\"material\":\"OAK_LOG\",\"criteria\":\"#logs\"},"
                + "{\"criteria\":\"#logs\",\"resolve\":\"SOME\"},"
                + "{\"criteria\":\"#logs enchants:x\"},"
                + "{\"material\":\"NOT_AN_ITEM\"},"
                + "{\"criteria\":\"#logs\",\"resolve\":\"one\"}],"
                + "\"price\":{\"mode\":\"FIXED\",\"value\":2.0}}";
        var averaged = "{\"id\":\"logs\",\"pick\":\"rotate\",\"items\":["
                + "{\"material\":\"OAK_LOG\"},"
                + "{\"criteria\":\"#logs\",\"resolve\":\"ONE\"},"
                + "{\"criteria\":\"#logs\"}]}";

        var saved = perform(
                Actions.ADMIN_SYSTEM_ORDERS_RULES_SAVE,
                "{\"base\":0,\"rules\":[" + rule + "," + averaged + "]}",
                Permission.Admin.ORDERS,
                Permission.Admin.RELOAD);

        var errors = saved.data().path("errors");
        assertEquals("invalid", saved.data().path("result").asString());
        assertEquals("invalid", errors.path("rules.0.pick").asString());
        assertTrue(errors.path("rules.0.items.0").isMissingNode());
        assertTrue(errors.path("rules.0.items.1").isMissingNode());
        assertEquals("unknown", errors.path("rules.0.items.2").asString());
        assertEquals("invalid", errors.path("rules.0.items.3").asString());
        assertEquals("invalid", errors.path("rules.0.items.4").asString());
        assertEquals("invalid", errors.path("rules.0.items.5").asString());
        assertEquals("unknown", errors.path("rules.0.items.6").asString());
        assertTrue(errors.path("rules.0.items.7").isMissingNode());
        assertTrue(errors.path("rules.1.pick").isMissingNode());
        assertTrue(errors.path("rules.1.items.1").isMissingNode());
        assertEquals("fixedPrice", errors.path("rules.1.items.2").asString());
    }

    @Test
    void lowerCaseChoicesAreSavedInUpperCase() {
        var rule = "{\"id\":\"wood\",\"pick\":\"rotate\",\"items\":["
                + "{\"material\":\"OAK_LOG\"},{\"criteria\":\"#logs\",\"resolve\":\"one\"}],"
                + "\"price\":{\"mode\":\"FIXED\",\"value\":2.0}}";

        var saved = perform(
                Actions.ADMIN_SYSTEM_ORDERS_RULES_SAVE,
                "{\"base\":0,\"rules\":[" + rule + "]}",
                Permission.Admin.ORDERS,
                Permission.Admin.RELOAD);

        assertEquals(
                "saved", saved.data().path("result").asString(), saved.data().toString());
        var read = RulesEditor.read(world.settings, world.nodeData).rules().get(0);
        assertEquals("ROTATE", read.path("pick").asString());
        assertEquals("ONE", read.path("items").get(1).path("resolve").asString());
        assertTrue(read.path("items").get(0).path("resolve").isMissingNode());
    }

    @Test
    void aCapturedItemIsAddedToARuleAndReadBack() {
        perform(
                Actions.ADMIN_SYSTEM_ORDERS_RULES_SAVE,
                "{\"base\":0,\"rules\":[" + RULE + "]}",
                Permission.Admin.ORDERS,
                Permission.Admin.RELOAD);
        var sword = new ItemStack(Material.DIAMOND_SWORD);
        sword.addUnsafeEnchantment(Enchantment.SHARPNESS, 5);
        var mapper = world.settings.storageMapper();
        var entry = JsonNodeFactory.instance.objectNode();
        entry.set("item", mapper.valueToTree(sword));
        entry.put("name", "Royal blade");

        var result =
                RulesEditor.addEntry(world.settings, world.nodeData, "iron", entry, RulesEditor::knownMaterial, "Ada");
        var unknown = RulesEditor.addEntry(
                world.settings, world.nodeData, "copper", entry, RulesEditor::knownMaterial, "Ada");
        var again =
                RulesEditor.addEntry(world.settings, world.nodeData, "iron", entry, RulesEditor::knownMaterial, "Ada");

        assertInstanceOf(RulesEditor.Result.Done.class, result);
        assertInstanceOf(RulesEditor.Result.Invalid.class, unknown);
        assertEquals(Map.of("item", "duplicate"), ((RulesEditor.Result.Invalid) again).fields());
        var rule = RulesEditor.read(world.settings, world.nodeData).rules().get(0);
        assertTrue(rule.path("material").asString("").isEmpty());
        assertEquals("IRON_INGOT", rule.path("items").get(0).path("material").asString());
        assertTrue(rule.path("items").get(0).path("criteria").isMissingNode());
        assertTrue(rule.path("items").get(0).path("item").isMissingNode());
        var read = mapper.treeToValue(rule.path("items").get(1), RuleEntry.class);
        assertEquals("Royal blade", read.name());
        assertEquals(5, read.item().getEnchantmentLevel(Enchantment.SHARPNESS));
    }

    @Test
    void aRuleIsPausedAndResumedAsTheNextRevision() {
        perform(
                Actions.ADMIN_SYSTEM_ORDERS_RULES_SAVE,
                "{\"base\":0,\"rules\":[" + RULE + "]}",
                Permission.Admin.ORDERS,
                Permission.Admin.RELOAD);

        var running =
                RulesEditor.setEnabled(world.settings, world.nodeData, "iron", true, RulesEditor::knownMaterial, "Ada");
        var paused = RulesEditor.setEnabled(
                world.settings, world.nodeData, "iron", false, RulesEditor::knownMaterial, "Ada");
        var again = RulesEditor.setEnabled(
                world.settings, world.nodeData, "iron", false, RulesEditor::knownMaterial, "Ada");
        var unknown = RulesEditor.setEnabled(
                world.settings, world.nodeData, "copper", false, RulesEditor::knownMaterial, "Ada");

        assertEquals(new Configuration.StoreOutcome.Unchanged(1), ((RulesEditor.Result.Done) running).outcome());
        assertInstanceOf(Configuration.StoreOutcome.Stored.class, ((RulesEditor.Result.Done) paused).outcome());
        assertEquals(new Configuration.StoreOutcome.Unchanged(2), ((RulesEditor.Result.Done) again).outcome());
        assertEquals(Map.of("id", "unknown"), ((RulesEditor.Result.Invalid) unknown).fields());
        var rules = RulesEditor.read(world.settings, world.nodeData);
        assertEquals(2, rules.revision());
        assertFalse(rules.rules().get(0).path("enabled").asBoolean(true));

        var resumed =
                RulesEditor.setEnabled(world.settings, world.nodeData, "iron", true, RulesEditor::knownMaterial, "Ada");

        assertInstanceOf(Configuration.StoreOutcome.Stored.class, ((RulesEditor.Result.Done) resumed).outcome());
        var rule = RulesEditor.read(world.settings, world.nodeData).rules().get(0);
        assertTrue(rule.path("enabled").asBoolean());
        assertEquals(256, rule.path("amount").asInt());
    }

    @Test
    void savingRulesNeedsTheReloadPermission() {
        var saved =
                perform(Actions.ADMIN_SYSTEM_ORDERS_RULES_SAVE, "{\"base\":0,\"rules\":[]}", Permission.Admin.ORDERS);

        assertFalse(saved.ok());
        assertEquals("companyadmin.error.permission", saved.reason());
    }

    @Test
    void rulesSavedMeanwhileRefuseAnOlderBase() {
        perform(
                Actions.ADMIN_SYSTEM_ORDERS_RULES_SAVE,
                "{\"base\":0,\"rules\":[" + RULE + "]}",
                Permission.Admin.ORDERS,
                Permission.Admin.RELOAD);

        var stale = perform(
                Actions.ADMIN_SYSTEM_ORDERS_RULES_SAVE,
                "{\"base\":0,\"rules\":[]}",
                Permission.Admin.ORDERS,
                Permission.Admin.RELOAD);

        assertEquals("newer", stale.data().path("result").asString());
        assertEquals(1, stale.data().path("revision").asInt());
        assertEquals("iron", stale.data().path("rules").get(0).path("id").asString());
    }

    private int systemOrder(String rule, String record) {
        var order = place();
        world.orders.systemRule(order, rule);
        world.orders.saveSystemDecision(new SystemDecision(order, rule, "DEFINED", Times.now(), "bank", 20, record));
        return order;
    }

    private int place() {
        var server = SqLiteTestDatabase.player(SystemOwner.ID);
        var content = new OrderContent(0, new ItemStack(Material.IRON_INGOT), false, 10, 20.0);
        world.orders
                .submitOrder(server, new SimpleOrder(SystemOwner.ID, "Royal smithy", 0.0).toFullOrder(List.of(content)))
                .join();
        var placed = world.orders
                .retrieveOrdersByPlayer(server, OrderState.UNCLAIMED, OrderState.RECEIVED)
                .join();
        return placed.get(placed.size() - 1).id();
    }

    private ActionResult perform(String action, String args, String... permissions) {
        var session = new StoredSession(
                "session",
                admin,
                "Ada",
                UUID.randomUUID(),
                Times.now(),
                Times.now(),
                Times.now(),
                Times.now(),
                null,
                Set.of(permissions));
        return world.writes.perform(
                new ActionRequest(
                        "act",
                        Protocol.VERSION,
                        session.id(),
                        admin,
                        action,
                        Protocol.mapper().readTree(args)),
                session);
    }
}
