/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.eldoria.companies.configuration.ChangeTracker;
import de.eldoria.companies.configuration.elements.dashboard.BrandingSettings;
import de.eldoria.companies.configuration.elements.generalsettings.Hooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@code overrides} section of {@code node.yml}: the few shared settings one server may set for itself, because
 * they depend on what that server has, such as its plugins. A setting left out uses the shared value. They are never
 * shared and are read at start, as all of {@code node.yml}.
 */
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "CanBeFinal"})
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NodeOverrides {
    private @Nullable Boolean messageBlocking;
    private @Nullable Boolean boardsEnabled;
    private @Nullable HookOverrides hooks;
    private @Nullable BrandingOverrides branding;

    public NodeOverrides() {}

    /**
     * @param shared {@code generalSettings.messageBlocking} of {@code config.yml}
     * @return the value on this server
     */
    public boolean messageBlocking(boolean shared) {
        return messageBlocking == null ? shared : messageBlocking;
    }

    /**
     * @param shared {@code boards.enabled} of {@code config.yml}
     * @return the value on this server
     */
    public boolean boardsEnabled(boolean shared) {
        return boardsEnabled == null ? shared : boardsEnabled;
    }

    /**
     * @param shared {@code generalSettings.hooks} of {@code config.yml}
     * @return the switches on this server
     */
    public Hooks hooks(Hooks shared) {
        if (hooks == null) return shared;
        return new Hooks(
                pick(hooks.placeholderapi, shared.placeholderapi()),
                pick(hooks.citizens, shared.citizens()),
                pick(hooks.fancynpcs, shared.fancynpcs()));
    }

    /**
     * @param shared {@code dashboard.branding} of {@code config.yml}
     * @return the name and icon of this server
     */
    public BrandingSettings branding(BrandingSettings shared) {
        if (branding == null) return shared;
        return new BrandingSettings(
                branding.name == null ? shared.name() : branding.name,
                branding.icon == null ? shared.icon() : branding.icon);
    }

    /**
     * @return the shared settings this server sets for itself
     */
    public List<ChangeTracker.Setting> overridden() {
        var found = new ArrayList<ChangeTracker.Setting>();
        if (messageBlocking != null) found.add(ChangeTracker.MESSAGE_BLOCKING);
        if (boardsEnabled != null) found.add(ChangeTracker.BOARDS_ENABLED);
        if (hooks != null) {
            if (hooks.placeholderapi != null) found.add(ChangeTracker.HOOKS.child("placeholderapi"));
            if (hooks.citizens != null) found.add(ChangeTracker.HOOKS.child("citizens"));
            if (hooks.fancynpcs != null) found.add(ChangeTracker.HOOKS.child("fancynpcs"));
        }
        if (branding != null) {
            if (branding.name != null) found.add(ChangeTracker.DASHBOARD.child("branding.name"));
            if (branding.icon != null) found.add(ChangeTracker.DASHBOARD.child("branding.icon"));
        }
        return found;
    }

    private static boolean pick(@Nullable Boolean override, boolean shared) {
        return override == null ? shared : override;
    }

    /**
     * The plugin hooks this server switches for itself.
     */
    @SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "CanBeFinal"})
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class HookOverrides {
        private @Nullable Boolean placeholderapi;
        private @Nullable Boolean citizens;
        private @Nullable Boolean fancynpcs;

        public HookOverrides() {}
    }

    /**
     * The dashboard name and icon of this server.
     */
    @SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "CanBeFinal"})
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BrandingOverrides {
        private @Nullable String name;
        private @Nullable String icon;

        public BrandingOverrides() {}
    }
}
