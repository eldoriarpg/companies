/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api.handshake;

import com.fasterxml.jackson.annotation.JsonTypeName;
import de.eldoria.companies.web.api.Message;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The gateway's answer to {@link Hello}.
 *
 * @param id the message id; an answer carries the id of its request, see {@link Message#id()}
 * @param v the protocol version, see {@link Message#v()}
 * @param protocol the protocol version used on this connection
 * @param gateway whether the gateway is hosted by Eldoria or by the server owner
 * @param features the features the gateway supports
 * @param version the state of the plugin version
 * @param deprecated the deprecated parts this server still uses
 * @param iconCached whether the gateway has the icon of the hello's {@link Branding#iconHash()}; when not, the node
 * sends it with {@link BrandingUpdate}
 */
@JsonTypeName("welcome")
public record Welcome(
        @Nullable String id,
        int v,
        int protocol,
        GatewayKind gateway,
        List<String> features,
        VersionStatus version,
        List<DeprecatedPart> deprecated,
        boolean iconCached)
        implements Message {}
