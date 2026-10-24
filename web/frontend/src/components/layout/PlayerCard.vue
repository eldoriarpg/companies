/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import LiveIndicator from './LiveIndicator.vue'

/** The player the session belongs to, with their company and role, and whether the dashboard is live. */
defineProps<{
  name: string
  role?: string
  company?: string
  live: boolean
}>()
</script>

<template>
  <div class="flex flex-col gap-1 border border-line bg-ground p-3">
    <span class="text-sm font-semibold">{{ name }}</span>
    <span v-if="company" class="text-xs text-ink-muted">{{ role ? `${role} · ${company}` : company }}</span>
    <LiveIndicator :live="live"/>
    <slot/>
  </div>
</template>
