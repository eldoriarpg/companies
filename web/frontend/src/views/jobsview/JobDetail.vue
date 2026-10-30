/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {JobDetails} from '~/api/gateway'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import FigureValue from '@/components/display/FigureValue.vue'
import ActionNotice from '@/components/feedback/ActionNotice.vue'
import TextAreaInput from '@/components/input/TextAreaInput.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import {count, dateTime, money} from '~/util/format'
import {jobSteps, needsText, warns, type JobStep} from '~/util/jobs'

/**
 * A job with the steps the player may take on it. A step that needs a reason or a description asks for it first. The
 * owner never sees the company working on the job, and companies never see its owner. Area jobs are posted and checked
 * in game; the page says where.
 */
const props = defineProps<{
  details: JobDetails
  inSheet?: boolean
}>()

const emit = defineEmits<{
  changed: [details: JobDetails]
  /** The plugin refused a step, so the job is read again. */
  stale: [job: number]
}>()

const {t, locale} = useI18n()
const action = useAction()
const job = computed(() => props.details.job)
const steps = computed(() => jobSteps(props.details))
const writing = ref<JobStep | null>(null)
const text = ref('')
const stock = computed(() => Object.entries(props.details.stock))
const finishInGame = computed(() => job.value.ours && job.value.state === 'taken' && job.value.kind !== 'contract' && job.value.kind !== 'harvest')

watch(() => job.value.id, () => {
  action.reset()
  writing.value = null
})

/**
 * Sends a step to the plugin.
 *
 * @param step the step
 * @param id the job's id
 * @param written the reason or description the step needs
 * @returns the job afterwards, none when the step failed
 */
function send(step: JobStep, id: number, written: string): Promise<JobDetails | null> {
  switch (step) {
    case 'describe':
      return action.run('jobs.describe', {job: id, text: written})
    case 'reject':
      return action.run('jobs.reject', {job: id, reason: written})
    case 'dispute':
      return action.run('jobs.dispute', {job: id, reason: written})
    case 'take':
      return action.run('jobs.take', {job: id})
    case 'giveback':
      return action.run('jobs.giveback', {job: id})
    case 'done':
      return action.run('jobs.done', {job: id})
    case 'end':
      return action.run('jobs.end', {job: id})
    case 'approve':
      return action.run('jobs.approve', {job: id})
    case 'withdraw':
      return action.run('jobs.withdraw', {job: id})
    case 'cancel':
      return action.run('jobs.cancel', {job: id})
  }
}

async function take(step: JobStep): Promise<void> {
  if (needsText(step) && writing.value !== step) {
    writing.value = step
    text.value = step === 'describe' ? (props.details.description ?? '') : ''
    return
  }
  const id = job.value.id
  const result = await send(step, id, text.value)
  if (result) {
    writing.value = null
    emit('changed', result)
  } else if (action.problem.value?.kind === 'refused') {
    emit('stale', id)
  }
}
</script>

<template>
  <aside :aria-label="t('jobs.details')" :class="inSheet ? '' : 'border border-line bg-surface p-4'" class="flex min-w-0 flex-col gap-4" data-testid="job-detail">
    <div v-if="!inSheet" class="flex flex-col gap-1">
      <SectionHeader>{{ job.title }}</SectionHeader>
      <MutedText>{{ t(`jobs.kind.${job.kind}`) }} · {{ t(`jobs.state.${job.state}`) }}</MutedText>
    </div>
    <div class="grid grid-cols-2 gap-2">
      <FigureValue :label="t(job.kind === 'harvest' ? 'jobs.figure.budget' : 'jobs.figure.price')">{{ money(job.price, locale) }}</FigureValue>
      <FigureValue v-if="job.kind === 'harvest'" :label="t('jobs.figure.perItem')">{{ money(job.unitPrice ?? 0, locale) }}</FigureValue>
      <FigureValue v-else-if="job.blocks > 0" :label="t('jobs.figure.blocks')">{{ count(job.blocks, locale) }}</FigureValue>
    </div>
    <MutedText v-if="job.kind === 'harvest'" tag="p">{{ t('jobs.paidSoFar', {paid: money(job.paid ?? 0, locale)}) }}</MutedText>
    <p v-if="details.description" class="m-0 whitespace-pre-line text-sm" data-testid="job-description">{{ details.description }}</p>
    <MutedText v-if="job.world" tag="p">{{ t('jobs.place', {center: job.center, world: job.world}) }}</MutedText>
    <MutedText v-if="details.targetY != null" tag="p">{{ details.surface ? t('jobs.targetSurface', {y: details.targetY, block: details.surface.toLowerCase()}) : t('jobs.target', {y: details.targetY}) }}</MutedText>
    <MutedText v-if="job.state === 'open' && job.expires" tag="p">{{ t('jobs.expires', {time: dateTime(job.expires, locale)}) }}</MutedText>
    <MutedText v-if="job.due && (job.state === 'taken' || job.state === 'done')" tag="p">{{ t('jobs.due', {time: dateTime(job.due, locale)}) }}</MutedText>
    <MutedText v-if="job.state === 'done' && details.approveAt" tag="p" data-testid="job-approve-at">{{ t('jobs.approveAt', {time: dateTime(details.approveAt, locale)}) }}</MutedText>
    <MutedText v-if="details.rejected && job.state === 'taken'" tag="p">{{ t('jobs.rejected', {reason: details.rejected}) }}</MutedText>
    <MutedText v-if="job.state === 'disputed'" tag="p" data-testid="job-dispute-reason">{{ t('jobs.dispute', {reason: details.dispute ?? ''}) }}</MutedText>
    <MutedText v-if="job.state === 'settled' && details.settled != null" tag="p">{{ t('jobs.settled', {percent: details.settled}) }}</MutedText>
    <section v-if="stock.length > 0" class="flex flex-col gap-2" data-testid="job-stock">
      <SectionHeader>{{ t('jobs.stock') }}</SectionHeader>
      <ul class="m-0 list-none border border-line bg-ground px-3 py-0.5">
        <li v-for="[material, amount] in stock" :key="material" class="flex justify-between gap-3 border-b border-line-soft py-2 text-sm last:border-b-0">
          <span>{{ material.toLowerCase() }}</span>
          <span class="tabular">{{ count(amount, locale) }}</span>
        </li>
      </ul>
      <MutedText tag="p">{{ t('jobs.collectInGame', {id: job.id}) }}</MutedText>
    </section>
    <MutedText v-if="finishInGame" tag="p">{{ t('jobs.finishInGame', {id: job.id}) }}</MutedText>
    <ActionNotice :problem="action.problem.value"/>
    <TextAreaInput
        v-if="writing"
        v-model="text"
        :label="t(`jobs.text.${writing}`)"
        :maxlength="writing === 'describe' ? 512 : 256"
        :rows="3"
        data-testid="job-text"
    />
    <div v-if="steps.length > 0" class="flex flex-wrap gap-2">
      <template v-for="step in steps" :key="step">
        <SecondaryButton v-if="warns(step)" :disabled="action.busy.value" :data-testid="`job-${step}`" @click="take(step)">{{ t(`jobs.step.${step}`) }}</SecondaryButton>
        <PrimaryButton v-else :disabled="action.busy.value" :data-testid="`job-${step}`" @click="take(step)">{{ t(`jobs.step.${step}`) }}</PrimaryButton>
      </template>
    </div>
  </aside>
</template>
