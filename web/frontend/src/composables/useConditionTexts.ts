/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import type {ConditionTexts} from '~/util/orderDraft'

/**
 * Writes the conditions of criteria lines that need words, such as the enchantment count, in the reader's language.
 *
 * @returns the texts
 */
export function useConditionTexts(): ConditionTexts {
    const {t} = useI18n()
    return {
        count: count => t('builder.atLeastEnchantments', {count}),
        durability: percent => t('builder.atLeastDurability', {percent}),
    }
}
