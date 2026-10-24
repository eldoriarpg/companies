/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import AppIcon from '@/components/display/AppIcon.vue'
import type {IconName} from '~/util/icons'

/**
 * The shape every labelled button shares: square, at least 44 pixels high for a thumb, a label that never wraps and
 * the disabled state. The variants put their own fill on top of it; a `type` of submit sends its form.
 */
defineProps<{
  fill: string
  type?: 'button' | 'submit'
  disabled?: boolean
  fullWidth?: boolean
  icon?: IconName
}>()

defineEmits<{
  click: [event: MouseEvent]
}>()
</script>

<template>
  <button
      :type="type ?? 'button'"
      :disabled="disabled"
      :class="[fill, fullWidth ? 'w-full' : '']"
      class="inline-flex min-h-11 items-center justify-center gap-2 whitespace-nowrap border px-4 text-sm font-semibold transition-colors duration-150 disabled:cursor-not-allowed disabled:opacity-50"
      @click="$emit('click', $event)"
  >
    <AppIcon v-if="icon" :name="icon" :size="18"/>
    <slot/>
  </button>
</template>
