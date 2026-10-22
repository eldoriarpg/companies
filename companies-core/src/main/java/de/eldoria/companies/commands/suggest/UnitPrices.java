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
 * Suggests the market average unit price of the item a draft action is about, unless it is an exact item.
 */
@CustomSuggestion
@Retention(RetentionPolicy.SOURCE)
public @interface UnitPrices {}
