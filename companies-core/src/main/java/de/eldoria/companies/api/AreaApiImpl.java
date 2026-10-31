/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.api;

import de.eldoria.companies.api.write.AreaOfferRequest;
import de.eldoria.companies.api.write.OfferProblem;
import de.eldoria.companies.api.write.OfferResult;
import de.eldoria.companies.api.write.WithdrawResult;
import de.eldoria.companies.areas.AreaOffers;
import de.eldoria.companies.areas.AreaProviders;
import de.eldoria.companies.components.area.AreaProvider;
import de.eldoria.companies.components.area.IAreaOffer;
import de.eldoria.companies.data.repository.AAreaData;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

/**
 * Registers area providers with the plugin's registry and offers areas through the offer service of
 * {@code /companyadmin area}: the area is picked on the main thread, the offer stored off it.
 */
final class AreaApiImpl implements AreaApi {
    private final AreaProviders providers;
    private final AAreaData areas;
    private final WriteAccess writes;
    private final Logger logger;
    private final Executor executor;

    AreaApiImpl(AreaProviders providers, AAreaData areas, WriteAccess writes, Logger logger, Executor executor) {
        this.providers = providers;
        this.areas = areas;
        this.writes = writes;
        this.logger = logger;
        this.executor = executor;
    }

    @Override
    public void register(Plugin plugin, AreaProvider provider) {
        providers.register(plugin, provider);
    }

    @Override
    public void unregister(String id) {
        providers.unregister(id);
    }

    @Override
    public List<String> providers() {
        return providers.providers();
    }

    @Override
    public CompletableFuture<OfferResult> offer(AreaOfferRequest request) {
        return writes.run(services -> CompletableFuture.supplyAsync(
                        () -> pick(services.areaOffers(), request), services.mainThread())
                .thenApplyAsync(picked -> offer(services.areaOffers(), request, picked), executor));
    }

    @Override
    public CompletableFuture<List<? extends IAreaOffer>> offers() {
        return CompletableFuture.<List<? extends IAreaOffer>>supplyAsync(
                () -> areas.openOffers().stream().map(ApiAreaOffer::new).toList(), executor);
    }

    @Override
    public CompletableFuture<WithdrawResult> withdraw(int id, String by) {
        Objects.requireNonNull(by, "by");
        return writes.run(services -> CompletableFuture.supplyAsync(
                () -> {
                    var result =
                            switch (services.areaOffers().withdraw(id)) {
                                case WITHDRAWN -> WithdrawResult.WITHDRAWN;
                                case TAKEN -> WithdrawResult.TAKEN;
                                case UNKNOWN -> WithdrawResult.UNKNOWN;
                            };
                    if (result == WithdrawResult.WITHDRAWN) logger.info("%s withdrew area offer %d".formatted(by, id));
                    return result;
                },
                executor));
    }

    private static AreaOffers.Picked pick(AreaOffers offers, AreaOfferRequest request) {
        var location = request.location();
        if (location != null) return offers.at(location);
        return offers.of(Objects.requireNonNull(request.provider()), Objects.requireNonNull(request.ref()));
    }

    private OfferResult offer(AreaOffers offers, AreaOfferRequest request, AreaOffers.Picked picked) {
        return switch (picked) {
            case AreaOffers.Picked.Refused refused -> new OfferResult.Rejected(problem(refused.problem()));
            case AreaOffers.Picked.Area area ->
                switch (offers.offer(area, request.kind(), request.price(), request.periodDays())) {
                    case AreaOffers.Stored.Done done -> {
                        logger.info("%s offered %s %s for %s"
                                .formatted(
                                        request.by(),
                                        area.ref(),
                                        request.kind(),
                                        done.offer().price()));
                        yield new OfferResult.Offered(new ApiAreaOffer(done.offer()));
                    }
                    case AreaOffers.Stored.Refused refused -> new OfferResult.Rejected(problem(refused.problem()));
                };
        };
    }

    private static OfferProblem problem(AreaOffers.Problem problem) {
        return switch (problem) {
            case NO_AREA -> OfferProblem.NO_AREA;
            case NOT_MOVABLE -> OfferProblem.NOT_MOVABLE;
            case NO_KEEPER -> OfferProblem.NO_KEEPER;
            case IN_USE -> OfferProblem.IN_USE;
            case OFFERED -> OfferProblem.ALREADY_OFFERED;
        };
    }
}
