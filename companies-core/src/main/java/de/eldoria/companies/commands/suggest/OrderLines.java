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
 * Suggests the lines of the order in argument {@code id} that still miss items, with the item and the missing amount as tooltip.
 */
@CustomSuggestion
@Retention(RetentionPolicy.SOURCE)
public @interface OrderLines {}
