/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import {useMediaQuery} from '@vueuse/core'
import type {JobDetails, JobRow} from '~/api/gateway'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import RowButton from '@/components/button/RowButton.vue'
import PanelBox from '@/components/container/PanelBox.vue'
import PlainList from '@/components/container/PlainList.vue'
import Alert from '@/components/feedback/Alert.vue'
import BottomSheet from '@/components/feedback/BottomSheet.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import ViewContent from '@/components/layout/ViewContent.vue'
import MutedText from '@/components/typography/MutedText.vue'
import RowTitle from '@/components/typography/RowTitle.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import ContractForm from './jobsview/ContractForm.vue'
import JobDetail from './jobsview/JobDetail.vue'
import {orderIdOf} from '~/composables/useSelection'
import {money} from '~/util/format'

/**
 * The jobs of the server, as `/company job` and `/order job` list them: the jobs the player's company holds, the open
 * jobs companies may take, and the player's own jobs, with a form to post a contract. A job's page opens beside the
 * lists from tablet width and as a sheet on a phone; the address names it.
 */
const {t, locale} = useI18n()
const jobs = useJobs()
const selection = useSelection('job', orderIdOf)
const wide = useMediaQuery('(min-width: 1024px)')
const posting = ref(false)

onMounted(() => {
  void jobs.load()
})
watch(selection.id, id => void jobs.open(id), {immediate: true})

const sheetOpen = computed({
  get: () => !wide.value && jobs.detail.value !== null,
  set: (open: boolean) => {
    if (!open) selection.close()
  },
})
const list = computed(() => jobs.list.value)
const sections = computed(() => {
  const current = list.value
  if (!current) return []
  const shown: {key: string, rows: JobRow[]}[] = []
  if (current.inCompany) shown.push({key: 'company', rows: current.company})
  shown.push({key: 'open', rows: current.open}, {key: 'mine', rows: current.mine})
  return shown
})

function posted(details: JobDetails): void {
  posting.value = false
  jobs.changed(details)
  selection.open(details.job.id)
}
</script>

<template>
  <ViewContent :title="t('nav.jobs')">
    <MutedText tag="p">{{ t('jobs.intro') }}</MutedText>
    <Alert v-if="jobs.status.value === 'failed'" variant="problem">{{ t('jobs.failed') }}</Alert>
    <ContractForm v-if="posting" @posted="posted" @close="posting = false"/>
    <PrimaryButton v-else-if="list?.canPost" class="self-start" data-testid="contract-open" @click="posting = true">{{ t('jobs.contract.open') }}</PrimaryButton>
    <div class="grid items-start gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(20rem,1fr)]">
      <div class="flex min-w-0 flex-col gap-4">
        <PanelBox v-for="section in sections" :key="section.key" :aria-label="t(`jobs.list.${section.key}`)" :data-testid="`jobs-${section.key}`">
          <template #head>
            <SectionHeader>{{ t(`jobs.list.${section.key}`) }}</SectionHeader>
          </template>
          <EmptyState v-if="section.rows.length === 0" :loading="jobs.status.value === 'loading'">{{ t(`jobs.none.${section.key}`) }}</EmptyState>
          <PlainList v-else>
            <li v-for="job in section.rows" :key="job.id">
              <RowButton :selected="selection.id.value === job.id" @click="selection.open(job.id)">
                <span class="flex min-w-0 grow flex-col gap-0.5">
                  <span class="flex min-w-0 items-center gap-1.5">
                    <RowTitle>{{ job.title }}</RowTitle>
                    <PrimaryBadge>{{ t(`jobs.kind.${job.kind}`) }}</PrimaryBadge>
                    <SecondaryBadge v-if="job.state !== 'open'">{{ t(`jobs.state.${job.state}`) }}</SecondaryBadge>
                  </span>
                  <span v-if="job.world" class="truncate text-[0.6875rem] text-ink-muted">{{ t('jobs.place', {center: job.center, world: job.world}) }}</span>
                </span>
                <span class="shrink-0 tabular text-xs">{{ money(job.price, locale) }}</span>
              </RowButton>
            </li>
          </PlainList>
        </PanelBox>
      </div>
      <JobDetail
          v-if="wide && jobs.detail.value"
          :details="jobs.detail.value"
          class="sticky top-4"
          @changed="jobs.changed"
          @stale="jobs.refresh"
      />
      <MutedText v-else-if="wide && jobs.detailStatus.value === 'failed'" tag="p">{{ t('jobs.detailFailed') }}</MutedText>
    </div>
    <BottomSheet v-if="jobs.detail.value" v-model:open="sheetOpen" :title="jobs.detail.value.job.title" :description="t(`jobs.kind.${jobs.detail.value.job.kind}`)">
      <JobDetail :details="jobs.detail.value" in-sheet @changed="jobs.changed" @stale="jobs.refresh"/>
    </BottomSheet>
  </ViewContent>
</template>
