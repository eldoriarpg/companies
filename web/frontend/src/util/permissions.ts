/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {MemberPermission} from '~/api/generated/protocol'

/** The permissions a member can be given or have taken away; the owner's is handed on only by a transfer. */
export const GRANTABLE: readonly MemberPermission[] = [
    MemberPermission.INVITE,
    MemberPermission.KICK,
    MemberPermission.MANAGE_ORDERS,
    MemberPermission.MANAGE_PERMISSIONS,
    MemberPermission.EDIT_PROFILE,
    MemberPermission.TREASURY,
]

/**
 * Whether a member holds a permission. The owner holds every one, as in game.
 *
 * @param permissions the member's permissions
 * @param permission the permission
 * @returns true when the member holds it
 */
export function holds(permissions: readonly MemberPermission[], permission: MemberPermission): boolean {
    return permissions.includes(MemberPermission.OWNER) || permissions.includes(permission)
}

/**
 * Whether one member may remove another, as the plugin decides it: the owner removes anyone, a member who may kick
 * removes members who may not, and nobody removes the owner.
 *
 * @param actor the permissions of the member who wants to remove
 * @param target the permissions of the member who would be removed
 * @returns true when the plugin would allow it
 */
export function mayKick(actor: readonly MemberPermission[], target: readonly MemberPermission[]): boolean {
    if (target.includes(MemberPermission.OWNER)) return false
    if (actor.includes(MemberPermission.OWNER)) return true
    return actor.includes(MemberPermission.KICK) && !target.includes(MemberPermission.KICK)
}

/**
 * The permissions a member may give or take away: those they hold themselves, given they may manage permissions.
 *
 * @param actor the permissions of the member who edits
 * @returns the permissions they may change, none without the permission to manage them
 */
export function editable(actor: readonly MemberPermission[]): MemberPermission[] {
    if (!holds(actor, MemberPermission.MANAGE_PERMISSIONS)) return []
    return GRANTABLE.filter(permission => holds(actor, permission))
}
