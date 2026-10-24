/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import {useNow} from '@vueuse/core'
import PanelBox from '@/components/container/PanelBox.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SearchInput from '@/components/input/SearchInput.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import {count, money, when} from '~/util/format'
import type {ActivityEntry} from '~/util/snapshot/activity'

/**
 * The live feed of the company: deliveries with the member who made them, accepted and finished orders, levels,
 * members coming and going, money moving. It fills while the page is open; new lines come in on top without moving
 * what is read.
 */
const props = defineProps<{
  entries: readonly ActivityEntry[]
}>()

const {t, locale} = useI18n()
const now = useNow({interval: 60_000})

/**
 * What a line says after the member's name, in the reader's language.
 *
 * @param entry the line
 * @returns the text
 */
function textOf(entry: ActivityEntry): string {
  const amount = entry.amount === undefined ? '' : entry.kind === 'deposit' || entry.kind === 'withdrawal' ? money(entry.amount, locale.value) : count(entry.amount, locale.value)
  const values = {amount, item: entry.item ?? t('activity.items'), order: entry.order ?? '', level: entry.level ?? '', company: entry.company ?? ''}
  if (entry.kind === 'delivered' && entry.order === undefined) return t('activity.deliveredSomewhere', values)
  if (entry.kind === 'delivered' && entry.actor === undefined) return t('activity.deliveredTo', values)
  return t(`activity.${entry.kind}`, values)
}

const {query, found} = useListSearch(toRef(props, 'entries'), entry => [entry.actor, textOf(entry)])
</script>

<template>
  <PanelBox :aria-label="t('portfolio.activity')">
    <template #head>
      <SectionHeader>{{ t('portfolio.activity') }}</SectionHeader>
      <div class="w-full">
        <SearchInput v-model="query" :label="t('portfolio.searchActivity')"/>
      </div>
    </template>
    <EmptyState v-if="found.length === 0">{{ entries.length === 0 ? t('portfolio.noActivity') : t('common.noMatch') }}</EmptyState>
    <ol v-else class="m-0 list-none px-3 py-1 md:px-4" aria-live="polite" data-testid="activity">
      <li v-for="entry in found" :key="entry.id" class="flex items-baseline gap-2.5 border-b border-line-soft py-2.5 text-sm leading-snug last:border-b-0">
        <span class="tabular shrink-0 text-[0.6875rem] text-ink-muted">{{ when(entry.time, now, locale) }}</span>
        <span>
          <span v-if="entry.actor" class="font-semibold text-accent">{{ entry.actor }}</span>
          {{ textOf(entry) }}
        </span>
      </li>
    </ol>
  </PanelBox>
</template>
