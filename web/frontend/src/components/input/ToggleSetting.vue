/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import {useId} from 'vue'
import SwitchKnob from './SwitchKnob.vue'

/**
 * One setting that is on or off: its label and an optional hint on the left, the switch on the right. The whole row
 * is the switch, 44 pixels high at the least, so a thumb does not have to find the small track.
 */
const model = defineModel<boolean>({required: true})

defineProps<{
  label: string
  hint?: string
  disabled?: boolean
}>()

const labelId = useId()
const hintId = useId()
</script>

<template>
  <button
      type="button"
      role="switch"
      :aria-checked="model"
      :aria-labelledby="labelId"
      :aria-describedby="hint ? hintId : undefined"
      :disabled="disabled"
      class="flex min-h-11 w-full items-center justify-between gap-4 py-1 text-left disabled:cursor-not-allowed disabled:opacity-50"
      @click="model = !model"
  >
    <span class="flex min-w-0 flex-col">
      <span :id="labelId" class="text-xs font-semibold text-ink">{{ label }}</span>
      <span v-if="hint" :id="hintId" class="text-xs text-ink-muted">{{ hint }}</span>
    </span>
    <SwitchKnob :on="model"/>
  </button>
</template>
