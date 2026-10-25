/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import type {NodeInfo} from '~/api/generated/protocol'

/** The characters of a node id shown where a server has no name. */
const SHORT_ID = 8

/**
 * How a server of the network is named to an admin: by the name its `node.yml` gives it, otherwise by its role and the
 * start of its id.
 *
 * @param node the server
 * @param role the label of its role
 * @returns the name to show
 */
export function nodeLabel(node: Pick<NodeInfo, 'uid' | 'name'>, role: string): string {
    const name = node.name?.trim()
    return name ? name : `${role} ${node.uid.slice(0, SHORT_ID)}`
}
