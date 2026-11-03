/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository;

import de.chojo.sadu.queries.configuration.QueryConfiguration;
import de.eldoria.companies.data.wrapper.perk.OwnedPerk;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.LocalDateTime;
import java.util.List;

import javax.sql.DataSource;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What every dialect does with the perks of companies. The database is started once per dialect and emptied before
 * each test.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class PerkDataContract {
    private static final LocalDateTime FIRST = LocalDateTime.of(2026, 11, 3, 12, 0);
    private static final LocalDateTime LATER = LocalDateTime.of(2026, 11, 4, 9, 30);

    private DataSource dataSource;
    private APerkData perks;

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
     * @return the dialect's perk table
     */
    protected abstract APerkData perks();

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
        query("DELETE FROM company_perks").single().delete();
        query("DELETE FROM companies").single().delete();
        perks = perks();
    }

    @Test
    void aCompanyOwnsEachPerkOnceOldestFirst() {
        var builders = company("Builders");
        var miners = company("Miners");

        assertTrue(perks.add(builders, "builder-tool", LATER));
        assertTrue(perks.add(builders, "second-home", FIRST));
        assertFalse(perks.add(builders, "builder-tool", FIRST));
        assertTrue(perks.add(miners, "builder-tool", FIRST));

        assertEquals(
                List.of(new OwnedPerk(builders, "second-home", FIRST), new OwnedPerk(builders, "builder-tool", LATER)),
                perks.perks(builders));
        assertEquals(List.of(new OwnedPerk(miners, "builder-tool", FIRST)), perks.perks(miners));
    }

    @Test
    void aPerkIsRemovedOnce() {
        var builders = company("Builders");
        perks.add(builders, "builder-tool", FIRST);

        assertTrue(perks.remove(builders, "builder-tool"));
        assertFalse(perks.remove(builders, "builder-tool"));
        assertEquals(List.of(), perks.perks(builders));
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
