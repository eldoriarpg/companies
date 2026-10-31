/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.areas;

import de.eldoria.companies.components.area.OfferKind;
import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteAreaData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.area.AreaOffer;
import de.eldoria.companies.data.wrapper.area.CompanyArea;
import de.eldoria.companies.data.wrapper.company.CompanyDetails;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rents of company areas: a rent due is paid for another period or ends when the treasury can not pay, a cancelled
 * rent ends when it is due, and the company is told once before a rent is due.
 */
class AreaRentsTest {
    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 31, 12, 0);
    private static final UUID NODE = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    @TempDir
    Path directory;

    private final List<String> told = new ArrayList<>();
    private SqLiteAreaData areas;
    private AreaRents rents;
    private AreaRents.Payment payment = AreaRents.Payment.PAID;
    private LocalDateTime now = START;
    private int company;
    private int paid;

    @BeforeEach
    void setUp() throws Exception {
        SqLiteTestDatabase.create(directory);
        query("INSERT INTO companies(name) VALUES ('Tenants')").single().insert();
        company = query("SELECT id FROM companies WHERE name = 'Tenants'")
                .single()
                .map(row -> row.getInt("id"))
                .first()
                .orElseThrow();
        areas = new SqLiteAreaData();
        var profile = new CompanyProfile(
                company,
                "Tenants",
                START,
                1,
                CompanyDetails.NONE,
                List.of(CompanyMember.of(company, UUID.randomUUID(), CompanyPermission.OWNER.mask())));
        rents = new AreaRents(
                areas,
                id -> Optional.of(profile),
                (ignored, area, renewal) -> {
                    if (payment != AreaRents.Payment.PAID) return payment;
                    paid++;
                    return renewal.get() ? AreaRents.Payment.PAID : AreaRents.Payment.FAILED;
                },
                new AreaRents.Notices() {
                    @Override
                    public void due(CompanyProfile company, CompanyArea area, Duration left) {
                        told.add("due " + left.toHours());
                    }

                    @Override
                    public void renewed(CompanyProfile company, CompanyArea area, Duration period) {
                        told.add("renewed " + period.toDays());
                    }

                    @Override
                    public void unpaid(CompanyProfile company, CompanyArea area) {
                        told.add("unpaid");
                    }

                    @Override
                    public void ended(CompanyProfile company, CompanyArea area) {
                        told.add("ended");
                    }
                },
                () -> 24,
                () -> now,
                Logger.getAnonymousLogger());
    }

    @Test
    void aRentDueIsPaidForAnotherPeriod() {
        var id = rented();
        now = START.plusDays(7);

        rents.check();
        rents.check();

        assertEquals(1, paid);
        assertEquals(START.plusDays(14), areas.area(id).orElseThrow().rentUntil());
        assertEquals(List.of("renewed 7"), told);
    }

    @Test
    void aRentLongOverdueIsPaidOnceFromNow() {
        var id = rented();
        now = START.plusDays(30);

        rents.check();
        rents.check();

        assertEquals(1, paid);
        assertEquals(START.plusDays(37), areas.area(id).orElseThrow().rentUntil());
    }

    @Test
    void aRentTheTreasuryCanNotPayEnds() {
        var id = rented();
        now = START.plusDays(7);
        payment = AreaRents.Payment.UNPAID;

        rents.check();

        assertTrue(areas.area(id).orElseThrow().rentEnded());
        assertEquals(List.of("unpaid"), told);
    }

    @Test
    void aCancelledRentEndsWhenItIsDueWithoutPaying() {
        var id = rented();
        areas.stopRenewing(id);
        now = START.plusDays(6);
        rents.check();
        assertFalse(areas.area(id).orElseThrow().rentEnded());
        now = START.plusDays(7);

        rents.check();

        assertEquals(0, paid);
        assertTrue(areas.area(id).orElseThrow().rentEnded());
        assertEquals(List.of("ended"), told);
    }

    @Test
    void theCompanyIsToldOnceBeforeTheRentIsDue() {
        rented();
        now = START.plusDays(6);
        rents.check();
        now = START.plusDays(6).plusHours(12);

        rents.check();

        assertEquals(List.of("due 24"), told);
        assertEquals(0, paid);
    }

    private int rented() {
        var offer = areas.addOffer(
                        new AreaOffer(0, "plotsquared", "plots|1;1", "plots", NODE, OfferKind.RENT, 10, 7, null, START))
                .orElseThrow();
        return areas.add(new CompanyArea(
                        0,
                        company,
                        "plotsquared",
                        "plots|1;1",
                        "plots",
                        NODE,
                        OfferKind.RENT.source(),
                        null,
                        10,
                        START.plusDays(7),
                        START,
                        offer,
                        true,
                        false,
                        false))
                .orElseThrow();
    }
}
