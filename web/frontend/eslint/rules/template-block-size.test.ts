/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
import rule from './template-block-size.mjs'
import {vueRuleTester} from './rule-tester'

/**
 * A block is measured from its opening tag to its closing tag, and only below the page's own level.
 *
 * @vitest-environment node
 */
const lines = (count: number) => '<p>line</p>\n'.repeat(count)

vueRuleTester().run('template-block-size', rule, {
    valid: [
        {code: `<template><div><section>\n${lines(10)}</section></div></template>`},
        {code: `<template><div>\n${lines(80)}</div></template>`},
        {code: `<template><ViewContent><Modal>\n${lines(80)}</Modal></ViewContent></template>`},
        {code: `<template><div><section>\n${lines(80)}</section></div></template>`, options: [{max: 100}]},
    ],
    invalid: [
        {
            code: `<template><div><section>\n${lines(60)}</section></div></template>`,
            errors: [{messageId: 'tooLong', data: {tag: 'section', span: 62, max: 50}}],
        },
        {
            code: `<template><div><div><ul>\n${lines(12)}</ul></div></div></template>`,
            options: [{max: 10}],
            errors: [{messageId: 'tooLong', data: {tag: 'div', span: 14, max: 10}}, {messageId: 'tooLong', data: {tag: 'ul', span: 14, max: 10}}],
        },
    ],
})
