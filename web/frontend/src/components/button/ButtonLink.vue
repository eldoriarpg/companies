/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {RouteLocationRaw} from 'vue-router'
import AppIcon from '@/components/display/AppIcon.vue'
import type {IconName} from '~/util/icons'

/**
 * A link to another page drawn as a button, for an action that opens a page of its own, such as placing a new order.
 * It stays a link, so it can be opened in a new tab and its address copied.
 */
defineProps<{
  to: RouteLocationRaw
  icon?: IconName
}>()
</script>

<template>
  <NuxtLink
      :to="to"
      class="inline-flex min-h-11 items-center justify-center gap-2 whitespace-nowrap border border-line bg-ground px-4 text-sm font-semibold text-ink no-underline transition-colors duration-150 hover:border-ink-faint"
  >
    <AppIcon v-if="icon" :name="icon" :size="18"/>
    <slot/>
  </NuxtLink>
</template>
