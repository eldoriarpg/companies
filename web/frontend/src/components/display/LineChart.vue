/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import {linePoints} from '~/util/chart'
import type {Trend} from '~/util/format'

/**
 * A line of values, green when it rose, red when it fell, grey when flat; as a sparkline in a row or as the chart of a
 * panel stretched to its width. With a label it is an image a screen reader names; without one it is decoration.
 */
const props = withDefaults(defineProps<{
  values: readonly number[]
  trend: Trend
  width?: number
  height?: number
  stretch?: boolean
  label?: string
}>(), {width: 80, height: 22, label: undefined})

const points = computed(() => linePoints(props.values, props.width, props.height))
const colour = computed(() => ({up: 'text-gain', down: 'text-loss', flat: 'text-ink-muted'})[props.trend])
</script>

<template>
  <svg
      :viewBox="`0 0 ${width} ${height}`"
      :width="stretch ? '100%' : width"
      :height="height"
      :preserveAspectRatio="stretch ? 'none' : undefined"
      :role="label ? 'img' : undefined"
      :aria-label="label"
      :aria-hidden="label ? undefined : 'true'"
      :class="colour"
      class="block shrink-0"
  >
    <polyline :points="points" fill="none" stroke="currentColor" stroke-width="2" vector-effect="non-scaling-stroke"/>
  </svg>
</template>
