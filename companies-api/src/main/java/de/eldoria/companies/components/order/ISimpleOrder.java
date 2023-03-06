/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.order;

import java.time.LocalDateTime;
import java.util.UUID;

public interface ISimpleOrder {
    void name(String name);

    int id();

    UUID owner();

    String name();

    String fullName();

    LocalDateTime created();

    int company();

    LocalDateTime claimed();

    OrderState state();
}
