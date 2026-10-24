/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
/**
 * A panel of the dashboard: the raised surface with a hairline border and square corners. A heading goes in the
 * `head` slot, which sits above a divider with the panel's search bar beside it where there is room.
 */
withDefaults(defineProps<{
  tag?: 'section' | 'div' | 'aside'
  padded?: boolean
}>(), {tag: 'section'})
</script>

<template>
  <component :is="tag" class="flex min-w-0 flex-col border border-line bg-surface">
    <div v-if="$slots.head" class="flex flex-wrap items-center justify-between gap-2 border-b border-line-soft px-3 py-2.5 md:px-4">
      <slot name="head"/>
    </div>
    <div :class="padded ? 'flex flex-col gap-3 p-3 md:p-4' : ''">
      <slot/>
    </div>
  </component>
</template>
