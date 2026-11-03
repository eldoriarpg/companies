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
import DecimalInput from '@/components/input/DecimalInput.vue'
import NumberInput from '@/components/input/NumberInput.vue'
import TextInput from '@/components/input/TextInput.vue'
import TextAreaInput from '@/components/input/TextAreaInput.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import {count, dateTime, money} from '~/util/format'
import {jobSteps, needsHours, needsText, warns, type JobStep} from '~/util/jobs'

/**
 * A job with the steps the player may take on it. A step that needs a reason, a description or a number of hours asks
 * for it first, and is only sent with hours the job may still get. The owner never sees the company working on the
 * job, and companies never see its owner. Area jobs are posted and checked in game; the page says where.
 */
const props = defineProps<{
  details: JobDetails
  inSheet?: boolean
}>()

const emit = defineEmits<{
  changed: [details: JobDetails]
  /** The plugin refused a step, so the job is read again. */
  stale: [job: number]
  /** The job is gone, such as a deleted draft. */
  removed: [job: number]
}>()

const {t, locale} = useI18n()
const action = useAction()
const job = computed(() => props.details.job)
const steps = computed(() => jobSteps(props.details))
const writing = ref<JobStep | null>(null)
const text = ref('')
const hours = ref<number | undefined>()
const title = ref('')
const price = ref<number | undefined>()

/**
 * @param step a step
 * @returns whether the step waits for hours the job may still get before it is sent
 */
function blocked(step: JobStep): boolean {
  if (step === 'edit' && writing.value === 'edit') {
    return title.value.trim() === '' || (price.value ?? 0) <= 0 || (hours.value ?? 0) < 1
  }
  if (writing.value !== step || !needsHours(step)) return false
  const value = hours.value ?? 0
  return value < 1 || value > (props.details.hoursLeft ?? 0)
}
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
    case 'extend':
      return action.run('jobs.extend', {job: id, hours: hours.value ?? 0})
    case 'ask':
      return action.run('jobs.extension.ask', {job: id, hours: hours.value ?? 0, reason: written})
    case 'grant':
      return action.run('jobs.extension.grant', {job: id})
    case 'refuse':
      return action.run('jobs.extension.refuse', {job: id, reason: written})
    case 'askCancel':
      return action.run('jobs.cancel', {job: id})
    case 'cancelAccept':
      return action.run('jobs.cancel.accept', {job: id})
    case 'cancelRefuse':
      return action.run('jobs.cancel.refuse', {job: id})
    case 'collect':
      return action.run('jobs.collect', {job: id})
    case 'post':
      return action.run('jobs.drafts.post', {id, ...(job.value.node ? {node: job.value.node} : {})})
    case 'edit':
      return action.run('jobs.drafts.edit', {id, title: title.value.trim(), description: text.value, price: price.value ?? 0, hours: hours.value ?? 0})
    case 'remove':
      return action.run('jobs.drafts.delete', {id}).then(() => null)
  }
}

