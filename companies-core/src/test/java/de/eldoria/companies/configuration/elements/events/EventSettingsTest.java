/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements.events;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import de.eldoria.companies.data.wrapper.offer.OfferKind;
import org.junit.jupiter.api.Test;
import tools.jackson.dataformat.yaml.YAMLMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventSettingsTest {
    @Test
    void theCapDefaultsToThree() {
        assertEquals(3.0, new EventSettings().maxMultiplier());
    }

    @Test
    void theCapIsNeverBelowOneNorUnreadable() {
        var settings = new EventSettings();

        settings.maxMultiplier(2.5);
        assertEquals(2.5, settings.maxMultiplier());
        settings.maxMultiplier(0.5);
        assertEquals(1.0, settings.maxMultiplier());
        settings.maxMultiplier(-3);
        assertEquals(1.0, settings.maxMultiplier());
        settings.maxMultiplier(Double.NaN);
        assertEquals(1.0, settings.maxMultiplier());
        settings.maxMultiplier(Double.POSITIVE_INFINITY);
        assertEquals(1.0, settings.maxMultiplier());
    }

    @Test
    void aTemplateReadsItsOffers() {
        var mapper = YAMLMapper.builder()
                .changeDefaultVisibility(visibility -> visibility.withFieldVisibility(JsonAutoDetect.Visibility.ANY))
                .build();
        var yaml = """
                templates:
                  - id: harvest
                    kind: TAX_REDUCTION
                    value: 5
                    every: SATURDAY 18:00
                    offers:
                      - kind: CONTEST
                        name: Log race
                        criteria: "#logs"
                        amount: 300
                        price: 2
                        prizes: 100+50
                      - material: oak_log
                        amount: 500
                        price: 1.5
                """;

        var settings = mapper.readValue(yaml, EventSettings.class);

        var offers = settings.templates().getFirst().offers();
        assertEquals(2, offers.size());
        assertEquals(OfferKind.CONTEST, offers.getFirst().kind());
        assertEquals("#logs", offers.getFirst().criteria());
        assertEquals("100+50", offers.getFirst().prizes());
        assertEquals(OfferKind.GOAL, offers.get(1).kind());
        assertEquals("minted", offers.get(1).money());
        assertEquals(64, offers.get(1).chunk());
        assertTrue(new EventTemplate().offers().isEmpty());
    }

    @Test
    void theOffersOfATemplateSurviveWritingTheSettingsBack() {
        var mapper = YAMLMapper.builder()
                .changeDefaultVisibility(visibility -> visibility.withFieldVisibility(JsonAutoDetect.Visibility.ANY))
                .build();
        var settings = mapper.readValue("""
                templates:
                  - id: harvest
                    kind: TAX_REDUCTION
                    value: 5
                    offers:
                      - kind: CONTEST
                        name: Log race
                        criteria: "#logs"
                        money: treasury
                        amount: 300
                        prizes: 100+50
                """, EventSettings.class);

        var reread = mapper.readValue(mapper.writeValueAsString(settings), EventSettings.class);

        var offer = reread.templates().getFirst().offers().getFirst();
        assertEquals(OfferKind.CONTEST, offer.kind());
        assertEquals("Log race", offer.name());
        assertEquals("#logs", offer.criteria());
        assertEquals("treasury", offer.money());
        assertEquals(300, offer.amount());
        assertEquals("100+50", offer.prizes());
    }
}
