/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import {ICONS, type IconName} from '~/util/icons'

/** One icon of the dashboard, stroked in the current text colour and hidden from screen readers. */
withDefaults(defineProps<{
  name: IconName
  size?: number
}>(), {size: 20})
</script>

<template>
  <svg
      :width="size"
      :height="size"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.8"
      stroke-linecap="square"
      stroke-linejoin="miter"
      aria-hidden="true"
      focusable="false"
      class="shrink-0"
  >
    <path :d="ICONS[name]"/>
  </svg>
</template>
