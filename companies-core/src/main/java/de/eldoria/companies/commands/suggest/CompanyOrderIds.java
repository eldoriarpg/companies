/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.suggest;

import net.strokkur.commands.CustomSuggestion;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Suggests the ids of the orders the sender's company works on, with the order name as tooltip.
 */
@CustomSuggestion
@Retention(RetentionPolicy.SOURCE)
public @interface CompanyOrderIds {}
