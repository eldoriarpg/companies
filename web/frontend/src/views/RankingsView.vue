/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import PanelBox from '@/components/container/PanelBox.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import NoticeBanner from '@/components/feedback/NoticeBanner.vue'
import SelectInput from '@/components/input/SelectInput.vue'
import ViewContent from '@/components/layout/ViewContent.vue'
import PageSwitcher from '@/components/navigation/PageSwitcher.vue'
import {RANGES, RANKINGS} from '~/composables/useRankings'
import {count} from '~/util/format'
import {openLabel} from '~/util/labels'

/**
 * The rankings of the companies: which ranking over which time, a page at a time, with the player's company marked
 * and its own place below the page when it is elsewhere.
 */
const {t, te, locale} = useI18n()
const rankings = useRankings()
const {company} = useDashboard()

const rankingChoices = computed(() => RANKINGS.map(value => ({value, label: openLabel({t, te}, `rankings.kind.${value}`, value)})))
const rangeChoices = computed(() => RANGES.map(value => ({value, label: openLabel({t, te}, `rankings.range.${value}`, value)})))
const own = computed(() => {
  const page = rankings.shown.value
  return page?.own && !page.entries.some(entry => entry.company === page.own?.company) ? page.own : null
})
</script>

<template>
  <ViewContent :title="t('nav.rankings')">
    <div class="flex flex-wrap gap-3">
      <SelectInput v-model="rankings.ranking.value" :label="t('rankings.ranking')" :options="rankingChoices"/>
      <SelectInput v-model="rankings.range.value" :label="t('rankings.range.label')" :options="rangeChoices"/>
    </div>
    <NoticeBanner v-if="rankings.status.value === 'failed'" tone="problem">{{ t('rankings.failed') }}</NoticeBanner>
    <PanelBox>
      <EmptyState v-if="!rankings.shown.value?.entries.length">{{ t('rankings.empty') }}</EmptyState>
      <ol v-else class="m-0 list-none p-0" data-testid="rankings">
        <li
            v-for="entry in [...rankings.shown.value.entries, ...(own ? [own] : [])]"
            :key="entry.company"
            :aria-current="entry.company === company?.id ? 'true' : undefined"
            :class="entry.company === company?.id ? 'bg-selected' : ''"
            class="flex items-center gap-3 border-b border-line-soft px-3 py-2.5 last:border-b-0 md:px-4"
        >
          <span class="tabular w-10 shrink-0 text-sm text-ink-muted">#{{ entry.place }}</span>
          <span :class="entry.company === company?.id ? 'text-accent' : ''" class="min-w-0 grow truncate text-sm font-semibold">{{ entry.name }}</span>
          <span class="tabular text-sm">{{ count(entry.value, locale) }}</span>
        </li>
      </ol>
    </PanelBox>
    <PageSwitcher v-model:page="rankings.page.value" :pages="rankings.shown.value?.pages ?? 1"/>
  </ViewContent>
</template>
