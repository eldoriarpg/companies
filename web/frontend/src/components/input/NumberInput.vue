/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import NumericField from './NumericField.vue'

/** A whole number typed by the player, such as a count or a percent; a phone opens its number keyboard. */
const model = defineModel<number | undefined>()

defineProps<{
  label: string
  hint?: string
  min?: number
  max?: number
  placeholder?: string
  disabled?: boolean
}>()

/**
 * Reads a whole number.
 *
 * @param text what was typed
 * @returns the number, or NaN when it is no whole number
 */
function whole(text: string): number {
  const plain = text.trim()
  return /^\d+$/.test(plain) ? Number(plain) : Number.NaN
}
</script>

<template>
  <NumericField
      v-model="model"
      :label="label"
      :hint="hint"
      :min="min"
      :max="max"
      :placeholder="placeholder"
      :disabled="disabled"
      :parse="whole"
      inputmode="numeric"
  />
</template>
