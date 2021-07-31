package de.eldoria.companies.data.wrapper.company;

import de.eldoria.companies.permissions.CompanyPermissions;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.UUID;

public class CompanyMember {
    int company;
    UUID uuid;
    long permission;

    public CompanyMember(int company, UUID uuid, long permission) {
        this.company = company;
        this.uuid = uuid;
        this.permission = permission;
    }

    public boolean hasPermission(CompanyPermissions permissions) {
        return permissions.hasPermission(permission);
    }

    public boolean hasPermissions(CompanyPermissions... permissions) {
        return CompanyPermissions.hasPermission(permission, permissions);
    }

    public void addPermission(CompanyPermissions permission) {
        if (hasPermission(permission)) return;
        this.permission += permission.mask();
    }

    public void removePermission(CompanyPermissions permission) {
        if (!hasPermission(permission)) return;
        this.permission -= permission.mask();
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
}
