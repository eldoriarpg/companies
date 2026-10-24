/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {spawnSync} from 'node:child_process'
import {LINTERS} from './linters.mjs'

/**
 * Runs the linters of the registry in `linters.mjs`.
 *
 * <p>`node scripts/lint.mjs` runs all of them and fails when any of them fails, after every one has
 * had its say, so one run shows the whole picture. `--audit` runs all of them and never fails, for
 * surveying what they print. `node scripts/lint.mjs <name> [args]` runs one, with the arguments
 * handed on to it.
 *
 * @param args the command line arguments
 */
function main(args) {
    if (args.length > 0 && args[0] !== '--audit') {
        const [name, ...rest] = args
        const linter = LINTERS.find(candidate => candidate.name === name)
        if (!linter) {
            console.error(`No linter named '${name}'. Known: ${LINTERS.map(candidate => candidate.name).join(', ')}`)
            process.exit(2)
        }
        process.exit(run(linter, rest))
    }

    const audit = args[0] === '--audit'
    const failed = LINTERS.filter(linter => run(linter, []) !== 0).map(linter => linter.name)
    if (failed.length === 0) return
    console.error(`\nLinters failed: ${failed.join(', ')}`)
    if (!audit) process.exit(1)
}

/**
 * Runs one linter with extra arguments and answers its exit code.
 *
 * @param linter the registry entry
 * @param extra arguments appended to its command
 * @returns the exit code, 1 when it could not be started
 */
function run(linter, extra) {
    const [command, ...args] = linter.command
    const executable = command === 'node' ? process.execPath : command
    const result = spawnSync(executable, [...args, ...extra], {stdio: 'inherit'})
    return result.status ?? 1
}

main(process.argv.slice(2))
