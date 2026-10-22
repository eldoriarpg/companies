/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.sqlite;

import de.chojo.sadu.core.updater.SqlVersion;
import de.eldoria.companies.commands.company.order.search.SearchQuery;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;
import de.eldoria.companies.data.wrapper.order.ItemAmount;
import de.eldoria.companies.data.wrapper.order.OrderContent;
import de.eldoria.companies.data.wrapper.order.ReturnKind;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.orders.DeliveryPart;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_BYTES;

class SqLiteOrderDataTest {
    @BeforeAll
    static void startServer() {
        MockBukkit.mock();
    }

    @AfterAll
    static void stopServer() {
        MockBukkit.unmock();
    }

    private static final int COMPANY = 1;
    private final OfflinePlayer owner = SqLiteTestDatabase.player(UUID.randomUUID());
    private final OfflinePlayer worker = SqLiteTestDatabase.player(UUID.randomUUID());

    @TempDir
    Path directory;

    private SqLiteOrderData orderData;

    @BeforeEach
    void setUp() throws Exception {
        SqLiteTestDatabase.create(directory);
        orderData = new SqLiteOrderData(null, SqLiteTestDatabase.mapper());
    }

    private SimpleOrder createOrder(int stone, int dirt) {
        return createOrder(stone, dirt, 0.0);
    }

    private SimpleOrder createOrder(int stone, int dirt, double fee) {
        var contents = List.of(
                new OrderContent(0, new ItemStack(Material.STONE), false, stone, 100.0),
                new OrderContent(1, new ItemStack(Material.DIRT), false, dirt, 50.0));
        orderData
                .submitOrder(owner, new SimpleOrder(owner.getUniqueId(), "test", fee).toFullOrder(contents))
                .join();
        var orders = orderData
                .retrieveOrdersByPlayer(owner, OrderState.UNCLAIMED, OrderState.RECEIVED)
                .join();
        return orders.get(orders.size() - 1);
    }

    private SimpleOrder reload(SimpleOrder order) {
        return orderData.retrieveOrderById(order.id()).join().orElseThrow();
    }

    private void claim(SimpleOrder order) {
        SqLiteTestDatabase.setState(order.id(), OrderState.CLAIMED.stateId(), COMPANY);
    }

    private void addDelivery(SimpleOrder order, int line, Material material, int amount) {
        query("INSERT INTO order_deliveries(order_id, line, worker_uuid, stack, amount) VALUES (?, ?, ?, ?, ?)")
                .single(call().bind(order.id())
                        .bind(line)
                        .bind(worker.getUniqueId(), UUID_BYTES)
                        .bind("{\"type\":\"" + material.name() + "\",\"amount\":1}")
                        .bind(amount))
                .insert();
    }

    private int deliver(SimpleOrder order, int line, Material material, int amount) {
        return orderData
                .submitDeliveries(
                        worker, reload(order), List.of(new DeliveryPart(line, new ItemStack(material), amount)))
                .join()
                .get(0);
    }

    @Test
    void unclaimMovesDeliveriesToItemReturns() {
        var order = createOrder(10, 5);
        claim(order);
        addDelivery(order, 0, Material.STONE, 4);

        var workers = orderData.submitUnclaimOrder(reload(order)).join();

        Assertions.assertEquals(Set.of(worker.getUniqueId()), workers.orElseThrow());
        Assertions.assertEquals(OrderState.UNCLAIMED, reload(order).state());
        Assertions.assertEquals(0, SqLiteTestDatabase.count("order_deliveries", "order_id = " + order.id()));
        var returns = orderData
                .retrieveItemReturns(worker.getUniqueId(), ReturnKind.WORKER)
                .join();
        Assertions.assertEquals(1, returns.size());
        Assertions.assertEquals(Material.STONE, returns.get(0).stack().getType());
        Assertions.assertEquals(4, returns.get(0).amount());
    }

    @Test
    void unclaimIgnoresOrdersWhichAreNotClaimed() {
        var order = createOrder(10, 5);
        claim(order);
        addDelivery(order, 0, Material.STONE, 4);
        SqLiteTestDatabase.setState(order.id(), OrderState.RECEIVED.stateId(), COMPANY);

        var workers = orderData.submitUnclaimOrder(reload(order)).join();

        Assertions.assertTrue(workers.isEmpty());
        Assertions.assertEquals(OrderState.RECEIVED, reload(order).state());
        Assertions.assertEquals(1, SqLiteTestDatabase.count("order_deliveries", "order_id = " + order.id()));
        Assertions.assertTrue(orderData
                .retrieveItemReturns(worker.getUniqueId(), ReturnKind.WORKER)
                .join()
                .isEmpty());
    }

