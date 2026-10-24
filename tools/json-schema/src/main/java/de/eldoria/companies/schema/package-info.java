/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
/**
 * A build tool that writes JSON Schemas (draft 2020-12) from Java records, sealed interfaces, enums and classes, see
 * {@link de.eldoria.companies.schema.JsonSchemas}. It is used only by test-scope generators that produce committed
 * schema files, such as the dashboard protocol's schema in {@code web/api}; nothing ships it at runtime.
 */
@NullMarked
@DefaultAnnotationForMethods(NonNull.class)
@DefaultAnnotationForParameters(NonNull.class)
package de.eldoria.companies.schema;

import edu.umd.cs.findbugs.annotations.DefaultAnnotationForMethods;
import edu.umd.cs.findbugs.annotations.DefaultAnnotationForParameters;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
