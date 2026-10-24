/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import AppIcon from '@/components/display/AppIcon.vue'
import type {Section} from '~/util/navigation'

/**
 * The tab bar at the bottom of a phone: the main sections, each a 52 pixel target with its icon and name, clear of
 * the home indicator.
 */
const props = defineProps<{
  sections: readonly Section[]
  current?: string
  badges: Readonly<Partial<Record<Section['id'], number>>>
}>()

const {t} = useI18n()
const tabs = computed(() => props.sections.filter(section => section.tab))
</script>

<template>
  <nav
      :aria-label="t('nav.label')"
      class="pb-safe fixed inset-x-0 bottom-0 z-30 border-t border-line-soft bg-deep md:hidden"
  >
    <ul class="m-0 grid list-none px-1 pt-1.5 pb-2" :style="{gridTemplateColumns: `repeat(${tabs.length}, minmax(0, 1fr))`}">
      <li v-for="section in tabs" :key="section.id">
        <NuxtLink
            :to="section.path"
            :aria-current="current === section.id ? 'page' : undefined"
            :class="current === section.id ? 'text-accent' : 'text-ink-muted'"
            class="relative flex h-13 flex-col items-center justify-center gap-1 text-[0.6875rem] font-semibold no-underline"
        >
          <AppIcon :name="section.icon" :size="22"/>
          <span>{{ t(`nav.${section.id}`) }}</span>
          <span
              v-if="badges[section.id]"
              class="tabular absolute top-0.5 left-1/2 ml-2 min-w-4 bg-accent px-1 text-center text-[0.625rem] text-accent-contrast"
          >{{ badges[section.id] }}</span>
        </NuxtLink>
      </li>
    </ul>
  </nav>
</template>
