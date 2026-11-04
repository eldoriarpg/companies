/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.demo;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayCapTest {
    private final PayCap cap = new PayCap(Set.of("pay", "XConomy:pay"), BigDecimal.valueOf(1000), "no");

    @Test
    void readsAbbreviations() {
        assertEquals(0, BigDecimal.valueOf(1500).compareTo(PayCap.amount("1.5k")));
        assertEquals(0, BigDecimal.valueOf(2_000_000).compareTo(PayCap.amount("2M")));
        assertEquals(0, BigDecimal.valueOf(1_000_000_000).compareTo(PayCap.amount("1b")));
        assertEquals(0, BigDecimal.valueOf(250).compareTo(PayCap.amount("250")));
        assertNull(PayCap.amount("lots"));
        assertNull(PayCap.amount("k"));
        assertNull(PayCap.amount(""));
    }

    @Test
    void refusesPaymentsAboveTheCapAndUnreadableAmounts() {
        assertTrue(cap.exceeds("/pay Steve 1b"));
        assertTrue(cap.exceeds("/PAY Steve 1001"));
        assertTrue(cap.exceeds("/xconomy:pay Steve 2k"));
        assertTrue(cap.exceeds("/pay Steve 1e9"));
        assertTrue(cap.exceeds("/pay Steve lots"));
        assertTrue(cap.exceeds("/pay  Steve   5k"));
        assertTrue(cap.exceeds("/XCONOMY:PAY Steve 2k"));
        assertTrue(cap.exceeds("pay Steve 2k"));
        assertTrue(cap.exceeds("/pay Steve 1000.01 "));
    }

    @Test
    void letsSmallPaymentsAndOtherCommandsThrough() {
        assertFalse(cap.exceeds("/pay Steve 1000"));
        assertFalse(cap.exceeds("/pay Steve 1k"));
        assertFalse(cap.exceeds("/pay Steve"));
        assertFalse(cap.exceeds("/pay"));
        assertFalse(cap.exceeds("/order create 1b"));
        assertFalse(cap.exceeds("/payday Steve 1b"));
        assertFalse(cap.exceeds("/pay Steve -5000"));
    }

    @Test
    void showsTheCapWithoutTrailingZeros() {
        assertEquals("1000", new PayCap(Set.of("pay"), new BigDecimal("1000.0"), "no").shownMax());
        assertEquals("1500", new PayCap(Set.of("pay"), PayCap.amount("1.5k"), "no").shownMax());
        assertEquals("2.5", new PayCap(Set.of("pay"), new BigDecimal("2.50"), "no").shownMax());
    }

    @Test
    void capsNothingAtZero() {
        assertFalse(new PayCap(Set.of("pay"), BigDecimal.ZERO, "no").exceeds("/pay Steve 1b"));
    }
}
