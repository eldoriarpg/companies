/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
import {config} from '@vue/test-utils'
import {createI18n} from 'vue-i18n'
import en from '@/i18n/en'

/**
 * Everything every mounted component needs and no test should have to repeat.
 *
 * <p>The i18n plugin is real rather than stubbed, in English, the language every other falls back
 * to, so a test asserting a label asserts the label a reader sees. The router behind every link is
 * stubbed, so a mount does not drag in the pages.
 */
const i18n = createI18n({
    legacy: false,
    locale: 'en',
    messages: {en, de: {}, fr: {}},
})

config.global.plugins = [i18n]

config.global.stubs = {
    NuxtLink: {
        template: '<a :href="to"><slot /></a>',
        props: ['to'],
    },
}
