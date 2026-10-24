/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
/**
 * A whole row of a list that opens its entry: full width, left aligned, at least 44 pixels high, marked while its
 * entry is open.
 */
defineProps<{
  selected?: boolean
}>()

defineEmits<{
  click: [event: MouseEvent]
}>()
</script>

<template>
  <button
      type="button"
      :aria-current="selected ? 'true' : undefined"
      :class="selected ? 'bg-selected' : 'bg-transparent hover:bg-selected'"
      class="flex min-h-11 w-full items-center gap-3 border-b border-line-soft px-3 py-2.5 text-left text-ink transition-colors last:border-b-0"
      @click="$emit('click', $event)"
  >
    <slot/>
  </button>
</template>
