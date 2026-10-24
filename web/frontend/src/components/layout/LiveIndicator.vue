/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
/** Whether the dashboard is live: a green square and LIVE while the stream stands, a grey one while it reconnects. */
defineProps<{
  live: boolean
}>()

const {t} = useI18n()
</script>

<template>
  <span
      role="status"
      :class="live ? 'text-gain' : 'text-ink-muted'"
      class="tabular flex items-center gap-1.5 text-[0.6875rem] font-semibold uppercase"
  >
    <span :class="live ? 'bg-gain' : 'bg-ink-muted'" class="size-2" aria-hidden="true"/>
    {{ live ? t('live.on') : t('live.off') }}
  </span>
</template>
