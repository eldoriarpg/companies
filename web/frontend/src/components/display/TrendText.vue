/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {Trend} from '~/util/format'

/** A change in the number face, green for a gain, red for a loss, grey for none; its text carries the arrow. */
defineProps<{
  trend: Trend
}>()
</script>

<template>
  <span :class="{up: 'text-gain', down: 'text-loss', flat: 'text-ink-muted'}[trend]" class="tabular whitespace-nowrap">
    <slot/>
  </span>
</template>
