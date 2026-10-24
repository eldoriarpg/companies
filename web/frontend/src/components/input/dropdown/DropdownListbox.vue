/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import {useTemplateRef} from 'vue'
import {ListboxContent, ListboxRoot, type AcceptableValue} from 'reka-ui'

/**
 * The list inside a dropdown, as a screen reader and a keyboard meet it.
 *
 * <p>It is a listbox of options that says which are chosen. Up and down walk it and come round at
 * either end, Home and End jump to the ends, Enter and Space take the highlighted entry, and typing
 * jumps to an entry by its first letters. Where a search sits in the `head` slot the focus stays in
 * the search and the highlight moves under it, so the reader types and walks without switching.
 *
 * <p>A single list takes one value and replaces it; a `multiple` one adds and takes away. An entry
 * that is an action rather than a value handles its own `select` and prevents the default.
 */
const model = defineModel<AcceptableValue | AcceptableValue[]>()

defineProps<{
  multiple?: boolean
  /** What the list is, for a screen reader, where nothing visible names it. */
  label?: string
}>()

const root = useTemplateRef('root')

/** Which end a key walks off, and so which end it comes round to. */
const WALKS_OFF: Record<string, 'first' | 'last'> = {ArrowDown: 'last', ArrowUp: 'first'}

function comeRoundAtTheEnds(event: KeyboardEvent) {
  const end = WALKS_OFF[event.key]
  const list = root.value
  if (!end || !list) return
  const items = list.getItems().filter(item => item.ref.dataset.disabled !== '')
  const [leaving, arriving] = end === 'last' ? [items.at(-1), items[0]] : [items[0], items.at(-1)]
  if (!leaving || !arriving || list.highlightedElement !== leaving.ref) return
  event.preventDefault()
  event.stopPropagation()
  list.highlightItem(arriving.value)
}

defineExpose({
  /** Puts the highlight back on the first entry, for a list whose entries were just replaced. */
  highlightFirst: () => root.value?.highlightFirstItem(),
})
</script>

<template>
  <ListboxRoot
      ref="root"
      v-model="model"
      :multiple="multiple"
      :selection-behavior="multiple ? 'toggle' : 'replace'"
      highlight-on-hover
      class="flex min-h-0 flex-1 flex-col"
      @keydown.capture="comeRoundAtTheEnds"
  >
    <slot name="head"/>
    <ListboxContent :aria-label="label" class="min-h-0 flex-1 overflow-y-auto py-1 outline-none">
      <slot/>
    </ListboxContent>
  </ListboxRoot>
</template>
