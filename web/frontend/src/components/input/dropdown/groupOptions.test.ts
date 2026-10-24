/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {describe, expect, it} from 'vitest'
import {filterOptions, groupOptions} from './groupOptions'

/**
 * Entries sort under their headings in the order the headings first appear, and a list without headings stays one.
 *
 * @vitest-environment node
 */
describe('groupOptions', () => {
    it('keeps a list without headings as one group', () => {
        const options = [{value: 'a', label: 'A'}, {value: 'b', label: 'B'}]
        expect(groupOptions(options)).toEqual([{options}])
    })

    it('puts each heading where its first entry stood', () => {
        const groups = groupOptions([
            {value: 'stone', label: 'Stone', group: 'Materials'},
            {value: 'saved:1', label: 'Blade', group: 'Saved'},
            {value: 'dirt', label: 'Dirt', group: 'Materials'},
        ])
        expect(groups.map(group => group.group)).toEqual(['Materials', 'Saved'])
        expect(groups[0]?.options.map(option => option.value)).toEqual(['stone', 'dirt'])
    })

    it('filters by label, ignoring case', () => {
        expect(filterOptions([{label: 'Oak Log'}, {label: 'Stone'}], ' oak ')).toEqual([{label: 'Oak Log'}])
    })
})
