/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import AppIcon from '@/components/display/AppIcon.vue'
import SwitchKnob from './SwitchKnob.vue'
import type {IconName} from '~/util/icons'

/** A setting that is on or off, read by a screen reader as a switch, with its label beside it. */
const model = defineModel<boolean>({required: true})

defineProps<{
  label: string
  icon?: IconName
  disabled?: boolean
}>()
</script>

<template>
  <button
      type="button"
      role="switch"
      :aria-checked="model"
      :disabled="disabled"
      class="inline-flex min-h-11 items-center gap-2 border border-line bg-ground px-3 text-xs font-semibold text-ink transition-colors hover:border-ink-faint disabled:cursor-not-allowed disabled:opacity-50"
      @click="model = !model"
  >
    <AppIcon v-if="icon" :name="icon" :size="16"/>
    <span>{{ label }}</span>
    <SwitchKnob :on="model"/>
  </button>
</template>
