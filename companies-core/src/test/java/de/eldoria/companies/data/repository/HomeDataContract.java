/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository;

import de.chojo.sadu.queries.configuration.QueryConfiguration;
import de.eldoria.companies.data.wrapper.area.CompanyHome;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.LocalDateTime;
import java.util.OptionalInt;
import java.util.UUID;

import javax.sql.DataSource;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What every dialect does with company homes and the teleports waiting for a player on another server.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class HomeDataContract {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 31, 12, 0);
    private static final UUID MEMBER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID NODE = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID OTHER_NODE = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    private DataSource dataSource;
    private AHomeData homes;

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
     * @return the dialect's home tables
     */
    protected abstract AHomeData homes();

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
        query("DELETE FROM home_teleports").single().delete();
        query("DELETE FROM company_homes").single().delete();
        query("DELETE FROM companies").single().delete();
        homes = homes();
    }

    @Test
    void aHomeIsSetMovedAndRemoved() {
        var company = company("Builders");

        assertTrue(homes.home(company).isEmpty());
        assertTrue(homes.set(home(company, 10.5, "survival")));
        assertTrue(homes.set(home(company, 20.5, "survival")));

        var home = homes.home(company).orElseThrow();
        assertEquals(20.5, home.x(), 0.001);
        assertEquals("survival", home.server());
        assertEquals(NODE, home.node());
        assertEquals(90f, home.yaw(), 0.001);
        assertEquals(NOW, home.setAt());
        assertTrue(homes.remove(company));
        assertFalse(homes.remove(company));
    }

    @Test
    void aTeleportWaitsOnceForThePlayerOnTheHomesServer() {
        var company = company("Builders");
        homes.travelling(MEMBER, company, OTHER_NODE, NOW.plusMinutes(1));

        assertEquals(OptionalInt.empty(), homes.arrived(MEMBER, NODE, NOW));
        assertEquals(OptionalInt.of(company), homes.arrived(MEMBER, OTHER_NODE, NOW));
        assertEquals(OptionalInt.empty(), homes.arrived(MEMBER, OTHER_NODE, NOW));
    }

    @Test
    void theProxyNameOfAServersHomesFollowsItsNodeFile() {
        var company = company("Builders");
        homes.set(home(company, 10.5, ""));

        homes.server(NODE, "survival");

        assertEquals("survival", homes.home(company).orElseThrow().server());
    }

    @Test
    void anExpiredTeleportIsNotTaken() {
        var company = company("Builders");
        homes.travelling(MEMBER, company, OTHER_NODE, NOW.minusSeconds(1));

        assertEquals(OptionalInt.empty(), homes.arrived(MEMBER, OTHER_NODE, NOW));
    }

    private static CompanyHome home(int company, double x, String server) {
        return new CompanyHome(company, NODE, server, "world", x, 64, -3.25, 90f, 10f, MEMBER, NOW);
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
