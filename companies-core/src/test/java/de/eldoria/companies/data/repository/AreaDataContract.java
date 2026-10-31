/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository;

import de.chojo.sadu.queries.configuration.QueryConfiguration;
import de.eldoria.companies.data.wrapper.area.AreaSource;
import de.eldoria.companies.data.wrapper.area.CompanyArea;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What every dialect does with the areas of companies. The database is started once per dialect and emptied before
 * each test.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AreaDataContract {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 31, 12, 0);
    private static final UUID LINKER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID NODE = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID OTHER_NODE = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    private DataSource dataSource;
    private AAreaData areas;

    /**
     * Starts a migrated database.
     *
     * @return its data source
     */
    protected abstract DataSource start() throws Exception;

    /**
     * Stops the database.
     */
    protected abstract void stop();

    /**
     * @return the dialect's area table
     */
    protected abstract AAreaData areas();

    @BeforeAll
    void startDatabase() throws Exception {
        dataSource = start();
    }

    @AfterAll
    void stopDatabase() {
        if (dataSource != null) stop();
    }

    @BeforeEach
    void setUp() {
        QueryConfiguration.setDefault(QueryConfiguration.builder(dataSource).build());
        query("DELETE FROM company_areas").single().delete();
        query("DELETE FROM companies").single().delete();
        areas = areas();
    }

    @Test
    void anAreaIsStoredAndReadBack() {
        var company = company("Builders");
        var id = areas.add(linked(company, "plots|1;2", NODE)).orElseThrow();

        var area = areas.area(id).orElseThrow();

        assertEquals(company, area.company());
        assertEquals("plotsquared", area.provider());
        assertEquals("plots|1;2", area.ref());
        assertEquals("plots", area.world());
        assertEquals(NODE, area.node());
        assertEquals(AreaSource.LINKED, area.source());
        assertEquals(LINKER, area.linkedBy());
        assertEquals(NOW, area.created());
        assertEquals(area, areas.byRef("plotsquared", "plots|1;2").orElseThrow());
    }

    @Test
    void anAreaBelongsToOneCompanyAtATime() {
        var first = company("Builders");
        var second = company("Miners");
        areas.add(linked(first, "plots|1;2", NODE)).orElseThrow();

        assertTrue(areas.add(linked(second, "plots|1;2", NODE)).isEmpty());
        assertTrue(areas.add(linked(second, "plots|1;3", NODE)).isPresent());
        assertEquals(1, areas.ofCompany(first).size());
    }

    @Test
    void areasAreFoundByCompanyServerAndLinker() {
        var company = company("Builders");
        var rented = new CompanyArea(
                0,
                company,
                "worldguard",
                "world/shop",
                "world",
                OTHER_NODE,
                AreaSource.RENTED,
                null,
                50,
                NOW.plusDays(7),
                NOW);
        var plot = areas.add(linked(company, "plots|1;2", NODE)).orElseThrow();
        var shop = areas.add(rented).orElseThrow();

        assertEquals(
                List.of(plot, shop),
                areas.ofCompany(company).stream().map(CompanyArea::id).toList());
        assertEquals(
                List.of(shop),
                areas.ofNode(OTHER_NODE).stream().map(CompanyArea::id).toList());
        assertEquals(
                List.of(plot),
                areas.linkedBy(LINKER).stream().map(CompanyArea::id).toList());
        assertEquals(NOW.plusDays(7), areas.area(shop).orElseThrow().rentUntil());
    }

    @Test
    void anAreaIsRemovedOnce() {
        var company = company("Builders");
        var id = areas.add(linked(company, "plots|1;2", NODE)).orElseThrow();

        assertTrue(areas.remove(id));
        assertFalse(areas.remove(id));
        assertTrue(areas.byRef("plotsquared", "plots|1;2").isEmpty());
    }

    private static CompanyArea linked(int company, String ref, UUID node) {
        return new CompanyArea(0, company, "plotsquared", ref, "plots", node, AreaSource.LINKED, LINKER, 0, null, NOW);
    }

    private static int company(String name) {
        query("INSERT INTO companies(name) VALUES (?)")
                .single(call().bind(name))
                .insert();
        return query("SELECT id FROM companies WHERE name = ?")
                .single(call().bind(name))
                .map(row -> row.getInt("id"))
                .first()
                .orElseThrow();
    }
}
