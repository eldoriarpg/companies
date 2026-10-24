/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway.policy;

import de.eldoria.companies.web.api.Protocol;
import de.eldoria.companies.web.api.handshake.DeprecatedPart;
import de.eldoria.companies.web.api.handshake.PartKind;
import de.eldoria.companies.web.api.handshake.VersionState;
import de.eldoria.companies.web.api.handshake.VersionStatus;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The gateway's version policy: which plugin versions are deprecated or discontinued, and which protocol parts are
 * deprecated and when they go. Each gateway release ships the policy as of its build ({@value #RESOURCE}); a file can
 * replace it.
 * <p>
 * The first rule whose range holds a plugin version decides its state; a version no rule holds, such as one newer
 * than the gateway knows, is supported. Dates move a version along on their own: a deprecated version is
 * discontinued from its {@code discontinuedAt}, and a version marked discontinued from a future {@code since} is
 * deprecated until then.
 *
 * @param windowMonths how long a deprecated part keeps working when its entry names no {@code removedAt}
 * @param versions the rules for plugin versions, in order
 * @param deprecations the deprecated protocol parts
 */
public record VersionPolicy(int windowMonths, List<VersionRule> versions, List<Deprecation> deprecations) {
    /**
     * The resource the gateway's own policy is read from.
     */
    public static final String RESOURCE = "version-policy.json";

    /**
     * Plugin versions in the range {@code from} (inclusive) to {@code before} (exclusive); a missing end is open.
     *
     * @param from the first version of the range
     * @param before the first version after the range
     * @param licence {@code TRIAL} or {@code BOUGHT} to apply only to servers with that kind of Lyna key; none for all
     * @param status deprecated or discontinued
     * @param since when a discontinued version stops working
     * @param discontinuedAt when a deprecated version is discontinued
     * @param updateTo the version to update to
     * @param message a plain sentence explaining the problem and what to do
     */
    public record VersionRule(
            @Nullable String from,
            @Nullable String before,
            @Nullable String licence,
            VersionState status,
            @Nullable LocalDate since,
            @Nullable LocalDate discontinuedAt,
            @Nullable String updateTo,
            @Nullable String message) {

        boolean holds(PluginVersion version, @Nullable String keyKind) {
            if (licence != null && !licence.equals(keyKind)) return false;
            if (from != null && version.compareTo(PluginVersion.parse(from)) < 0) return false;
            return before == null || version.compareTo(PluginVersion.parse(before)) < 0;
        }
    }

    /**
     * A deprecated protocol part.
     *
     * @param kind field, message type, feature or protocol version
     * @param name the field as {@code type.path}, the message type, the feature name or the protocol version
     * @param since when it was deprecated: the release date of the first plugin version sending the replacement
     * @param removedAt when the gateway stops reading and sending it; the window after {@code since} when missing
     * @param replacement what replaces it
     * @param message a plain sentence for admins
     * @param from for fields and message types, the first plugin version still using it; none for all
     * @param before for fields and message types, the first plugin version no longer using it; none for all
     */
    public record Deprecation(
            PartKind kind,
            String name,
            LocalDate since,
            @Nullable LocalDate removedAt,
            @Nullable String replacement,
            @Nullable String message,
            @Nullable String from,
            @Nullable String before) {}

    /**
     * @return the policy shipped with the gateway
     */
    public static VersionPolicy bundled() {
        try (InputStream in = VersionPolicy.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            return read(Objects.requireNonNull(in, "The gateway ships no " + RESOURCE));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * @param file a policy file
     * @return its policy
     * @throws IOException when it cannot be read
     */
    public static VersionPolicy read(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return read(in);
        }
    }

    /**
     * @param in a policy as JSON
     * @return the policy
     * @throws IllegalArgumentException when a rule or entry is incomplete
     */
    public static VersionPolicy read(InputStream in) {
        VersionPolicy policy = Protocol.mapper().readValue(in, VersionPolicy.class);
        policy.validate();
        return policy;
    }

    private void validate() {
        if (windowMonths <= 0) throw new IllegalArgumentException("windowMonths must be positive");
        for (VersionRule rule : versions) {
            if (rule.status() != VersionState.DEPRECATED && rule.status() != VersionState.DISCONTINUED) {
                throw new IllegalArgumentException("A version rule is DEPRECATED or DISCONTINUED: " + rule);
            }
            if (rule.status() == VersionState.DEPRECATED && rule.discontinuedAt() == null) {
                throw new IllegalArgumentException("A deprecated version needs discontinuedAt: " + rule);
            }
            if (rule.status() == VersionState.DISCONTINUED && rule.since() == null) {
                throw new IllegalArgumentException("A discontinued version needs since: " + rule);
            }
        }
        for (Deprecation deprecation : deprecations) {
            if (deprecation.kind() == PartKind.UNKNOWN) {
                throw new IllegalArgumentException("Unknown kind of deprecated part: " + deprecation);
            }
        }
    }

    /**
     * @param plugin the plugin version
     * @param keyKind the kind of the server's Lyna key, {@code TRIAL} or {@code BOUGHT}; none on a self-hosted gateway
     * @param today the date
     * @return the version's state
     */
    public VersionStatus status(String plugin, @Nullable String keyKind, LocalDate today) {
        PluginVersion version = PluginVersion.parse(plugin);
        for (VersionRule rule : versions) {
            if (!rule.holds(version, keyKind)) continue;
            LocalDate end = rule.status() == VersionState.DEPRECATED ? rule.discontinuedAt() : rule.since();
            if (end != null && !today.isBefore(end)) {
                return new VersionStatus(VersionState.DISCONTINUED, end, null, rule.updateTo(), rule.message());
            }
            return new VersionStatus(VersionState.DEPRECATED, null, end, rule.updateTo(), rule.message());
        }
        return new VersionStatus(VersionState.SUPPORTED, null, null, null, null);
    }

    /**
     * The deprecated parts a node still uses: features it lists, the protocol version used with it, and fields and
     * message types its plugin version sends. Parts past their window are left out; they are no longer read.
     *
     * @param plugin the plugin version
     * @param features the features the node lists
     * @param protocol the protocol version used
     * @param today the date
     * @return the parts with the date they go
     */
    public List<DeprecatedPart> inUse(String plugin, Collection<String> features, int protocol, LocalDate today) {
        PluginVersion version = PluginVersion.parse(plugin);
        return deprecations.stream()
                .filter(part -> today.isBefore(removedAt(part)))
                .filter(part -> switch (part.kind()) {
                    case FEATURE -> features.contains(part.name());
                    case PROTOCOL -> part.name().equals(Integer.toString(protocol));
                    case FIELD, MESSAGE -> inRange(version, part);
                    case UNKNOWN -> false;
                })
                .map(part -> new DeprecatedPart(
                        part.kind(), part.name(), part.since(), removedAt(part), part.replacement(), part.message()))
                .toList();
    }

    /**
     * @param today the date
     * @return the features whose window has passed, which the gateway no longer offers
     */
    public Set<String> removedFeatures(LocalDate today) {
        return deprecations.stream()
                .filter(part -> part.kind() == PartKind.FEATURE)
                .filter(part -> !today.isBefore(removedAt(part)))
                .map(Deprecation::name)
                .collect(Collectors.toUnmodifiableSet());
    }

    private LocalDate removedAt(Deprecation part) {
        LocalDate removedAt = part.removedAt();
        return removedAt != null ? removedAt : part.since().plusMonths(windowMonths);
    }

    private static boolean inRange(PluginVersion version, Deprecation part) {
        String from = part.from();
        String before = part.before();
        if (from != null && version.compareTo(PluginVersion.parse(from)) < 0) return false;
        return before == null || version.compareTo(PluginVersion.parse(before)) < 0;
    }
}
