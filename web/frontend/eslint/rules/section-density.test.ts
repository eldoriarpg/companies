/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
import rule from './section-density.mjs'
import {vueRuleTester} from './rule-tester'

/**
 * Sections stacked directly below one element count on the second and third level only, and a
 * section with no body of its own does not count.
 *
 * @vitest-environment node
 */
const sections = (tag: string, count: number) => `<${tag}>x</${tag}>`.repeat(count)

vueRuleTester().run('section-density', rule, {
    valid: [
        {code: `<template><div>${sections('div', 5)}</div></template>`},
        {code: `<template><div>${'<div/>'.repeat(8)}</div></template>`},
        {code: `<template><div><div><div>${sections('div', 8)}</div></div></div></template>`},
        {code: `<template><div>${sections('p', 8)}</div></template>`},
        {code: `<template><div>${sections('div', 7)}</div></template>`, options: [{max: 8}]},
    ],
    invalid: [
        {
            code: `<template><div>${sections('div', 3)}${sections('PanelBox', 3)}</div></template>`,
            errors: [{messageId: 'dense', data: {tag: 'div', depth: 2, count: 6}}],
        },
        {
            code: `<template><main><section>${sections('article', 6)}</section></main></template>`,
            errors: [{messageId: 'dense', data: {tag: 'section', depth: 3, count: 6}}],
        },
    ],
})
