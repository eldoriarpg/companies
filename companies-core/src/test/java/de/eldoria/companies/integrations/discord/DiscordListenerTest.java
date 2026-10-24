/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.integrations.discord;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import de.eldoria.companies.components.order.OrderSource;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.elements.DiscordSettings;
import de.eldoria.companies.configuration.elements.companylevel.CompanyLevel;
import de.eldoria.companies.data.wrapper.company.CompanyDetails;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.OrderContent;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.events.company.CompanyLevelUpEvent;
import de.eldoria.companies.events.order.OrderCreatedEvent;
import de.eldoria.companies.events.order.OrderDoneEvent;
import de.eldoria.companies.util.Times;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordListenerTest {
    private static final String OWNER_NAME = "OwnerAlice";
    private static final String COMPANY_NAME = "Acme Holdings";
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-00000000a11c");
    private static final CompanyProfile COMPANY =
            new CompanyProfile(77, COMPANY_NAME, Times.now(), 2, CompanyDetails.NONE, List.of());

    private static ServerMock server;

    private final List<Embed> posted = new ArrayList<>();
    private final WebhookPayload payload = new WebhookPayload(Optional.empty(), Optional.empty());

    @BeforeAll
    static void startServer() {
        server = MockBukkit.mock();
    }

    @AfterAll
    static void stopServer() {
        MockBukkit.unmock();
    }

    private DiscordListener listener(DiscordSettings settings) {
        return new DiscordListener(
                () -> settings,
                new DiscordEmbeds(key -> key, InstantSource.fixed(Instant.EPOCH)),
                amount -> String.format(Locale.ROOT, "$%.2f", amount),
                posted::add);
    }

    private static DiscordSettings settings(String yaml) {
        ObjectMapper mapper = YAMLMapper.builder()
                .changeDefaultVisibility(visibility -> visibility.withFieldVisibility(JsonAutoDetect.Visibility.ANY))
                .build();
        return mapper.readValue(yaml, DiscordSettings.class);
    }

    private static FullOrder order() {
        return new FullOrder(
                12,
                OWNER,
                "Iron please",
                Times.now(),
                COMPANY.id(),
                Times.now(),
                OrderState.DELIVERED,
                5.0,
                0,
                0,
                List.of(
                        new OrderContent(0, new ItemStack(Material.IRON_INGOT), false, 64, 100.0),
                        new OrderContent(1, new ItemStack(Material.OAK_LOG), true, 3, 20.0)));
    }

    private void assertAnonymous() {
        var json = payload.write(posted);
        assertFalse(json.contains(OWNER_NAME), json);
        assertFalse(json.contains(OWNER.toString()), json);
        assertFalse(json.contains(COMPANY_NAME), json);
        assertFalse(json.contains("5.00"), json);
    }

    @Test
    void newOrderPostsContentsAndPriceOnly() {
        var owner = server.addPlayer(OWNER_NAME);

        listener(new DiscordSettings()).onOrderCreated(new OrderCreatedEvent(order(), owner, OrderSource.PLAYER));

        assertEquals(1, posted.size());
        var embed = posted.getFirst();
        assertEquals("discord.orderCreated", embed.title());
        assertEquals("**Iron please**", embed.description());
        assertEquals("64× Iron Ingot\n3× Oak Log", embed.fields().get(0).value());
        assertEquals("$120.00", embed.fields().get(1).value());
        assertAnonymous();
    }

    @Test
    void completedOrderNamesNeitherOwnerNorCompany() {
        listener(new DiscordSettings()).onOrderDone(new OrderDoneEvent(order(), COMPANY));

        assertEquals("discord.orderCompleted", posted.getFirst().title());
        assertEquals(2, posted.getFirst().fields().size());
        assertAnonymous();
    }

    @Test
    void completedSimpleOrderShowsItsName() {
        listener(new DiscordSettings()).onOrderDone(new OrderDoneEvent(new SimpleOrder(OWNER, "Wood"), COMPANY));

        assertEquals("**Wood**", posted.getFirst().description());
        assertEquals(List.of(), posted.getFirst().fields());
        assertAnonymous();
    }

    @Test
    void levelUpShowsPlainLevelName() {
        var oldLevel = new CompanyLevel();
        var newLevel = new CompanyLevel();
        newLevel.levelName("<gold>Gold Tier</gold>");

        listener(new DiscordSettings()).onLevelUp(new CompanyLevelUpEvent(COMPANY, oldLevel, newLevel));

        var embed = posted.getFirst();
        assertTrue(embed.description().contains(COMPANY_NAME));
        assertEquals("Gold Tier", embed.fields().getFirst().value());
    }

    @Test
    void eventsTurnedOffAreNotPosted() {
        var listener = listener(settings("events: [LEVEL_UP]"));

        listener.onOrderCreated(new OrderCreatedEvent(order(), server.addPlayer(), OrderSource.PLAYER));
        listener.onOrderDone(new OrderDoneEvent(order(), COMPANY));

        assertEquals(List.of(), posted);
    }
}
