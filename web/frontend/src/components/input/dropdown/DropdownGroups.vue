/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import {ListboxGroup, ListboxGroupLabel} from 'reka-ui'
import type {OptionGroup, SelectOption} from './groupOptions'

/**
 * A dropdown's entries under their headings, each group announced by its heading. The entry itself
 * is drawn by the default slot, since what marks a choice differs between one and several.
 */
defineProps<{
  groups: OptionGroup[]
}>()

defineSlots<{
  default(props: { option: SelectOption }): unknown
}>()
</script>

<template>
  <ListboxGroup v-for="(section, index) in groups" :key="section.group ?? index">
    <ListboxGroupLabel
        v-if="section.group"
        class="px-3 py-1.5 text-xs font-semibold uppercase tracking-wide text-ink-faint"
    >
      {{ section.group }}
    </ListboxGroupLabel>
    <template v-for="option in section.options" :key="option.value">
      <slot :option="option"/>
    </template>
  </ListboxGroup>
</template>
