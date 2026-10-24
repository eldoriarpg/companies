#!/usr/bin/env node
/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
/**
 * API types linter: the committed protocol types must be what `scripts/generate-api-types.mjs` writes from the
 * committed protocol schema, so a changed record of `:web-api` is regenerated here rather than noticed as a field the
 * screen reads and nobody sends. Exit code 1 when the file is stale.
 */

import {readFileSync, existsSync} from 'fs'
import {renderProtocolTypes, PROTOCOL_TARGET} from './generate-api-types.mjs'
import {RED, GREEN, RESET, BOLD} from './lint-utils.mjs'

const committed = existsSync(PROTOCOL_TARGET) ? readFileSync(PROTOCOL_TARGET, 'utf-8') : ''
if (committed !== await renderProtocolTypes()) {
    console.log(`${RED}${BOLD}The generated types are out of date:${RESET} ${PROTOCOL_TARGET}`)
    console.log(`  Run ${BOLD}node scripts/generate-api-types.mjs${RESET} and commit what it writes.`)
    process.exit(1)
}
console.log(`${GREEN}The generated types match the protocol schema${RESET}`)
