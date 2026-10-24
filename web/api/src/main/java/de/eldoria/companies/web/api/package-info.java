/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
/**
 * The dashboard protocol: the messages between a Companies node and the dashboard gateway, as plain Jackson records
 * and enums without Bukkit types, so the plugin and the gateway can both depend on this module.
 *
 * <h2>Envelope</h2>
 * Every message is a {@link de.eldoria.companies.web.api.Message}: {@code {"type", "id", "v", …}}. {@code type} picks
 * the record, {@code id} pairs a request with its answer, {@code v} is the
 * {@link de.eldoria.companies.web.api.Protocol#VERSION protocol version}. Read and write them with
 * {@link de.eldoria.companies.web.api.Protocol#mapper()}.
 *
 * <h2>Messages</h2>
 * <ul>
 *     <li>{@link de.eldoria.companies.web.api.handshake}: {@code hello} from the node, {@code welcome} from the
 *     gateway with the protocol used, the gateway kind, the version status and the deprecated parts in use, and
 *     {@code branding.update} with the server's name and icon.</li>
 *     <li>{@link de.eldoria.companies.web.api.session}: create a session for a player, the short link and code, the
 *     login, and the end of sessions.</li>
 *     <li>{@link de.eldoria.companies.web.api.snapshot}: the snapshot request and answer with the views of player,
 *     company, own orders and rankings.</li>
 *     <li>{@link de.eldoria.companies.web.api.update}: subscribe, unsubscribe and patches.</li>
 *     <li>{@link de.eldoria.companies.web.api.action}: actions by name, see
 *     {@link de.eldoria.companies.web.api.Actions}, and their results.</li>
 *     <li>{@link de.eldoria.companies.web.api.link}: ping, pong, errors and unknown messages.</li>
 * </ul>
 *
 * <h2>Compatibility</h2>
 * <ul>
 *     <li>Payloads only grow. A new field is always {@code @Nullable}, because older peers do not send it; readers
 *     ignore fields they do not know.</li>
 *     <li>Enum values are written as the constant's name, such as {@code SELF_HOSTED}. Feature and action names,
 *     ranking and range keys are strings, not enums.</li>
 *     <li>Enums are open: an unknown value reads as {@code UNKNOWN}. An unknown message type reads as
 *     {@link de.eldoria.companies.web.api.link.UnknownMessage}.</li>
 *     <li>What a node offers is told by {@link de.eldoria.companies.web.api.Features feature names}, not by versions.
 *     </li>
 *     <li>Nothing is renamed in place; the old part is marked {@link de.eldoria.companies.web.api.DeprecatedSince}
 *     next to its replacement.</li>
 *     <li>The JSON Schema of each protocol version is committed under {@code schema/v<N>/} in the resources and
 *     generated with {@code ./gradlew :web-api:generateSchema}; a test fails while it is stale. Recorded messages of
 *     every protocol version under the test resources {@code messages/v<N>/} must validate against the schema and
 *     read with the current records. Once a protocol version is released, its recorded messages are never changed,
 *     only added.</li>
 * </ul>
 * <p>
 * Every package is {@code @NullMarked}: a value is non-null unless marked {@code @Nullable}.
 */
@NullMarked
@DefaultAnnotationForMethods(NonNull.class)
@DefaultAnnotationForParameters(NonNull.class)
package de.eldoria.companies.web.api;

import edu.umd.cs.findbugs.annotations.DefaultAnnotationForMethods;
import edu.umd.cs.findbugs.annotations.DefaultAnnotationForParameters;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
