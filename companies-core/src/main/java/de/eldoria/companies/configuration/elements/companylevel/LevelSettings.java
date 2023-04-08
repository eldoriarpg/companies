/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements.companylevel;

import de.eldoria.companies.components.level.ILevelSettings;

@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "RedundantNoArgConstructor"})
public class LevelSettings implements ILevelSettings {
    private int maxMembers = 20;
    private int maxOrders = 5;

    public LevelSettings() {
    }

    @Override
    public int maxMembers() {
        return maxMembers;
    }

    @Override
    public int maxOrders() {
        return maxOrders;
    }

    public void maxMembers(int maxMembers) {
        this.maxMembers = maxMembers;
    }

    public void maxOrders(int maxOrders) {
        this.maxOrders = maxOrders;
    }
}
