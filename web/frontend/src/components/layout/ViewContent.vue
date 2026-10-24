/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import PageHeader from '@/components/typography/PageHeader.vue'

/**
 * The body of a page: its heading and its content. The title also names the browser tab, so a page is titled in one
 * place. On a phone the bar at the top shows the section, so the heading is there for screen readers only, unless the
 * page stands without that bar.
 */
const props = defineProps<{
  title: string
  standalone?: boolean
}>()

useHead(() => ({title: props.title}))
</script>

<template>
  <div class="flex w-full min-w-0 flex-col gap-4 md:gap-5">
    <PageHeader :class="standalone ? '' : 'max-md:sr-only'">{{ title }}</PageHeader>
    <slot/>
  </div>
</template>
