/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {ServerBranding} from '~/api/gateway'
import ServerMark from '@/components/display/ServerMark.vue'

/** The server a waiting session belongs to, large on the login: its icon, its name and the dashboard's name. */
defineProps<{
  server: ServerBranding
}>()

const {t} = useI18n()
</script>

<template>
  <div class="order-first flex items-center gap-3">
    <ServerMark :server="server" size="large"/>
    <span class="flex min-w-0 flex-col gap-0.5">
      <span class="truncate text-lg font-bold">{{ server.name }}</span>
      <span class="tabular text-[0.6875rem] uppercase tracking-widest text-ink-muted">{{ t('app.name') }}</span>
    </span>
  </div>
</template>
