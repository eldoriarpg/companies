/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.company;

import de.eldoria.companies.components.company.ICompanyStats;

import java.time.LocalDateTime;

public class CompanyStats implements ICompanyStats {
    private final int id;
    private final String name;
    private final LocalDateTime founded;
    private final int memberCount;
    private final int orderCount;
    private final double price;
    private final int deliveredItems;

    public CompanyStats(
            int id,
            String name,
            LocalDateTime founded,
            int memberCount,
            int orderCount,
            double price,
            int deliveredItems) {
        this.id = id;
        this.name = name;
        this.founded = founded;
        this.memberCount = memberCount;
        this.orderCount = orderCount;
        this.price = price;
        this.deliveredItems = deliveredItems;
    }

    @Override
    public int id() {
        return id;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public LocalDateTime founded() {
        return founded;
    }

    @Override
    public int memberCount() {
        return memberCount;
    }

    @Override
    public int orderCount() {
        return orderCount;
    }

    @Override
    public double price() {
        return price;
    }

    @Override
    public int deliveredItems() {
        return deliveredItems;
    }
}
