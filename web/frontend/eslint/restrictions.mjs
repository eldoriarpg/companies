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
