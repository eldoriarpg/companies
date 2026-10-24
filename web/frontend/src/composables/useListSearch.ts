/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import type {Ref} from 'vue'
import {matches} from '~/util/text'

/**
 * The search bar of a list the snapshot holds whole: the list narrowed to the entries matching every word typed.
 *
 * @param list the list
 * @param texts the texts of an entry that are searched
 * @returns the search text and the matching entries
 */
export function useListSearch<T>(list: Ref<readonly T[]>, texts: (entry: T) => (string | undefined)[]) {
    const query = ref('')
    return {
        query,
        found: computed(() => list.value.filter(entry => matches(query.value, ...texts(entry)))),
    }
}
