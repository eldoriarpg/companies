package de.eldoria.companies.data.wrapper.company;

import de.eldoria.companies.permissions.CompanyPermission;
import de.eldoria.companies.util.Colors;
import de.eldoria.eldoutilities.localization.MessageComposer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class CompanyMember {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yy HH:mm");
    private final UUID uuid;
    private int company;
    private long permission;

    private CompanyMember(int company, UUID uuid, long permission) {
        this.company = company;
        this.uuid = uuid;
        this.permission = permission;
    }

    public static CompanyMember forCompany(SimpleCompany company, OfflinePlayer player) {
        return new CompanyMember(company.id(), player.getUniqueId(), 0);
    }

    public static CompanyMember forCompanyId(int company, OfflinePlayer player) {
        return new CompanyMember(company, player.getUniqueId(), 0);
    }

    public static CompanyMember withoutCompany(OfflinePlayer player) {
        return new CompanyMember(-1, player.getUniqueId(), 0);
    }

    public static CompanyMember of(int company, UUID player, long permission) {
        return new CompanyMember(company, player, permission);
    }

    public boolean hasPermission(CompanyPermission permissions) {
        if (isOwner()) return true;
        return permissions.hasPermission(permission);
    }

    public boolean hasPermissions(CompanyPermission... permissions) {
        if (isOwner()) return true;
        return CompanyPermission.hasPermission(permission, permissions);
    }

    public boolean hasAnyPermissions(CompanyPermission... permissions) {
        if (isOwner()) return true;
        return CompanyPermission.hasAnyPermission(permission, permissions);
    }

    public CompanyMember addPermission(CompanyPermission permission) {
        if (hasPermission(permission)) return this;
        this.permission += permission.mask();
        return this;
    }

    public CompanyMember removePermission(CompanyPermission permission) {
        if (isOwner()) return this;
        if (!hasPermission(permission)) return this;
        this.permission -= permission.mask();
        return this;
    }

    public OfflinePlayer player() {
        return Bukkit.getOfflinePlayer(uuid);
    }

    public int company() {
        return company;
    }

    public UUID uuid() {
        return uuid;
    }

    public long permission() {
        return permission;
    }

    public List<CompanyPermission> permissions() {
        return Arrays.stream(CompanyPermission.values()).filter(this::hasPermissions).collect(Collectors.toList());
    }

    public CompanyMember kick() {
        company = -1;
        return this;
    }

    public String statusComponent() {
        var hover = MessageComposer.create();
        if (player().isOnline()) {
            hover.text("<%s>", Colors.ACTIVE).localeCode("Online");
        } else {
            var lastSeen = LocalDateTime.ofInstant(Instant.ofEpochMilli(player().getLastPlayed()), ZoneId.systemDefault());
            hover.text("<%s>", Colors.NEUTRAL).localeCode("Seen").text(": %s", lastSeen.format(FORMATTER));
        }
        return hover.build();
    }

    public boolean isOwner() {
        return CompanyPermission.hasPermission(permission, CompanyPermission.OWNER);
    }
}
