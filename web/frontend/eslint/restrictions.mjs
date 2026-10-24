/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
/**
 * Shapes of code the frontend refuses, as `no-restricted-syntax` selectors with the message each one
 * reports. The config switches them on per area; the tests beside this file hold them to what they
 * must catch and what they must leave alone.
 */

/** A state type written inline, which wants a name of its own. */
export const INLINE_STATE_TYPE = {
    selector: "CallExpression[callee.name='ref']:matches([typeArguments.params.0.type='TSTypeLiteral'], [typeArguments.params.0.types.0.type='TSTypeLiteral'])",
    message: 'Inline object type in ref<>: give the type a name.',
}

/**
 * The same syntax restriction for the script and for the template of a component.
 *
 * @param restrictions the selectors with their messages
 * @returns the rules entry for both
 */
export function restrictSyntax(...restrictions) {
    return {
        'no-restricted-syntax': ['error', ...restrictions],
        'vue/no-restricted-syntax': ['error', ...restrictions],
    }
}

/**
 * A failure worded by hand. `describeFailure` in `util/failure.ts` says what failed and what to do about it, and the
 * failure texts are only ever reached through it.
 */
export const GENERIC_FAILURE = {
    selector: "CallExpression:matches([callee.name=/^\\$?t$/], [callee.property.name='t'])[arguments.0.value=/^failure\\./]",
    message: 'A failure worded by hand. Describe it: describeFailure(e, t) or describeKind(kind, t) from util/failure.ts.',
}

/**
 * A date formatted for the locale in place. `toLocaleString` also formats numbers, which pass the locale alone, while
 * a date always passes options after it.
 */
export const INLINE_DATE_FORMAT = {
    selector: "CallExpression:matches([callee.property.name=/^toLocale(Date|Time)String$/], [callee.property.name='toLocaleString'][arguments.length>1])",
    message: 'Inline toLocale date formatting: use the helpers in util/format.ts.',
}