    @Test
    void itemReturnsCanOnlyBeTakenOnce() {
        orderData
                .submitItemReturn(worker.getUniqueId(), ReturnKind.WORKER, new ItemStack(Material.STONE), 10)
                .join();
        var itemReturn = orderData
                .retrieveItemReturns(worker.getUniqueId(), ReturnKind.WORKER)
                .join()
                .get(0);

        Assertions.assertTrue(orderData.submitItemReturnTake(itemReturn, 6).join());
        Assertions.assertFalse(orderData.submitItemReturnTake(itemReturn, 6).join());
        Assertions.assertEquals(
                4,
                orderData
                        .retrieveItemReturns(worker.getUniqueId(), ReturnKind.WORKER)
                        .join()
                        .get(0)
                        .amount());
        Assertions.assertTrue(orderData.submitItemReturnTake(itemReturn, 4).join());
        Assertions.assertTrue(orderData
                .retrieveItemReturns(worker.getUniqueId(), ReturnKind.WORKER)
                .join()
                .isEmpty());
    }

    @Test
    void deliveryIsCappedToMissingAmount() {
        var order = createOrder(10, 5);
        claim(order);

        Assertions.assertEquals(6, deliver(order, 0, Material.STONE, 6));
        Assertions.assertEquals(4, deliver(order, 0, Material.STONE, 6));
        Assertions.assertEquals(0, deliver(order, 0, Material.STONE, 6));

        var content = orderData.retrieveFullOrder(reload(order)).join().line(0).orElseThrow();
        Assertions.assertEquals(10, content.delivered());
        Assertions.assertEquals(2, SqLiteTestDatabase.count("order_deliveries", "order_id = " + order.id()));
    }

    @Test
    void deliveryRequiresClaimedOrder() {
        var order = createOrder(10, 5);

        Assertions.assertEquals(0, deliver(order, 0, Material.STONE, 5));

        SqLiteTestDatabase.setState(order.id(), OrderState.DELIVERED.stateId(), COMPANY);
        Assertions.assertEquals(0, deliver(order, 0, Material.STONE, 5));
        Assertions.assertEquals(0, SqLiteTestDatabase.count("order_deliveries", "order_id = " + order.id()));
    }

    @Test
    void orderIsMarkedDeliveredOnlyOnceAndWhenComplete() {
        var order = createOrder(10, 5);
        claim(order);
        deliver(order, 0, Material.STONE, 10);

        Assertions.assertTrue(
                orderData.submitOrderDelivered(reload(order)).join().isEmpty());

        deliver(order, 1, Material.DIRT, 5);
        var delivered = orderData.submitOrderDelivered(reload(order)).join();

        Assertions.assertTrue(delivered.isPresent());
        Assertions.assertEquals(OrderState.DELIVERED, delivered.get().state());
        Assertions.assertEquals(15, delivered.get().delivered());
        Assertions.assertTrue(
                orderData.submitOrderDelivered(reload(order)).join().isEmpty());
    }

    @Test
    void orderCanOnlyBeReceivedOnce() {
        var order = createOrder(10, 5);
        SqLiteTestDatabase.setState(order.id(), OrderState.DELIVERED.stateId(), COMPANY);

        Assertions.assertTrue(orderData
                .submitOrderStateUpdate(reload(order), OrderState.DELIVERED, OrderState.RECEIVED)
                .join());
        Assertions.assertFalse(orderData
                .submitOrderStateUpdate(reload(order), OrderState.DELIVERED, OrderState.RECEIVED)
                .join());
        Assertions.assertEquals(OrderState.RECEIVED, reload(order).state());
    }

    @Test
    void onlyUnclaimedOrdersCanBeDeleted() {
        var claimed = createOrder(10, 5);
        claim(claimed);
        var unclaimed = createOrder(10, 5);

        Assertions.assertFalse(orderData.submitOrderDeletion(reload(claimed)).join());
        Assertions.assertTrue(orderData.retrieveOrderById(claimed.id()).join().isPresent());
        Assertions.assertTrue(orderData.submitOrderDeletion(reload(unclaimed)).join());
        Assertions.assertFalse(orderData.submitOrderDeletion(unclaimed).join());
        Assertions.assertTrue(orderData.retrieveOrderById(unclaimed.id()).join().isEmpty());
    }

