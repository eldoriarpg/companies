/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements.events;

import de.eldoria.companies.configuration.elements.systemorders.MoneySource;
import de.eldoria.companies.data.wrapper.event.EventKind;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One template of {@code events.yml}: an event the primary creates again and again, every week at a day and time
 * ({@code every: SATURDAY 18:00}), at fixed dates ({@code dates: ["2026-12-24 18:00"]}), or both, each time running
 * for {@code duration}. Times are in the server's time zone.
 */
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "RedundantNoArgConstructor"})
public class EventTemplate {
    /**
     * The longest id a template may have, as the events store it.
     */
    public static final int MAX_ID_LENGTH = 64;

    private String id = "";
    private String name = "";
    private boolean enabled = true;
    private EventKind kind = EventKind.TAX_REDUCTION;
    private double value = 0.0;
    private List<String> materials = new ArrayList<>();
    private List<Integer> levels = new ArrayList<>();
    private String every = "";
    private List<String> dates = new ArrayList<>();
    private String duration = "24h";
    private MoneySource.@Nullable SourceType funding = null;
    private String account = "";
    private double budget = 0.0;

    public EventTemplate() {}

    /**
     * @param id    id of the template
     * @param kind  what its events change
     * @param value how much they change
     */
    public EventTemplate(String id, EventKind kind, double value) {
        this.id = id;
        this.kind = kind;
        this.value = value;
    }

    /**
     * The id its events keep to name the template. Templates without one, or with one an earlier template has, create
     * nothing.
     *
     * @return the id, trimmed
     */
    public String id() {
        return id == null ? "" : id.trim();
    }

    /**
     * @return the name of its events, the id when none is set
     */
    public String name() {
        return name == null || name.isBlank() ? id() : name.trim();
    }

    public void name(String name) {
        this.name = name;
    }

    /**
     * @return whether the template creates events
     */
    public boolean enabled() {
        return enabled;
    }

    public void enabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @return what its events change
     */
    public EventKind kind() {
        return kind == null ? EventKind.TAX_REDUCTION : kind;
    }

    /**
     * @return how much its events change, see {@link EventKind}
     */
    public double value() {
        return value;
    }

    /**
     * @return the materials its events are limited to, empty for every material
     */
    public List<String> materials() {
        return materials == null
                ? List.of()
                : materials.stream().filter(Objects::nonNull).toList();
    }

    public void materials(List<String> materials) {
        this.materials = new ArrayList<>(materials);
    }

    /**
     * @return the company levels its events are limited to, empty for every company
     */
    public List<Integer> levels() {
        return levels == null
                ? List.of()
                : levels.stream().filter(Objects::nonNull).toList();
    }

    public void levels(List<Integer> levels) {
        this.levels = new ArrayList<>(levels);
    }

    /**
     * @return the weekly start, such as {@code SATURDAY 18:00}, empty for none
     */
    public String every() {
        return every == null ? "" : every.trim();
    }

    public void every(String every) {
        this.every = every;
    }

    /**
     * @return the fixed starts, such as {@code 2026-12-24 18:00}
     */
    public List<String> dates() {
        return dates == null
                ? List.of()
                : dates.stream().filter(Objects::nonNull).toList();
    }

    public void dates(List<String> dates) {
        this.dates = new ArrayList<>(dates);
    }

    /**
     * @return how long each event runs, such as {@code 48h}
     */
    public String duration() {
        return duration == null ? "" : duration.trim();
    }

    public void duration(String duration) {
        this.duration = duration;
    }

    /**
     * @return where the money of a multiplier comes from, null for the other kinds
     */
    public MoneySource.@Nullable SourceType funding() {
        return funding;
    }

    public void funding(MoneySource.@Nullable SourceType funding) {
        this.funding = funding;
    }

    /**
     * @return the account paying a multiplier funded from an account, trimmed; empty for the collector account
     */
    public String account() {
        return account == null ? "" : account.trim();
    }

    public void account(String account) {
        this.account = account;
    }

    /**
     * @return what each event of a multiplier may pay, 0 for no budget
     */
    public double budget() {
        return Math.max(0, budget);
    }

    public void budget(double budget) {
        this.budget = budget;
    }
}
