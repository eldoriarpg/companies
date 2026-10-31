/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.areas;

import de.eldoria.companies.data.repository.AAreaData;
import de.eldoria.companies.data.repository.AHomeData;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * What the commands of company areas and homes work with.
 *
 * @param providers   the plot and claim plugins areas come from
 * @param areas       the areas of companies
 * @param homes       the homes of companies
 * @param teleports   teleports members home
 * @param sync        keeps this server's areas in step with their companies
 * @param node        this server
 * @param proxyServer the name of this server in the proxy, empty for none
 */
public record AreaServices(
        AreaProviders providers,
        AAreaData areas,
        AHomeData homes,
        HomeTeleports teleports,
        AreaSync sync,
        UUID node,
        Supplier<String> proxyServer) {}
