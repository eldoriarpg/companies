/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
import noPluralMessages from './no-plural-messages.mjs'
import unusedKeys from './unused-keys.mjs'

/**
 * The rules for the locale files, which run on the JSON AST the locale parser gives them.
 */
export const i18nRules = {
    'i18n-no-plural-messages': noPluralMessages,
    'i18n-unused-keys': unusedKeys,
}
