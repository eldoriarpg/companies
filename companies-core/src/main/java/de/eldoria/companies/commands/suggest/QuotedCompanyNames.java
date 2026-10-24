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
 * Suggests company names starting with the input, quoted where a string argument needs it.
 */
@CustomSuggestion
@Retention(RetentionPolicy.SOURCE)
public @interface QuotedCompanyNames {}
