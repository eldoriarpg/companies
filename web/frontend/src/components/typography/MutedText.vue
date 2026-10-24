/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
/** Secondary text in the muted colour, an inline span unless told to be a paragraph. */
withDefaults(defineProps<{
  tag?: 'span' | 'p'
}>(), {tag: 'span'})
</script>

<template>
  <component :is="tag" class="m-0 text-xs leading-relaxed text-ink-muted md:text-sm">
    <slot/>
  </component>
</template>
