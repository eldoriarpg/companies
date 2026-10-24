/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
/**
 * One tab of a tab list: 44 pixels high, underlined in the accent colour while selected. The list around it owns the
 * keyboard and passes the roving tabindex.
 */
defineProps<{
  selected: boolean
}>()

defineEmits<{
  click: [event: MouseEvent]
  keydown: [event: KeyboardEvent]
}>()
</script>

<template>
  <button
      type="button"
      role="tab"
      :aria-selected="selected"
      :class="selected ? 'border-accent text-ink' : 'border-transparent text-ink-muted hover:text-ink'"
      class="min-h-11 shrink-0 whitespace-nowrap border-b-2 px-4 text-sm font-semibold transition-colors"
      @click="$emit('click', $event)"
      @keydown="$emit('keydown', $event)"
  >
    <slot/>
  </button>
</template>
