/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.events.order.OrderExpiredEvent;
import de.eldoria.companies.events.order.OrderRemovedEvent;
import de.eldoria.eldoutilities.localization.MessageComposer;
import de.eldoria.eldoutilities.messages.MessageSender;
import de.eldoria.eldoutilities.messages.Replacement;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class ExpiringService implements Runnable, Listener {
    private final Plugin plugin;
    private final AOrderData orderData;
    private final ACompanyData companyData;
    private final Configuration configuration;
    private final MessageSender messageSender;

    private ExpiringService(Plugin plugin, AOrderData orderData, ACompanyData companyData, Configuration configuration) {
        messageSender = MessageSender.getPluginMessageSender(plugin);
        this.plugin = plugin;
        this.orderData = orderData;
        this.companyData = companyData;
        this.configuration = configuration;
    }

    public static void start(Plugin plugin, AOrderData orderData, ACompanyData companyData, Configuration configuration, ScheduledExecutorService executorService) {
        var expiringService = new ExpiringService(plugin, orderData, companyData, configuration);
        var interval = configuration.generalSettings().orderCheckInterval();
        executorService.scheduleAtFixedRate(expiringService, 10L, interval, TimeUnit.MINUTES);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        var hours = configuration.companySettings()
                                 .deliveryHours();
        companyData.retrievePlayerCompany(event.getPlayer())
                   .whenComplete(optCompand -> {
                       if (optCompand.isEmpty()) return;
                       orderData.retrieveExpiredOrders(hours + hours / 2)
                                .whenComplete((simpleOrders, throwable) -> {
                                    if (simpleOrders == null || simpleOrders.isEmpty()) return;
                                    reportOrderExpiring(event.getPlayer(), simpleOrders);
                                });
                   });
    }

    private void reportOrderExpiring(Player player, List<SimpleOrder> orders) {
        var composer = MessageComposer.create()
                                      .localeCode("expiring.notice")
                                      .newLine();
        for (var order : orders) {
            composer.localeCode("expiring.element",
                            Replacement.create("name", order.fullName()), Replacement.create("duration", order.runningOutTime(configuration)))
                    .text("<click:run_command:/company order info %s>[", order.id())
                    .localeCode("info")
                    .text("]</click>")
                    .newLine();
        }
        messageSender.sendMessage(player, composer);
    }

    @Override
    public void run() {
        var expired = orderData.retrieveExpiredOrders(configuration.companySettings()
                                                                   .deliveryHours())
                               .join();

        for (var order : expired) {
            orderData.submitUnclaimOrder(order)
                     .join();
            var profile = companyData.retrieveCompanyById(order.company())
                                     .asFuture()
                                     .exceptionally(err -> {
                                         plugin.getLogger()
                                               .log(Level.SEVERE, "Something went wrong", err);
                                         return Optional.empty();
                                     })
                                     .thenApply(r -> r.flatMap(p -> companyData.retrieveCompanyProfile(p).join()))
                                     .join();
            if (profile.isEmpty()) continue;
            plugin.getServer()
                  .getPluginManager()
                  .callEvent(new OrderExpiredEvent(order, profile.get()));
        }

        expired = orderData.retrieveDeadOrders(configuration.orderSetting()
                                                            .maxUnclaimedHours())
                           .join();

        for (var order : expired) {
            orderData.submitOrderPurge(order)
                     .join();
            plugin.getServer()
                  .getPluginManager()
                  .callEvent(new OrderRemovedEvent(order));
        }
    }
}
