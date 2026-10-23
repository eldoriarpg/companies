/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.suggest;

import net.strokkur.commands.CustomSuggestion;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Suggests the time ranges of the company ranking by their ids: {@code all}, {@code 30d} and {@code 7d}.
 */
@CustomSuggestion
@Retention(RetentionPolicy.SOURCE)
public @interface TimeRanges {}
