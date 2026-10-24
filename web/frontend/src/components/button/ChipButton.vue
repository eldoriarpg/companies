/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
/** A filter that is on or off, amber while on; a screen reader hears it as a pressed button. */
defineProps<{
  pressed: boolean
}>()

defineEmits<{
  click: [event: MouseEvent]
}>()
</script>

<template>
  <button
      type="button"
      :aria-pressed="pressed"
      :class="pressed ? 'border-accent bg-accent text-accent-contrast' : 'border-line bg-surface text-ink hover:border-ink-faint'"
      class="inline-flex min-h-11 shrink-0 items-center whitespace-nowrap border px-3 text-xs font-semibold transition-colors"
      @click="$emit('click', $event)"
  >
    <slot/>
  </button>
</template>