async function take(step: JobStep): Promise<void> {
  if (step === 'edit' && writing.value !== step) {
    writing.value = step
    title.value = job.value.title
    price.value = job.value.price
    hours.value = job.value.dueHours ?? undefined
    text.value = props.details.description ?? ''
    return
  }
  if ((needsText(step) || needsHours(step)) && writing.value !== step) {
    writing.value = step
    text.value = step === 'describe' ? (props.details.description ?? '') : ''
    hours.value = undefined
    return
  }
  const id = job.value.id
  const result = await send(step, id, text.value)
  if (result) {
    writing.value = null
    emit('changed', result)
  } else if (step === 'remove' && !action.problem.value) {
    emit('removed', id)
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
    <MutedText v-if="details.progress && job.state === 'taken'" tag="p" data-testid="job-progress">{{ t('jobs.progress', {percent: details.progress.percent, done: count(details.progress.done, locale), total: count(details.progress.total, locale)}) }}</MutedText>
    <MutedText v-if="details.materialCost" tag="p" data-testid="job-material-cost">{{ t(details.materialCost.unpriced > 0 ? 'jobs.materialCostUnpriced' : 'jobs.materialCost', {cost: money(details.materialCost.total, locale), unpriced: details.materialCost.unpriced}) }}</MutedText>
    <MutedText v-if="details.targetY != null" tag="p">{{ details.surface ? t('jobs.targetSurface', {y: details.targetY, block: details.surface.toLowerCase()}) : t('jobs.target', {y: details.targetY}) }}</MutedText>
    <MutedText v-if="job.state === 'open' && job.expires" tag="p">{{ t('jobs.expires', {time: dateTime(job.expires, locale)}) }}</MutedText>
    <MutedText v-if="job.due && (job.state === 'taken' || job.state === 'done')" tag="p">{{ t('jobs.due', {time: dateTime(job.due, locale)}) }}</MutedText>
    <MutedText v-if="job.state === 'done' && details.approveAt" tag="p" data-testid="job-approve-at">{{ t('jobs.approveAt', {time: dateTime(details.approveAt, locale)}) }}</MutedText>
    <MutedText v-if="(details.extendedHours ?? 0) > 0 && job.state === 'taken'" tag="p" data-testid="job-extended">{{ t('jobs.extended', {hours: details.extendedHours}) }}</MutedText>
    <MutedText v-if="details.request && job.state === 'taken'" tag="p" data-testid="job-extension-request">{{ t(job.mine ? 'jobs.extensionAsked' : 'jobs.extensionWaiting', {hours: details.request.hours, reason: details.request.reason}) }}</MutedText>
    <MutedText v-if="details.rejected && job.state === 'taken' && !(details.reviews ?? []).length" tag="p">{{ t('jobs.rejected', {reason: details.rejected}) }}</MutedText>
    <MutedText v-if="details.cancelAsked && job.state === 'taken'" tag="p" data-testid="job-cancel-asked">{{ t(job.mine ? 'jobs.cancelAskedOwner' : 'jobs.cancelAskedCompany') }}</MutedText>
    <section v-if="(details.reviews ?? []).length > 0" class="flex flex-col gap-2" data-testid="job-reviews">
      <SectionHeader>{{ t('jobs.reviews') }}</SectionHeader>
      <ul class="m-0 flex list-none flex-col gap-1 p-0 text-sm">
        <li v-for="(review, index) in details.reviews" :key="index">{{ review.at ? dateTime(review.at, locale) + ': ' : '' }}{{ review.kind === 'rejected' ? t('jobs.review.rejected', {reason: review.reason ?? ''}) : t('jobs.review.done') }}</li>
      </ul>
    </section>
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
      <MutedText tag="p">{{ t('jobs.collectInGame', {id: job.id}) }} {{ t('jobs.collectNote') }}</MutedText>
    </section>
    <MutedText v-if="finishInGame" tag="p">{{ t('jobs.finishInGame', {id: job.id}) }}</MutedText>
    <ActionNotice :problem="action.problem.value"/>
    <NumberInput
        v-if="writing && needsHours(writing)"
        v-model="hours"
        :label="t('jobs.hours', {left: details.hoursLeft ?? 0})"
        :min="1"
        :max="details.hoursLeft ?? 0"
        data-testid="job-hours"
    />
    <template v-if="writing === 'edit'">
      <TextInput v-model="title" :label="t('jobs.contract.name')" :maxlength="64" data-testid="draft-title"/>
      <DecimalInput v-model="price" :label="t('jobs.contract.price')" :min="0.01" data-testid="draft-price"/>
      <NumberInput v-model="hours" :label="t('jobs.contract.hours')" :min="1" data-testid="draft-hours"/>
      <TextAreaInput v-model="text" :label="t('jobs.contract.description')" :maxlength="512" :rows="3" data-testid="draft-description"/>
    </template>
    <MutedText v-if="job.state === 'draft'" tag="p" data-testid="job-draft">{{ t('jobs.draftNote') }}</MutedText>
    <TextAreaInput
        v-if="writing && needsText(writing)"
        v-model="text"
        :label="t(`jobs.text.${writing}`)"
        :maxlength="writing === 'describe' ? 512 : 256"
        :rows="3"
        data-testid="job-text"
    />
    <div v-if="steps.length > 0" class="flex flex-wrap gap-2">
      <template v-for="step in steps" :key="step">
        <SecondaryButton v-if="warns(step)" :disabled="action.busy.value || blocked(step)" :data-testid="`job-${step}`" @click="take(step)">{{ t(`jobs.step.${step}`) }}</SecondaryButton>
        <PrimaryButton v-else :disabled="action.busy.value || blocked(step)" :data-testid="`job-${step}`" @click="take(step)">{{ t(`jobs.step.${step}`) }}</PrimaryButton>
      </template>
    </div>
  </aside>
</template>
