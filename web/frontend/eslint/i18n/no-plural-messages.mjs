/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
import {createParser} from '@intlify/message-compiler'
import * as jsonc from 'jsonc-eslint-parser'

/** The node type vue-i18n's message compiler gives a message it reads as plural choices. */
const PLURAL = 1

/**
 * Refuses a message vue-i18n reads as plural choices.
 *
 * <p>A bare `|` splits a message into choices, and `t()` then shows the first of them unless a
 * count is passed, so a pipe written as text silently cuts the sentence short. Nothing here passes
 * counts to pick a plural form, so every such message is a pipe that wanted escaping as `{'|'}`.
 * The message is read by the compiler vue-i18n itself uses, not by pattern.
 */
export default {
    meta: {
        type: 'problem',
        docs: {description: 'disallow messages vue-i18n splits into plural choices'},
        schema: [],
        messages: {
            plural: "This message has a bare '|', which vue-i18n reads as plural choices. Escape it as {'|'}.",
        },
    },
    create(context) {
        if (!context.sourceCode.parserServices?.isJSON) return {}
        const parser = createParser({onError() {}})
        return {
            JSONProperty(property) {
                const value = property.value
                if (value.type === 'JSONObjectExpression' || value.type === 'JSONArrayExpression') return
                const message = jsonc.getStaticJSONValue(value)
                if (typeof message !== 'string') return
                if (parser.parse(message).body.type === PLURAL) context.report({node: value, messageId: 'plural'})
            },
        }
    },
}
