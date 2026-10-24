/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
import rule from './button-row.mjs'
import {vueRuleTester} from './rule-tester'

/**
 * A hand written flex row is reported once it holds two labelled buttons of its own and says
 * nothing about a narrow width.
 *
 * @vitest-environment node
 */
const row = (classes: string, inner: string) => `<template><div class="${classes}">${inner}</div></template>`
const save = '<PrimaryButton @click="save">{{ t(\'common.save\') }}</PrimaryButton>'
const cancel = '<SecondaryButton @click="cancel">Cancel</SecondaryButton>'

vueRuleTester().run('button-row', rule, {
    valid: [
        {code: row('flex gap-2', save)},
        {code: row('flex-1', save + cancel)},
        {code: row('flex sm:flex-row', save + cancel)},
        {code: row('flex flex-col', save + cancel)},
        {code: row('flex', `<ButtonRow>${save}${cancel}</ButtonRow>`)},
        {code: row('flex', `<div>${save}</div><div>${cancel}</div>`)},
        {code: row('flex', `${save}<PrimaryButton v-else>b</PrimaryButton>`)},
        {code: row('flex', `<PrimaryButton v-for="x in xs" :key="x">{{ x }}</PrimaryButton>${cancel}`)},
        {code: row('flex', `${save}<PrimaryButton :icon="['fas', 'x']"/>`)},
        {code: row('flex', `${save}<PrimaryButton><span class="sr-only">Gone</span></PrimaryButton>`)},
        {code: row('flex', `${save}<IconButton label="x">x</IconButton>`)},
    ],
    invalid: [
        {code: row('flex gap-2', save + cancel), errors: [{messageId: 'handWritten', data: {count: 2}}]},
        {
            code: row('flex', `<span>${save}</span>${cancel}<PrimaryButton :disabled="a >= b"><AppIcon/> {{ x }}</PrimaryButton>`),
            errors: [{messageId: 'handWritten', data: {count: 3}}],
        },
    ],
})
