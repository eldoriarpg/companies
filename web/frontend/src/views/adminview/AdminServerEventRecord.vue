/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {AdminEventRow} from '~/api/gateway'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ActionNotice from '@/components/feedback/ActionNotice.vue'
import ConfirmStep from '@/components/feedback/ConfirmStep.vue'
import DecimalInput from '@/components/input/DecimalInput.vue'
import TextInput from '@/components/input/TextInput.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {dateTime, money} from '~/util/format'
import {closerKind, serverEventActions} from '~/util/serverEvents'

/**
 * One server event: its effect, scope, times, money, who created and closed it and its event offers, with the changes
 * its state allows: ending, cancelling, extending it and setting the budget of a multiplier. The plugin refuses what
 * the command refuses; each change answers the event afterwards.
 */
const props = defineProps<{
  event: AdminEventRow
}>()

const emit = defineEmits<{
  changed: [event: AdminEventRow]
}>()

const {t, locale} = useI18n()
const changing = useAction()
const duration = ref('')
const budget = ref<number | undefined>(props.event.budget ?? undefined)
const actions = computed(() => serverEventActions(props.event.state, props.event.kind))
const closer = computed(() => closerKind(props.event.closedBy))
const scope = computed(() => {
  const parts: string[] = []
  if (props.event.materials.length > 0) parts.push(t('events.server.materials', {materials: props.event.materials.join(', ')}))
  if (props.event.levels.length > 0) parts.push(t('events.server.levels', {levels: props.event.levels.join(', ')}))
  return parts.length > 0 ? parts.join(' · ') : t('events.server.everything')
})

watch(() => props.event, event => {
  budget.value = event.budget ?? undefined
})

watch(() => props.event.id, () => {
  changing.reset()
  duration.value = ''
})

/**
 * Ends or cancels the event.
 *
 * @param action the change
 */
async function change(action: 'admin.events.end' | 'admin.events.cancel'): Promise<void> {
  const result = await changing.run(action, {event: props.event.id})
  if (result) emit('changed', result)
}

/** Moves the event's end later by the typed duration. */
async function extend(): Promise<void> {
  const result = await changing.run('admin.events.extend', {event: props.event.id, duration: duration.value.trim()})
  if (result) {
    duration.value = ''
    emit('changed', result)
  }
}

/** Sets the budget, or removes it when the field is empty. */
async function setBudget(): Promise<void> {
  const result = await changing.run('admin.events.budget', {event: props.event.id, budget: budget.value ?? null})
  if (result) emit('changed', result)
}
</script>

<template>
  <div class="flex flex-col gap-3 px-3 py-3 text-sm md:px-4" data-testid="admin-event-record">
    <dl class="m-0 grid grid-cols-[auto_minmax(0,1fr)] gap-x-4 gap-y-1">
      <dt class="text-ink-muted">{{ t('admin.serverevents.form.kind') }}</dt>
      <dd class="m-0">{{ t(`admin.serverevents.kind.${event.kind}`) }} · {{ t(`events.server.effect.${event.kind}`, {value: event.value}) }}</dd>
      <dt class="text-ink-muted">{{ t('admin.serverevents.form.materials') }}</dt>
      <dd class="m-0">{{ scope }}</dd>
      <dt class="text-ink-muted">{{ t('admin.serverevents.form.start') }}</dt>
      <dd class="m-0 tabular">{{ t('admin.serverevents.period', {starts: dateTime(event.starts, locale), ends: dateTime(event.ends, locale)}) }}</dd>
      <template v-if="event.funding">
        <dt class="text-ink-muted">{{ t('admin.serverevents.form.funding') }}</dt>
        <dd class="m-0 tabular">
          {{ t('admin.serverevents.money', {
            source: event.funding === 'minted' ? t('admin.serverevents.minted') : event.account,
            spent: money(event.spent, locale),
            budget: event.budget == null ? t('admin.serverevents.noBudget') : money(event.budget, locale),
          }) }}
        </dd>
      </template>
      <dt class="text-ink-muted">{{ t(`admin.serverevents.state.${event.state}`) }}</dt>
      <dd class="m-0">
        <span class="block">{{ t('admin.serverevents.createdBy', {by: event.createdBy, created: dateTime(event.created, locale)}) }}</span>
        <span v-if="event.template" class="block">{{ t('admin.serverevents.template', {template: event.template}) }}</span>
        <span v-if="event.closed" class="block" data-testid="admin-event-closer">
          {{ t(`admin.serverevents.closedBy.${closer}`, {by: event.closedBy ?? '', closed: dateTime(event.closed, locale)}) }}
        </span>
      </dd>
    </dl>
    <div>
      <p class="m-0 font-semibold">{{ t('admin.serverevents.offers') }}</p>
      <MutedText v-if="event.offers.length === 0" tag="p">{{ t('admin.serverevents.noOffers') }}</MutedText>
      <ul v-else class="m-0 list-none p-0">
        <li v-for="offer in event.offers" :key="offer.id">#{{ offer.id }} {{ offer.name }}</li>
      </ul>
    </div>
    <ActionNotice :problem="changing.problem.value"/>
    <form v-if="actions.extend" class="flex flex-wrap items-end gap-2" @submit.prevent="extend">
      <TextInput v-model="duration" :label="t('admin.serverevents.extend')" :hint="t('admin.serverevents.extendHint')"/>
      <SecondaryButton type="submit" :disabled="changing.busy.value || duration.trim() === ''" data-testid="admin-event-extend">{{ t('admin.serverevents.applyExtend') }}</SecondaryButton>
    </form>
    <form v-if="actions.budget" class="flex flex-wrap items-end gap-2" @submit.prevent="setBudget">
      <DecimalInput v-model="budget" :label="t('admin.serverevents.budget')" :hint="t('admin.serverevents.budgetHint')" :min="0"/>
      <SecondaryButton type="submit" :disabled="changing.busy.value" data-testid="admin-event-budget">{{ t('admin.serverevents.applyBudget') }}</SecondaryButton>
    </form>
    <ConfirmStep
        v-if="actions.end"
        :trigger="t('admin.serverevents.end')"
        :question="t('admin.serverevents.endQuestion', {name: event.name})"
        :confirm="t('admin.serverevents.endConfirm')"
        :busy="changing.busy.value"
        data-testid="admin-event-end"
        @confirmed="change('admin.events.end')"
    />
    <ConfirmStep
        v-if="actions.cancel"
        :trigger="t('admin.serverevents.cancel')"
        :question="t('admin.serverevents.cancelQuestion', {name: event.name})"
        :confirm="t('admin.serverevents.cancelConfirm')"
        :busy="changing.busy.value"
        @confirmed="change('admin.events.cancel')"
    />
  </div>
</template>
