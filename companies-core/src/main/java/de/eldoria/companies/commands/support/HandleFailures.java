/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.support;

import net.strokkur.commands.CustomExecutorWrapper;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Wraps executors with {@link CommandFailures#wrap}, so a thrown {@link CommandFailure} becomes an error message.
 */
@CustomExecutorWrapper
@Retention(RetentionPolicy.SOURCE)
public @interface HandleFailures {}
