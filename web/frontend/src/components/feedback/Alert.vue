/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import AppIcon from '@/components/display/AppIcon.vue'
import type {IconName} from '~/util/icons'

/**
 * A message across the page saying how something stands: green for something done, amber for a notice the reader
 * should act on, red for a problem. A problem is an `alert`, which a screen reader announces the moment it appears;
 * anything else is a `status`, announced once the reader is free.
 */
type Variant = 'success' | 'notice' | 'problem'

defineProps<{
  variant: Variant
}>()

const BOX_CLASSES: Record<Variant, string> = {
  success: 'border-gain/50 bg-surface text-ink',
  notice: 'border-notice-line bg-notice text-notice-ink',
  problem: 'border-problem-line bg-problem text-problem-ink',
}

const ICONS: Record<Variant, IconName> = {
  success: 'check',
  notice: 'warning',
  problem: 'warning',
}
</script>

<template>
  <div
      :role="variant === 'problem' ? 'alert' : 'status'"
      :class="BOX_CLASSES[variant]"
      class="flex items-start gap-2 border px-3 py-2.5 text-sm leading-snug"
  >
    <AppIcon :name="ICONS[variant]" :size="18" :class="variant === 'success' ? 'text-gain' : ''"/>
    <div class="flex min-w-0 flex-col gap-1">
      <slot/>
    </div>
  </div>
</template>
