/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {ItemView} from '~/api/generated/protocol'
import {initials, swatchOf} from '~/util/text'

/**
 * The square standing for an item: two letters of its name on a colour of its material, the same everywhere. Item
 * sprites come later; until then every item is drawn this way. The name beside it is what a screen reader reads.
 */
withDefaults(defineProps<{
  item: Pick<ItemView, 'material' | 'name'>
  size?: 'small' | 'large'
}>(), {size: 'large'})
</script>

<template>
  <span
      aria-hidden="true"
      :class="size === 'small' ? 'size-6 text-[0.55rem]' : 'size-8 text-[0.625rem]'"
      class="tabular flex shrink-0 items-center justify-center text-white"
      :style="{background: swatchOf(item.material)}"
  >{{ initials(item.name) }}</span>
</template>
