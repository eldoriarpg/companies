/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {ServerBranding} from '~/api/gateway'
import AppIcon from '@/components/display/AppIcon.vue'
import ServerMark from '@/components/display/ServerMark.vue'
import type {Section} from '~/util/navigation'

/**
 * The navigation from tablet width up: the server's mark and name on top, the sections with their counts, and the
 * player's card at the bottom.
 */
defineProps<{
  server: ServerBranding
  sections: readonly Section[]
  current?: string
  badges: Readonly<Partial<Record<Section['id'], number>>>
}>()

const {t} = useI18n()
</script>

<template>
  <aside class="sticky top-0 hidden h-dvh w-56 shrink-0 flex-col gap-5 border-r border-line-soft bg-deep px-3 py-5 md:flex lg:w-60">
    <div class="flex items-center gap-3 px-1">
      <ServerMark :server="server"/>
      <span class="flex min-w-0 flex-col">
        <span class="truncate font-bold">{{ server.name }}</span>
        <span class="tabular text-[0.625rem] uppercase tracking-widest text-ink-muted">{{ t('app.name') }}</span>
      </span>
    </div>
    <nav :aria-label="t('nav.label')" class="flex flex-col gap-0.5">
      <NuxtLink
          v-for="section in sections"
          :key="section.id"
          :to="section.path"
          :aria-current="current === section.id ? 'page' : undefined"
          :class="current === section.id ? 'bg-accent text-accent-contrast' : 'text-ink-soft hover:bg-surface'"
          class="flex min-h-11 items-center gap-3 px-3 text-sm font-semibold no-underline"
      >
        <AppIcon :name="section.icon" :size="18"/>
        <span class="grow">{{ t(`nav.${section.id}`) }}</span>
        <span v-if="badges[section.id]" class="tabular text-xs">{{ badges[section.id] }}</span>
      </NuxtLink>
    </nav>
    <div class="mt-auto">
      <slot/>
    </div>
  </aside>
</template>
