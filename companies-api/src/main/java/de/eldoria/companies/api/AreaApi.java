/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.api;

import de.eldoria.companies.api.write.AreaOfferRequest;
import de.eldoria.companies.api.write.OfferResult;
import de.eldoria.companies.api.write.WithdrawResult;
import de.eldoria.companies.components.area.AreaProvider;
import de.eldoria.companies.components.area.IAreaOffer;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The plot and claim plugins companies can own areas of. Companies registers its own providers for PlotSquared and
 * WorldGuard when they are present; another plugin can register a provider for its own areas, best in its
 * {@code onEnable}. A provider is dropped when the plugin that registered it is disabled. Areas of a provider that is
 * not registered stay stored and are shown as unavailable until it is registered again.
 * <p>
 * It also offers areas of the server to companies, as {@code /companyadmin area} does.
 */
public interface AreaApi {
    /**
     * Registers a provider. A provider with the same id replaces the one registered before; when the plugin of the new
     * provider is disabled, the id is gone until the old one registers again, which Companies' own do on a restart.
     *
     * @param plugin   the plugin the provider belongs to; it is dropped when the plugin is disabled
     * @param provider the provider
     */
    void register(Plugin plugin, AreaProvider provider);

    /**
     * Removes the provider with the given id.
     *
     * @param id id of the provider
     */
    void unregister(String id);

    /**
     * The providers in use.
     *
     * @return the ids of the registered providers that are not switched off, lower case, in the order they are asked
     *     which area holds a location
     */
    List<String> providers();

    /**
     * Offers an area to companies to buy or rent, with the same checks as {@code /companyadmin area offer}: its plugin
     * can move it to a company, someone keeps it for the server (its owner, or nobody where the plugin allows that), no
     * company has it and it is not offered yet. The area plugins are asked on the main thread.
     * <p>
     * This is a write operation: before Companies is ready the future completes exceptionally with an
     * {@link ApiNotReadyException}.
     *
     * @param request the area and the terms
     * @return future of the stored offer, or why the area was not offered
     */
    CompletableFuture<OfferResult> offer(AreaOfferRequest request);

    /**
     * Reads the offers no company has yet.
     *
     * @return future of the open offers
     */
    CompletableFuture<List<? extends IAreaOffer>> offers();

    /**
     * Withdraws an offer no company has.
     * <p>
     * This is a write operation: before Companies is ready the future completes exceptionally with an
     * {@link ApiNotReadyException}.
     *
     * @param id id of the offer
     * @param by who withdraws it, for the server log
     * @return future of what came of it
     */
    CompletableFuture<WithdrawResult> withdraw(int id, String by);
}
