/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.support;

import net.strokkur.commands.CustomRequirement;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Shows a command only to senders who may create debug reports, see {@link Requirements#mayDebug}.
 */
@CustomRequirement
@Retention(RetentionPolicy.SOURCE)
public @interface DebugAccess {}
