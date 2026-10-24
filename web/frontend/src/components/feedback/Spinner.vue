/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
/**
 * Something is on its way: a square turning in the accent colour. It is drawn only; the text beside it, or the
 * `aria-busy` of the region it sits in, is what a screen reader hears.
 */
withDefaults(defineProps<{
  size?: 'sm' | 'md' | 'lg'
}>(), {size: 'md'})

const SIZES = {sm: 'size-3', md: 'size-4', lg: 'size-7'} as const
</script>

<template>
  <span :class="SIZES[size]" class="inline-block shrink-0 animate-spin border-2 border-accent border-t-transparent motion-reduce:animate-none" aria-hidden="true"/>
</template>