    @Test
    void deadOrdersAreOldUnclaimedOrders() {
        var dead = createOrder(10, 5);
        var fresh = createOrder(10, 5);
        var claimed = createOrder(10, 5);
        claim(claimed);
        query("UPDATE order_states SET last_update = datetime(CURRENT_TIMESTAMP, '-10 HOUR') WHERE id IN (?, ?)")
                .single(call().bind(dead.id()).bind(claimed.id()))
                .update();

        var deadOrders = orderData.retrieveDeadOrders(5).join();

        Assertions.assertEquals(
                List.of(dead.id()), deadOrders.stream().map(SimpleOrder::id).toList());
        Assertions.assertTrue(orderData.submitOrderPurge(deadOrders.get(0)).join());
        Assertions.assertFalse(orderData.submitOrderPurge(deadOrders.get(0)).join());
        Assertions.assertTrue(orderData.retrieveOrderById(fresh.id()).join().isPresent());
    }

    @Test
    void searchFiltersByMaterial() {
        var order = createOrder(10, 5);
        var query = new SearchQuery();
        query.materials(List.of("STONE"));

        var found = orderData
                .retrieveOrdersByQuery(query, OrderState.UNCLAIMED, OrderState.UNCLAIMED)
                .join();
        Assertions.assertEquals(
                List.of(order.id()), found.stream().map(SimpleOrder::id).toList());

        query.materials(List.of("GRAVEL"));
        Assertions.assertTrue(orderData
                .retrieveOrdersByQuery(query, OrderState.UNCLAIMED, OrderState.UNCLAIMED)
                .join()
                .isEmpty());
    }

    @Test
    void materialPriceRefreshUpdatesAllPrices() {
        var cheap = createOrder(10, 5);
        SqLiteTestDatabase.setState(cheap.id(), OrderState.DELIVERED.stateId(), COMPANY);
        orderData.refreshMaterialPrices();

        var expensive = createOrder(1, 5);
        SqLiteTestDatabase.setState(expensive.id(), OrderState.DELIVERED.stateId(), COMPANY);
        orderData.refreshMaterialPrices();

        var price = orderData.retrieveMaterialPrice("STONE").join();
        Assertions.assertEquals(10.0, price.minPrice(), 0.0001);
        Assertions.assertEquals(100.0, price.maxPrice(), 0.0001);
    }

    @Test
    void feeIsStoredWithTheOrder() {
        var order = createOrder(10, 5, 12.34);

        Assertions.assertEquals(12.34, order.fee());
        Assertions.assertEquals(12.34, reload(order).fee());
        Assertions.assertEquals(
                12.34, orderData.retrieveFullOrder(reload(order)).join().fee());
        Assertions.assertEquals(
                12.34,
                orderData
                        .retrieveOrdersByQuery(new SearchQuery(), OrderState.UNCLAIMED, OrderState.UNCLAIMED)
                        .join()
                        .get(0)
                        .fee());
        claim(order);
        Assertions.assertEquals(
                12.34,
                orderData
                        .retrieveCompanyOrderById(order.id(), COMPANY)
                        .join()
                        .orElseThrow()
                        .fee());
    }

    @Test
    void deadOrdersCarryTheirFee() {
        var dead = createOrder(10, 5, 7.5);
        query("UPDATE order_states SET last_update = datetime(CURRENT_TIMESTAMP, '-10 HOUR') WHERE id = ?")
                .single(call().bind(dead.id()))
                .update();

        var deadOrders = orderData.retrieveDeadOrders(5).join();

        Assertions.assertEquals(7.5, deadOrders.get(0).fee());
    }

    @Test
    void migrationGivesExistingOrdersNoFee() throws Exception {
        var oldDirectory = directory.resolve("old");
        Files.createDirectories(oldDirectory);
        SqLiteTestDatabase.create(oldDirectory, new SqlVersion(1, 1));
        query("INSERT INTO orders(owner_uuid, name) VALUES (?, 'legacy')")
                .single(call().bind(owner.getUniqueId(), UUID_BYTES))
                .insert();
        query("INSERT INTO order_states(id, state) VALUES (1, ?)")
                .single(call().bind(OrderState.UNCLAIMED.stateId()))
                .insert();

        SqLiteTestDatabase.create(oldDirectory);

        var legacy = orderData.retrieveOrderById(1).join().orElseThrow();
        Assertions.assertEquals("legacy", legacy.name());
        Assertions.assertEquals(0.0, legacy.fee());
    }

    private void age(SimpleOrder order, int hours) {
        query("UPDATE order_states SET last_update = datetime(CURRENT_TIMESTAMP, ? || ' HOUR') WHERE id = ?")
                .single(call().bind("-" + hours).bind(order.id()))
                .update();
    }

    private List<Integer> purgeWarnings() {
        return orderData.retrievePurgeWarnings(12, 5).join().stream()
                .map(SimpleOrder::id)
                .toList();
    }

