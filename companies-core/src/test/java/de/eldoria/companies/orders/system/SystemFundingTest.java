/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders.system;

import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteOrderData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.data.wrapper.order.SystemDecision;
import de.eldoria.companies.orders.EconomyAccounts;
import de.eldoria.companies.orders.OrderRefunds;
import de.eldoria.companies.orders.SystemOwner;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemFundingTest {
    private static final String BANK = "Royal bank";

    @TempDir
    Path directory;

    private final Map<String, Double> balances = new HashMap<>();
    private SqLiteOrderData orderData;
    private SystemFunding funding;

    @BeforeEach
    void setUp() throws Exception {
        MockBukkit.mock();
        SqLiteTestDatabase.create(directory);
        orderData = new SqLiteOrderData(null, SqLiteTestDatabase.mapper());
        var accounts = new EconomyAccounts(MockBukkit.getMock(), economy());
        funding = new SystemFunding(accounts, orderData, Runnable::run, Logger.getAnonymousLogger());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void anAccountPaysOnlyWhatItHolds() {
        balances.put(BANK, 50.0);

        assertFalse(funding.withdraw(BANK, 60));
        assertTrue(funding.withdraw(BANK, 40));
        assertEquals(10.0, balances.get(BANK));
    }

    @Test
    void aRefundGoesToTheAccountThatPaidTheOrder() {
        orderData.saveSystemDecision(decision(1, BANK));

        funding.refund(1, 25);

        assertEquals(25.0, balances.get(BANK));
    }

    @Test
    void aRefundOfMintedMoneyOrAnUnknownOrderGoesNowhere() {
        orderData.saveSystemDecision(decision(1, null));

        funding.refund(1, 25);
        funding.refund(2, 25);

        assertTrue(balances.isEmpty());
    }

    @Test
    void refundsGoToTheOwnerOrForTheServerToThePayingAccount() {
        var owners = new HashMap<UUID, Double>();
        var refunds = OrderRefunds.of((owner, amount) -> owners.merge(owner, amount, Double::sum), funding);
        var player = UUID.randomUUID();
        orderData.saveSystemDecision(decision(7, BANK));

        refunds.refund(order(7, SystemOwner.ID), 25);
        refunds.refund(order(8, player), 30);

        assertEquals(25.0, balances.get(BANK));
        assertEquals(Map.of(player, 30.0), owners);
    }

    @Test
    void aRefundNeverExceedsWhatTheAccountPaid() {
        orderData.saveSystemDecision(decision(1, BANK));

        funding.refund(1, 40);

        assertEquals(25.0, balances.get(BANK));
    }

    private static SimpleOrder order(int id, UUID owner) {
        var now = LocalDateTime.now();
        return new SimpleOrder(id, owner, "order", now, -1, now, OrderState.UNCLAIMED, 0, 0, 0);
    }

    private static SystemDecision decision(int order, String account) {
        return new SystemDecision(order, "iron", DefinedRecord.KIND, LocalDateTime.now(), account, 25, "{}");
    }

    private Economy economy() {
        return (Economy) Proxy.newProxyInstance(
                Economy.class.getClassLoader(), new Class<?>[] {Economy.class}, (proxy, method, args) -> {
                    if (!(args != null && args.length == 2 && args[0] instanceof String account)) {
                        throw new UnsupportedOperationException(method.getName());
                    }
                    var amount = (double) args[1];
                    return switch (method.getName()) {
                        case "has" -> balances.getOrDefault(account, 0.0) >= amount;
                        case "withdrawPlayer" -> {
                            balances.merge(account, -amount, Double::sum);
                            yield new EconomyResponse(amount, 0, EconomyResponse.ResponseType.SUCCESS, null);
                        }
                        case "depositPlayer" -> {
                            balances.merge(account, amount, Double::sum);
                            yield new EconomyResponse(amount, 0, EconomyResponse.ResponseType.SUCCESS, null);
                        }
                        default -> throw new UnsupportedOperationException(method.getName());
                    };
                });
    }
}
