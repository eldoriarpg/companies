/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
/**
 * Every linter the frontend is held to, in the order they run.
 *
 * <p>This is the one list. `npm run lint`, `npm run lint:audit` and the build all read it, so a
 * linter green on one of them is green on all of them. Each entry is a name, which is what
 * `node scripts/lint.mjs <name>` takes, and the command that runs it from the frontend directory.
 *
 * <p>ESLint carries the rules about the code itself, the project's own among them. jscpd measures
 * duplication. The scripts that remain check what is not code in a file ESLint reads: the square
 * corners, the browser storage catalogue, the em dash and comment rules, and the generated types.
 */
export const LINTERS = [
    {name: 'eslint', command: ['node', 'node_modules/eslint/bin/eslint.js', '--max-warnings=0']},
    {name: 'duplication', command: ['node', 'node_modules/jscpd/run-jscpd.js', '--config', '.jscpd.json']},
    {name: 'square-corners', command: ['node', 'scripts/lint-square-corners.mjs']},
    {name: 'browser-storage', command: ['node', 'scripts/lint-browser-storage.mjs']},
    {name: 'em-dash', command: ['node', 'scripts/lint-em-dash.mjs']},
    {name: 'comments', command: ['node', 'scripts/lint-comments.mjs']},
    {name: 'api-types', command: ['node', 'scripts/lint-api-types.mjs']},
]