    @Test
    void extensionsAreLimited() {
        var order = createOrder(10, 5);
        age(order, 10);

        Assertions.assertTrue(orderData.submitOrderExtension(reload(order), 2).join());
        Assertions.assertEquals(1, reload(order).extensions());
        Assertions.assertTrue(reload(order)
                .claimed()
                .isAfter(LocalDateTime.now(ZoneOffset.UTC).minusHours(1)));
        Assertions.assertTrue(orderData.submitOrderExtension(reload(order), 2).join());
        Assertions.assertFalse(orderData.submitOrderExtension(reload(order), 2).join());
        Assertions.assertEquals(2, reload(order).extensions());
    }

    @Test
    void onlyUnclaimedOrdersCanBeExtended() {
        var order = createOrder(10, 5);
        claim(order);

        Assertions.assertFalse(orderData.submitOrderExtension(reload(order), 2).join());
        Assertions.assertEquals(0, reload(order).extensions());
    }

    @Test
    void purgeWarningIsSentOncePerTimer() {
        var old = createOrder(10, 5);
        var fresh = createOrder(10, 5);
        age(old, 10);

        Assertions.assertEquals(List.of(old.id()), purgeWarnings());
        Assertions.assertTrue(orderData.submitPurgeWarning(reload(old)).join());
        Assertions.assertFalse(orderData.submitPurgeWarning(reload(old)).join());
        Assertions.assertTrue(purgeWarnings().isEmpty());
        Assertions.assertFalse(purgeWarnings().contains(fresh.id()));
    }

    @Test
    void extendingArmsTheWarningAgain() {
        var order = createOrder(10, 5);
        age(order, 10);
        orderData.submitPurgeWarning(reload(order)).join();

        orderData.submitOrderExtension(reload(order), 2).join();
        query("UPDATE order_states SET purge_warning = datetime(CURRENT_TIMESTAMP, '-20 HOUR') WHERE id = ?")
                .single(call().bind(order.id()))
                .update();
        age(order, 10);

        Assertions.assertEquals(List.of(order.id()), purgeWarnings());
    }

    @Test
    void claimedOrdersGetNoPurgeWarning() {
        var order = createOrder(10, 5);
        claim(order);
        age(order, 10);

        Assertions.assertTrue(purgeWarnings().isEmpty());
        Assertions.assertFalse(orderData.submitPurgeWarning(reload(order)).join());
    }

    @Test
    void raisingThePriceChangesContentsAndFee() {
        var order = createOrder(10, 5, 5.0);

        Assertions.assertTrue(orderData
                .submitPriceRaise(reload(order), Map.of(0, 30.0, 1, 15.0), 2.5)
                .join());

        var raised = orderData.retrieveFullOrder(reload(order)).join();
        Assertions.assertEquals(195.0, raised.price(), 0.0001);
        Assertions.assertEquals(130.0, raised.line(0).orElseThrow().price(), 0.0001);
        Assertions.assertEquals(7.5, raised.fee(), 0.0001);
    }

    @Test
    void claimedOrdersKeepTheirPrice() {
        var order = createOrder(10, 5, 5.0);
        claim(order);

        Assertions.assertFalse(
                orderData.submitPriceRaise(reload(order), Map.of(0, 30.0), 2.5).join());

        var unchanged = orderData.retrieveFullOrder(reload(order)).join();
        Assertions.assertEquals(150.0, unchanged.price(), 0.0001);
        Assertions.assertEquals(5.0, unchanged.fee(), 0.0001);
    }

    @Test
    void receivingStoresWhatDidNotFitForTheOwner() {
        var order = createOrder(10, 5);
        SqLiteTestDatabase.setState(order.id(), OrderState.DELIVERED.stateId(), COMPANY);
        var pending = List.of(new ItemAmount(new ItemStack(Material.STONE), 4));

        Assertions.assertTrue(
                orderData.submitOrderReceive(reload(order), pending).join());
        Assertions.assertFalse(
                orderData.submitOrderReceive(reload(order), pending).join());

        Assertions.assertEquals(OrderState.RECEIVED, reload(order).state());
        var waiting = orderData
                .retrieveItemReturns(owner.getUniqueId(), ReturnKind.OWNER)
                .join();
        Assertions.assertEquals(1, waiting.size());
        Assertions.assertEquals(Material.STONE, waiting.get(0).stack().getType());
        Assertions.assertEquals(4, waiting.get(0).amount());
        Assertions.assertEquals(
                4,
                orderData
                        .retrieveItemReturnCount(owner.getUniqueId(), ReturnKind.OWNER)
                        .join());
    }

