/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
/** How far something is, as a flat bar a screen reader reads as a progress bar. */
withDefaults(defineProps<{
  value: number
  label: string
  tone?: 'progress' | 'accent'
}>(), {tone: 'progress'})
</script>

<template>
  <div
      role="progressbar"
      :aria-label="label"
      :aria-valuenow="value"
      aria-valuemin="0"
      aria-valuemax="100"
      class="h-1.5 w-full overflow-hidden bg-line-soft"
  >
    <div :class="tone === 'accent' ? 'bg-accent' : 'bg-progress'" class="h-full transition-[width] duration-500" :style="{width: `${Math.min(100, Math.max(0, value))}%`}"/>
  </div>
</template>
