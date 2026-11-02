/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
/**
 * A picture the player points into, such as a map: a click reports where it landed and the arrow keys reach it from
 * the keyboard. The label is what a screen reader says; a disabled picture is only shown.
 */
defineProps<{
  label: string
  disabled?: boolean
}>()

defineEmits<{
  click: [event: MouseEvent]
  keydown: [event: KeyboardEvent]
}>()
</script>

<template>
  <button
      type="button"
      :aria-label="label"
      :disabled="disabled"
      class="relative self-start border border-line bg-ground p-0 outline-none focus-visible:border-accent disabled:cursor-default"
      @click="$emit('click', $event)"
      @keydown="$emit('keydown', $event)"
  >
    <slot/>
  </button>
</template>