    @Test
    void ordersWhichAreNotDeliveredCanNotBeReceived() {
        var order = createOrder(10, 5);
        claim(order);

        Assertions.assertFalse(orderData
                .submitOrderReceive(reload(order), List.of(new ItemAmount(new ItemStack(Material.STONE), 4)))
                .join());
        Assertions.assertEquals(
                0,
                orderData
                        .retrieveItemReturnCount(owner.getUniqueId(), ReturnKind.OWNER)
                        .join());
    }

    @Test
    void returnKindsAreKeptApart() {
        var player = UUID.randomUUID();
        orderData
                .submitItemReturn(player, ReturnKind.WORKER, new ItemStack(Material.STONE), 3)
                .join();
        orderData
                .submitItemReturn(player, ReturnKind.OWNER, new ItemStack(Material.DIRT), 7)
                .join();

        var returns = orderData.retrieveItemReturns(player, ReturnKind.WORKER).join();
        Assertions.assertEquals(1, returns.size());
        Assertions.assertEquals(Material.STONE, returns.get(0).stack().getType());
        Assertions.assertEquals(
                7, orderData.retrieveItemReturnCount(player, ReturnKind.OWNER).join());
        Assertions.assertEquals(
                3, orderData.retrieveItemReturnCount(player, ReturnKind.WORKER).join());
    }

    @Test
    void deliveryBonusDelaysTheExpiry() {
        var order = createOrder(10, 5);
        Assertions.assertTrue(orderData
                .submitOrderClaim(SimpleCompany.forId(COMPANY), reload(order), 5)
                .join());
        age(order, 10);

        Assertions.assertEquals(5, reload(order).deliveryBonus());
        Assertions.assertTrue(orderData.retrieveExpiredOrders(8).join().isEmpty());
        Assertions.assertEquals(
                List.of(order.id()),
                orderData.retrieveExpiredOrders(4).join().stream()
                        .map(SimpleOrder::id)
                        .toList());

        orderData.submitUnclaimOrder(reload(order)).join();
        Assertions.assertEquals(0, reload(order).deliveryBonus());
    }

    @Test
    void releasingCompanyOrdersReportsOrdersAndWorkers() {
        var order = createOrder(10, 5);
        claim(order);
        addDelivery(order, 0, Material.STONE, 2);
        var untouched = createOrder(10, 5);

        var released =
                orderData.submitCompanyOrdersPurge(SimpleCompany.forId(COMPANY)).join();

        Assertions.assertEquals(
                List.of(order.id()),
                released.orders().stream().map(SimpleOrder::id).toList());
        Assertions.assertEquals(Set.of(worker.getUniqueId()), released.workers());
        Assertions.assertEquals(OrderState.UNCLAIMED, reload(untouched).state());
    }

    @Test
    void migrationKeepsOrdersAndReturns() throws Exception {
        var oldDirectory = directory.resolve("previous");
        Files.createDirectories(oldDirectory);
        SqLiteTestDatabase.create(oldDirectory, new SqlVersion(1, 2));
        query("INSERT INTO orders(owner_uuid, name, fee) VALUES (?, 'legacy', 3)")
                .single(call().bind(owner.getUniqueId(), UUID_BYTES))
                .insert();
        query("INSERT INTO order_states(id, state) VALUES (1, ?)")
                .single(call().bind(OrderState.UNCLAIMED.stateId()))
                .insert();
        query("INSERT INTO item_returns(worker_uuid, material, stack, amount) VALUES (?, 'STONE', ?, 2)")
                .single(call().bind(worker.getUniqueId(), UUID_BYTES).bind("{\"type\":\"STONE\",\"amount\":1}"))
                .insert();

        SqLiteTestDatabase.create(oldDirectory);

        var legacy = orderData.retrieveOrderById(1).join().orElseThrow();
        Assertions.assertEquals(0, legacy.extensions());
        Assertions.assertEquals(0, legacy.deliveryBonus());
        Assertions.assertEquals(3.0, legacy.fee());
        Assertions.assertEquals(
                2,
                orderData
                        .retrieveItemReturnCount(worker.getUniqueId(), ReturnKind.WORKER)
                        .join());
    }

    private SimpleOrder createOrder(OrderContent... contents) {
        orderData
                .submitOrder(owner, new SimpleOrder(owner.getUniqueId(), "lines", 0.0).toFullOrder(List.of(contents)))
                .join();
        var orders = orderData
                .retrieveOrdersByPlayer(owner, OrderState.UNCLAIMED, OrderState.RECEIVED)
                .join();
        return orders.get(orders.size() - 1);
    }

    private static ItemStack enchanted(Material material) {
        var stack = new ItemStack(material);
        stack.addUnsafeEnchantment(Enchantment.SHARPNESS, 5);
        return stack;
    }

