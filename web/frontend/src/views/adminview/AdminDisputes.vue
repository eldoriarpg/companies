/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {DisputeRow} from '~/api/gateway'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PanelBox from '@/components/container/PanelBox.vue'
import PlainList from '@/components/container/PlainList.vue'
import ActionNotice from '@/components/feedback/ActionNotice.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import NumberInput from '@/components/input/NumberInput.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import {count, dateTime, money} from '~/util/format'

/**
 * The disputes about jobs, as `/companyadmin job disputes` lists them: both sides, who opened the dispute and why, the
 * company's time and whether it had run out, and the work counted in an area. Settling gives the company a share of
 * the price, paid as a finished job, and the owner the rest; the fee is kept.
 */
const {t, locale} = useI18n()
const loading = useAction()
const settling = useAction()
const disputes = ref<DisputeRow[] | null>(null)
const shares = reactive<Record<number, number | undefined>>({})

onMounted(() => {
  void load()
})

async function load(): Promise<void> {
  const listed = await loading.run('admin.jobs.disputes', {})
  if (listed) disputes.value = listed.disputes
}

async function settle(dispute: DisputeRow): Promise<void> {
  const percent = shares[dispute.id]
  if (percent == null || percent < 0 || percent > 100) return
  const result = await settling.run('admin.jobs.settle', {job: dispute.id, percent})
  if (result) disputes.value = result.disputes
}
</script>

<template>
  <PanelBox :aria-label="t('admin.disputes.title')" data-testid="admin-disputes">
    <template #head>
      <SectionHeader>{{ t('admin.disputes.title') }}</SectionHeader>
    </template>
    <ActionNotice :problem="loading.problem.value ?? settling.problem.value"/>
    <EmptyState v-if="!disputes || disputes.length === 0" :loading="loading.busy.value">{{ t('admin.disputes.none') }}</EmptyState>
    <PlainList v-else>
      <li v-for="dispute in disputes" :key="dispute.id" class="flex flex-col gap-2 px-3 py-3 text-sm md:px-4" :data-testid="`dispute-${dispute.id}`">
        <span class="flex min-w-0 items-center gap-1.5">
          <span class="truncate font-semibold">#{{ dispute.id }} {{ dispute.title }}</span>
          <PrimaryBadge>{{ t(`jobs.kind.${dispute.kind}`) }}</PrimaryBadge>
          <ErrorBadge v-if="dispute.overdue">{{ t('admin.disputes.overdue') }}</ErrorBadge>
          <span class="ml-auto shrink-0 tabular">{{ money(dispute.price, locale) }}</span>
        </span>
        <span>{{ t('admin.disputes.sides', {owner: dispute.owner, company: dispute.company ?? t('admin.disputes.gone')}) }}</span>
        <span>{{ t(dispute.byOwner ? 'admin.disputes.byOwner' : 'admin.disputes.byCompany', {name: dispute.openedBy ?? '?', reason: dispute.reason ?? ''}) }}</span>
        <MutedText v-if="dispute.description" tag="p">{{ dispute.description }}</MutedText>
        <MutedText v-if="dispute.due" tag="p">{{ t('admin.disputes.due', {time: dateTime(dispute.due, locale)}) }}</MutedText>
        <MutedText v-if="dispute.world" tag="p">{{ t('admin.disputes.work', {center: dispute.center, world: dispute.world, blocks: count(dispute.work, locale)}) }}</MutedText>
        <div class="flex flex-wrap items-end gap-2">
          <NumberInput v-model="shares[dispute.id]" :label="t('admin.disputes.share')" :min="0" :max="100" :data-testid="`dispute-share-${dispute.id}`"/>
          <SecondaryButton :disabled="settling.busy.value || shares[dispute.id] == null" :data-testid="`dispute-settle-${dispute.id}`" @click="settle(dispute)">{{ t('admin.disputes.settle') }}</SecondaryButton>
        </div>
      </li>
    </PlainList>
  </PanelBox>
</template>
