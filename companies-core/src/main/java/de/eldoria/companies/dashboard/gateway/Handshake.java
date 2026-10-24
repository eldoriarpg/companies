/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.gateway;

import de.eldoria.companies.data.wrapper.dashboard.StoredKey;
import de.eldoria.companies.web.api.Protocol;
import de.eldoria.companies.web.api.handshake.Branding;
import de.eldoria.companies.web.api.handshake.BrandingUpdate;
import de.eldoria.companies.web.api.handshake.Credentials;
import de.eldoria.companies.web.api.handshake.Hello;
import de.eldoria.companies.web.api.handshake.KeyKind;
import de.eldoria.companies.web.api.handshake.Limits;
import de.eldoria.companies.web.api.handshake.NodeInfo;
import de.eldoria.companies.web.api.handshake.Welcome;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Builds the node's {@link Hello} and sends the icon when the gateway does not have it yet.
 * <p>
 * The hello carries the icon's hash only. When the welcome says the gateway has no icon of that hash, the icon follows
 * in a {@link BrandingUpdate}.
 */
public final class Handshake {
    private final Identity identity;
    private final Supplier<Optional<StoredKey>> key;
    private final Supplier<List<String>> features;
    private final Supplier<Optional<Branding>> branding;
    private volatile @Nullable Branding sent;
    private volatile @Nullable KeyKind sentKind;

    /**
     * @param identity who the node is
     * @param key      the key to authenticate with, refreshed first on the primary
     * @param features the features the node offers now
     * @param branding reads the server's name and icon
     */
    public Handshake(
            Identity identity,
            Supplier<Optional<StoredKey>> key,
            Supplier<List<String>> features,
            Supplier<Optional<Branding>> branding) {
        this.identity = identity;
        this.key = key;
        this.features = features;
        this.branding = branding;
    }

    /**
     * Builds the hello of a new connection.
     *
     * @return the hello, empty when no key is stored
     */
    public Optional<Hello> hello() {
        var current = key.get();
        if (current.isEmpty()) return Optional.empty();
        var full = branding.get().orElse(null);
        sent = full;
        sentKind = current.get().kind();
        var announced = full == null ? null : new Branding(full.name(), null, full.iconHash());
        return Optional.of(new Hello(
                UUID.randomUUID().toString(),
                Protocol.VERSION,
                identity.plugin(),
                identity.schema(),
                identity.node(),
                features.get(),
                new Limits(identity.maxPageSize()),
                new Credentials(current.get().kind(), current.get().secret()),
                identity.userAgent(),
                announced));
    }

    /**
     * The kind of key the last hello carried.
     *
     * @return the kind, empty before the first hello
     */
    public Optional<KeyKind> sentKind() {
        return Optional.ofNullable(sentKind);
    }

    /**
     * The icon the gateway asked for with the welcome.
     *
     * @param welcome the gateway's answer to the hello
     * @return the branding update with the icon, empty when the gateway has it or there is none
     */
    public Optional<BrandingUpdate> iconFor(Welcome welcome) {
        var full = sent;
        if (welcome.iconCached() || full == null || full.icon() == null) return Optional.empty();
        return Optional.of(new BrandingUpdate(null, Protocol.VERSION, full));
    }

    /**
     * Who the node is.
     *
     * @param plugin      the plugin version
     * @param schema      the database schema version
     * @param node        the node's uid and role
     * @param userAgent   {@code Companies/<version>}
     * @param maxPageSize the most entries the node returns in one page
     */
    public record Identity(String plugin, String schema, NodeInfo node, String userAgent, int maxPageSize) {}
}
