/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
import {spawn, spawnSync} from 'node:child_process'
import {existsSync, rmSync} from 'node:fs'
import {resolve} from 'node:path'

/**
 * Full frontend verification, in fail-fast order: linters, then type-check, then the production
 * build. Each stage is cheaper than the one after it, so a failure surfaces as early as possible.
 *
 * <p>The linters are the registry in `linters.mjs`, run by `lint.mjs`, which is also what
 * `npm run lint` runs. `--skip-lint` leaves them out for a caller that has just run them.
 *
 * <p>`nuxi build` does not type-check, so `nuxi typecheck` runs as its own stage. It reads the
 * application only: the server routes are checked against Nitro's tsconfig and the stories against
 * a tsconfig of their own. `nuxi build` does not always exit on its own once the bundle is
 * written, so it runs detached and is killed as soon as the server entrypoint appears.
 */
const nuxi = resolve('node_modules/.bin/nuxi')

/**
 * Runs one stage and ends the build with its exit code when it fails.
 *
 * @param args the arguments handed to node
 */
function stage(args) {
    const result = spawnSync(process.execPath, args, {stdio: 'inherit'})
    if (result.status !== 0) process.exit(result.status ?? 1)
}

/**
 * Kills the build's process group. A group that has already exited on its own is what the kill was
 * for, so the "no such process" it answers with is not a failure.
 *
 * @param pid the group leader
 */
function killGroup(pid) {
    try {
        process.kill(-pid, 'SIGKILL')
    } catch (error) {
        if (error.code !== 'ESRCH') throw error
    }
}

/** Runs the production build and exits once its server entrypoint is written. */
function build() {
    if (existsSync('.output')) rmSync('.output', {recursive: true, force: true})
    const child = spawn(process.execPath, [nuxi, 'build'], {stdio: ['ignore', 'inherit', 'inherit'], detached: true})
    child.unref()
    let done = false
    const interval = setInterval(() => {
        if (!existsSync('.output/server/index.mjs')) return
        clearInterval(interval)
        done = true
        setTimeout(() => killGroup(child.pid), 2000)
    }, 1000)
    child.on('exit', () => {
        clearInterval(interval)
        process.exit(done || existsSync('.output/server/index.mjs') ? 0 : 1)
    })
}

if (!process.argv.includes('--skip-lint')) stage([resolve('scripts/lint.mjs')])
stage([nuxi, 'typecheck'])
stage([resolve('node_modules/typescript/bin/tsc'), '--noEmit', '-p', '.nuxt/tsconfig.server.json'])
stage([resolve('node_modules/typescript/bin/tsc'), '-p', 'tsconfig.e2e.json'])
build()
