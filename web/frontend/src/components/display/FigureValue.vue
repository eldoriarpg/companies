/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import CapsLabel from '@/components/typography/CapsLabel.vue'

/** One figure under its upper case label, such as the level or the payout of an order. */
withDefaults(defineProps<{
  label: string
  size?: 'normal' | 'large'
}>(), {size: 'normal'})
</script>

<template>
  <div class="flex min-w-0 flex-col gap-0.5">
    <CapsLabel>{{ label }}</CapsLabel>
    <span :class="size === 'large' ? 'text-xl font-semibold' : 'text-base'" class="tabular truncate text-ink">
      <slot/>
    </span>
    <slot name="note"/>
  </div>
</template>