    private List<Integer> deliver(OfflinePlayer player, SimpleOrder order, DeliveryPart... parts) {
        return orderData.submitDeliveries(player, reload(order), List.of(parts)).join();
    }

    @Test
    void linesAreStoredInOrderWithTheirKind() {
        var order = createOrder(
                new OrderContent(0, new ItemStack(Material.STONE), false, 10, 10.0),
                new OrderContent(1, enchanted(Material.STONE), true, 5, 20.0));

        var contents = orderData.retrieveFullOrder(order).join().contents();

        Assertions.assertEquals(
                List.of(0, 1), contents.stream().map(OrderContent::line).toList());
        Assertions.assertFalse(contents.get(0).exact());
        Assertions.assertTrue(contents.get(1).exact());
        Assertions.assertEquals(enchanted(Material.STONE), contents.get(1).stack());
        Assertions.assertEquals(4.0, contents.get(1).unitPrice(), 0.0001);
    }

    @Test
    void twoLinesOfTheSameMaterialAreDeliveredAndReturnedSeparately() {
        var order = createOrder(
                new OrderContent(0, new ItemStack(Material.STONE), false, 10, 10.0),
                new OrderContent(1, enchanted(Material.STONE), true, 5, 20.0));
        claim(order);

        Assertions.assertEquals(
                List.of(3, 5),
                deliver(
                        worker,
                        order,
                        new DeliveryPart(0, new ItemStack(Material.STONE), 3),
                        new DeliveryPart(1, enchanted(Material.STONE), 7)));

        var full = orderData.retrieveFullOrder(reload(order)).join();
        Assertions.assertEquals(3, full.line(0).orElseThrow().delivered());
        Assertions.assertEquals(5, full.line(1).orElseThrow().delivered());

        orderData.submitUnclaimOrder(reload(order)).join();

        var returns = orderData
                .retrieveItemReturns(worker.getUniqueId(), ReturnKind.WORKER)
                .join();
        Assertions.assertEquals(2, returns.size());
        Assertions.assertEquals(new ItemStack(Material.STONE), returns.get(0).stack());
        Assertions.assertEquals(3, returns.get(0).amount());
        Assertions.assertEquals(enchanted(Material.STONE), returns.get(1).stack());
        Assertions.assertEquals(5, returns.get(1).amount());
    }

    @Test
    void deliveriesAreCappedPerLineInListOrderAndStoredPerStack() {
        var order = createOrder(
                new OrderContent(0, new ItemStack(Material.DIAMOND_SWORD), false, 10, 10.0),
                new OrderContent(1, new ItemStack(Material.DIRT), false, 5, 5.0));
        claim(order);

        Assertions.assertEquals(
                List.of(6, 4, 0, 2),
                deliver(
                        worker,
                        order,
                        new DeliveryPart(0, new ItemStack(Material.DIAMOND_SWORD), 6),
                        new DeliveryPart(0, enchanted(Material.DIAMOND_SWORD), 6),
                        new DeliveryPart(0, new ItemStack(Material.DIAMOND_SWORD), 1),
                        new DeliveryPart(1, new ItemStack(Material.DIRT), 2)));

        var deliveries = orderData
                .retrieveFullOrder(reload(order))
                .join()
                .line(0)
                .orElseThrow()
                .deliveries();
        Assertions.assertEquals(2, deliveries.size());
        Assertions.assertEquals(
                new ItemStack(Material.DIAMOND_SWORD), deliveries.get(0).stack());
        Assertions.assertEquals(6, deliveries.get(0).amount());
        Assertions.assertEquals(
                enchanted(Material.DIAMOND_SWORD), deliveries.get(1).stack());
        Assertions.assertEquals(4, deliveries.get(1).amount());
        Assertions.assertEquals(3, SqLiteTestDatabase.count("order_deliveries", "order_id = " + order.id()));
    }

    @Test
    void deliveriesToUnknownLinesAreNotRecorded() {
        var order = createOrder(10, 5);
        claim(order);

        Assertions.assertEquals(
                List.of(0), deliver(worker, order, new DeliveryPart(7, new ItemStack(Material.STONE), 3)));
        Assertions.assertEquals(0, SqLiteTestDatabase.count("order_deliveries", "order_id = " + order.id()));
    }

