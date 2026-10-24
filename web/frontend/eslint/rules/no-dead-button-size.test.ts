/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import rule from './no-dead-button-size.mjs'
import {vueRuleTester} from './rule-tester'

/**
 * A plain `size` on a button that declares no such prop is reported; a bound one, a button that
 * declares it, and anything that is not a button are left alone.
 *
 * @vitest-environment node
 */
const options = [{sizeAware: ['ChipButton']}]
const template = (inner: string) => `<template><div>${inner}</div></template>`

vueRuleTester().run('no-dead-button-size', rule, {
    valid: [
        {code: template('<ChipButton size="sm">x</ChipButton>'), options},
        {code: template('<SecondaryButton compact>x</SecondaryButton>'), options},
        {code: template('<SecondaryButton :size="size">x</SecondaryButton>'), options},
        {code: template('<UserAvatar size="sm"/>'), options},
        {code: template('<ButtonRow size="sm"/>'), options},
    ],
    invalid: [
        {
            code: template('<SecondaryButton v-if="a > 0" size="sm">x</SecondaryButton>'),
            options,
            errors: [{messageId: 'dead', data: {tag: 'SecondaryButton'}}],
        },
    ],
})
