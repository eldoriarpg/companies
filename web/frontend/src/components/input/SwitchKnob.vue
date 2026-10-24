/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
/** The drawn part of a switch: an accent track with the knob to the right while on, a plain one while off. */
defineProps<{
  on: boolean
}>()
</script>

<template>
  <span :class="on ? 'border-accent bg-accent' : 'border-line bg-surface'" class="relative h-4 w-7 shrink-0 border" aria-hidden="true">
    <span :class="on ? 'left-3.5 bg-accent-contrast' : 'left-0.5 bg-ink-muted'" class="absolute top-0.5 size-2.5 transition-all"/>
  </span>
</template>