    @Test
    void enchantedStackComesBackUnchangedAfterReceive() {
        var order = createOrder(new OrderContent(0, new ItemStack(Material.DIAMOND_SWORD), false, 2, 10.0));
        claim(order);
        deliver(worker, order, new DeliveryPart(0, enchanted(Material.DIAMOND_SWORD), 2));

        var delivered = orderData.submitOrderDelivered(reload(order)).join().orElseThrow();
        var stacks = delivered.deliveredStacks();

        var expected = enchanted(Material.DIAMOND_SWORD);
        Assertions.assertEquals(List.of(expected, expected), stacks);

        var pending = stacks.stream().map(stack -> new ItemAmount(stack, 1)).toList();
        Assertions.assertTrue(
                orderData.submitOrderReceive(reload(order), pending).join());
        var waiting = orderData
                .retrieveItemReturns(owner.getUniqueId(), ReturnKind.OWNER)
                .join();
        Assertions.assertEquals(2, waiting.size());
        Assertions.assertEquals(expected, waiting.get(0).stack());
    }

    @Test
    void enchantedStackComesBackUnchangedAfterAbort() {
        var order = createOrder(new OrderContent(0, new ItemStack(Material.DIAMOND_SWORD), false, 5, 10.0));
        claim(order);
        deliver(worker, order, new DeliveryPart(0, enchanted(Material.DIAMOND_SWORD), 2));

        orderData.submitUnclaimOrder(reload(order)).join();

        var returns = orderData
                .retrieveItemReturns(worker.getUniqueId(), ReturnKind.WORKER)
                .join();
        Assertions.assertEquals(1, returns.size());
        Assertions.assertEquals(
                enchanted(Material.DIAMOND_SWORD), returns.get(0).stack());
        Assertions.assertEquals(2, returns.get(0).amount());
        Assertions.assertEquals(0, SqLiteTestDatabase.count("order_deliveries", "order_id = " + order.id()));
    }

    @Test
    void deliveredStacksAreSplitByMaxStackSize() {
        var order = createOrder(new OrderContent(0, new ItemStack(Material.STONE), false, 100, 10.0));
        claim(order);
        deliver(worker, order, new DeliveryPart(0, new ItemStack(Material.STONE, 64), 100));

        var stacks = orderData.retrieveFullOrder(reload(order)).join().deliveredStacks();

        Assertions.assertEquals(
                List.of(64, 36), stacks.stream().map(ItemStack::getAmount).toList());
    }

    @Test
    void payoutsPerWorkerEqualTheSummedDeliveredAmounts() {
        var other = SqLiteTestDatabase.player(UUID.randomUUID());
        var order = createOrder(new OrderContent(0, new ItemStack(Material.STONE), false, 10, 100.0));
        claim(order);
        deliver(worker, order, new DeliveryPart(0, new ItemStack(Material.STONE), 3));
        deliver(other, order, new DeliveryPart(0, enchanted(Material.STONE), 5));
        deliver(worker, order, new DeliveryPart(0, enchanted(Material.STONE), 2));

        var content = orderData.retrieveFullOrder(reload(order)).join().line(0).orElseThrow();

        Assertions.assertEquals(5, content.workerAmount().get(worker.getUniqueId()));
        Assertions.assertEquals(5, content.workerAmount().get(other.getUniqueId()));
        Assertions.assertEquals(50.0, content.payments().get(worker.getUniqueId()), 0.0001);
        Assertions.assertEquals(50.0, content.payments().get(other.getUniqueId()), 0.0001);
    }

    @Test
    void materialPriceIgnoresExactLines() {
        var order = createOrder(
                new OrderContent(0, new ItemStack(Material.STONE), false, 10, 10.0),
                new OrderContent(1, enchanted(Material.STONE), true, 1, 500.0));
        SqLiteTestDatabase.setState(order.id(), OrderState.DELIVERED.stateId(), COMPANY);

        orderData.refreshMaterialPrices();

        var price = orderData.retrieveMaterialPrice("STONE").join();
        Assertions.assertEquals(1.0, price.maxPrice(), 0.0001);
        Assertions.assertEquals(1.0, price.avgPrice(), 0.0001);
    }

    @Test
    void forcedDeliveryWorksOnlyOnClaimedOrdersWhateverWasDelivered() {
        var open = createOrder(10, 5);
        var claimed = createOrder(10, 5);
        claim(claimed);
        addDelivery(claimed, 0, Material.STONE, 3);

        Assertions.assertTrue(
                orderData.submitForcedDelivery(reload(open)).join().isEmpty());
        var delivered = orderData.submitForcedDelivery(reload(claimed)).join().orElseThrow();

        Assertions.assertEquals(OrderState.DELIVERED, delivered.state());
        Assertions.assertEquals(3, delivered.delivered());
        Assertions.assertEquals(OrderState.UNCLAIMED, reload(open).state());
        Assertions.assertTrue(
                orderData.submitForcedDelivery(reload(claimed)).join().isEmpty());
    }

