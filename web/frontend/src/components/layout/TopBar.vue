/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {ServerBranding} from '~/api/gateway'
import IconButton from '@/components/button/IconButton.vue'
import ServerMark from '@/components/display/ServerMark.vue'
import LiveIndicator from './LiveIndicator.vue'

/** The bar at the top of a phone: the server's mark and name over the section's title, the live state and the menu. */
defineProps<{
  server: ServerBranding
  title: string
  live: boolean
}>()

defineEmits<{
  menu: []
}>()

const {t} = useI18n()
</script>

<template>
  <header class="pt-safe flex items-center justify-between gap-3 px-4 pb-2.5 md:hidden">
    <div class="flex min-w-0 items-center gap-2.5 pt-3">
      <ServerMark :server="server"/>
      <span class="flex min-w-0 flex-col">
        <span class="truncate text-[0.6875rem] text-ink-muted">{{ server.name }}</span>
        <span class="truncate text-lg font-bold" aria-hidden="true">{{ title }}</span>
      </span>
    </div>
    <div class="flex items-center gap-3 pt-3">
      <LiveIndicator :live="live"/>
      <IconButton icon="menu" :label="t('nav.menu')" @click="$emit('menu')"/>
    </div>
  </header>
</template>