    @Test
    void adminDeletionReturnsDeliveriesOfClaimedOrders() {
        var order = createOrder(10, 5);
        claim(order);
        addDelivery(order, 0, Material.STONE, 4);

        var workers = orderData.submitAdminDeletion(reload(order)).join();

        Assertions.assertEquals(Set.of(worker.getUniqueId()), workers.orElseThrow());
        Assertions.assertTrue(orderData.retrieveOrderById(order.id()).join().isEmpty());
        for (var table : List.of("order_content", "order_states", "orders")) {
            Assertions.assertEquals(0, SqLiteTestDatabase.count(table, "id = " + order.id()));
        }
        Assertions.assertEquals(0, SqLiteTestDatabase.count("order_deliveries", "order_id = " + order.id()));
        var returns = orderData
                .retrieveItemReturns(worker.getUniqueId(), ReturnKind.WORKER)
                .join();
        Assertions.assertEquals(4, returns.getFirst().amount());
    }

    @Test
    void adminDeletionOfUnclaimedOrdersReturnsNothing() {
        var order = createOrder(10, 5);

        Assertions.assertEquals(
                Set.of(), orderData.submitAdminDeletion(reload(order)).join().orElseThrow());
        Assertions.assertTrue(orderData.retrieveOrderById(order.id()).join().isEmpty());
    }

    @Test
    void deliveredOrdersAreNotDeletedByAdmins() {
        var order = createOrder(10, 5);
        claim(order);
        addDelivery(order, 0, Material.STONE, 4);
        SqLiteTestDatabase.setState(order.id(), OrderState.DELIVERED.stateId(), COMPANY);

        Assertions.assertTrue(
                orderData.submitAdminDeletion(reload(order)).join().isEmpty());
        Assertions.assertEquals(OrderState.DELIVERED, reload(order).state());
        Assertions.assertEquals(1, SqLiteTestDatabase.count("order_deliveries", "order_id = " + order.id()));
    }

    @Test
    void claimExtensionsAddToTheDeliveryBonus() {
        var order = createOrder(10, 5);

        Assertions.assertFalse(orderData.submitClaimExtension(reload(order), 5).join());
        claim(order);
        Assertions.assertTrue(orderData.submitClaimExtension(reload(order), 5).join());
        Assertions.assertTrue(orderData.submitClaimExtension(reload(order), 2).join());

        Assertions.assertEquals(7, reload(order).deliveryBonus());
    }

    @Test
    void unclaimedExtensionsMoveThePurgeWithoutCountingAnExtension() {
        var order = createOrder(10, 5);
        age(order, 10);
        var before = reload(order).claimed();

        Assertions.assertTrue(
                orderData.submitUnclaimedExtension(reload(order), 24).join());

        Assertions.assertEquals(before.plusHours(24), reload(order).claimed());
        Assertions.assertEquals(0, reload(order).extensions());
        claim(order);
        Assertions.assertFalse(
                orderData.submitUnclaimedExtension(reload(order), 24).join());
    }

    @Test
    void adminListsFilterByOwnerAndCompanyNewestFirst() {
        var first = createOrder(10, 5);
        var second = createOrder(10, 5);
        var third = createOrder(10, 5);
        claim(second);
        var foreign = query("INSERT INTO orders(owner_uuid, name) VALUES (?, 'other') RETURNING id")
                .single(call().bind(worker.getUniqueId(), UUID_BYTES))
                .map(row -> row.getInt("id"))
                .first()
                .orElseThrow();
        query("INSERT INTO order_states(id, state) VALUES (?, 0)")
                .single(call().bind(foreign))
                .insert();

        Assertions.assertEquals(
                List.of(third.id(), second.id(), first.id()),
                ids(orderData
                        .retrieveOrdersForAdmin(owner.getUniqueId(), null, 1, 10)
                        .join()));
        Assertions.assertEquals(
                List.of(second.id()),
                ids(orderData.retrieveOrdersForAdmin(null, COMPANY, 1, 10).join()));
        Assertions.assertEquals(
                List.of(second.id()),
                ids(orderData
                        .retrieveOrdersForAdmin(owner.getUniqueId(), COMPANY, 1, 10)
                        .join()));
        Assertions.assertEquals(
                List.of(second.id()),
                ids(orderData
                        .retrieveOrdersForAdmin(owner.getUniqueId(), null, 2, 1)
                        .join()));
        Assertions.assertEquals(
                4, orderData.retrieveOrdersForAdmin(null, null, 1, 10).join().size());
    }

    private static List<Integer> ids(List<SimpleOrder> orders) {
        return orders.stream().map(SimpleOrder::id).toList();
    }
}
