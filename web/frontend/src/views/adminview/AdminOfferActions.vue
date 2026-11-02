/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {AdminOffer} from '~/api/gateway'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ActionNotice from '@/components/feedback/ActionNotice.vue'
import ConfirmStep from '@/components/feedback/ConfirmStep.vue'
import DecimalInput from '@/components/input/DecimalInput.vue'
import NumberInput from '@/components/input/NumberInput.vue'
import {offerActions} from '~/util/offers'

/**
 * The changes of an event offer its state allows: pausing, resuming and cancelling it, how many chunks of a goal wait
 * at once, when it ends, counted in hours from now, and its budget. The plugin refuses what the command refuses; each
 * change answers the offer afterwards.
 */
const props = defineProps<{
  offer: AdminOffer
  /** Whether another change of the offer runs. */
  busy?: boolean
}>()

const emit = defineEmits<{
  changed: [offer: AdminOffer]
}>()

const {t} = useI18n()
const changing = useAction()
const open = ref<number | undefined>(props.offer.openChunks ?? undefined)
const endHours = ref<number | undefined>()
const budget = ref<number | undefined>(props.offer.budget)
const actions = computed(() => offerActions(props.offer.state, props.offer.kind))

watch(() => props.offer, offer => {
  open.value = offer.openChunks ?? undefined
  budget.value = offer.budget
})
watch(() => props.offer.id, () => changing.reset())

/**
 * Changes the offer's state.
 *
 * @param action the change
 */
async function change(action: 'admin.offers.pause' | 'admin.offers.resume' | 'admin.offers.cancel'): Promise<void> {
  const result = await changing.run(action, {offer: props.offer.id})
  if (result) emit('changed', result)
}

/** Changes how many chunks of the goal wait at once. */
async function changeOpen(): Promise<void> {
  if (open.value === undefined) return
  const result = await changing.run('admin.offers.open', {offer: props.offer.id, count: open.value})
  if (result) emit('changed', result)
}

/** Moves the end of the offer to the entered hours from now. */
async function changeEnd(): Promise<void> {
  if (endHours.value === undefined) return
  const end = new Date(Date.now() + endHours.value * 3_600_000).toISOString()
  const result = await changing.run('admin.offers.edit', {offer: props.offer.id, end})
  if (result) {
    endHours.value = undefined
    emit('changed', result)
  }
}

/** Changes the budget of the offer. */
async function changeBudget(): Promise<void> {
  if (budget.value === undefined) return
  const result = await changing.run('admin.offers.edit', {offer: props.offer.id, budget: budget.value})
  if (result) emit('changed', result)
}
</script>

<template>
  <div class="flex flex-col gap-3">
    <ActionNotice :problem="changing.problem.value"/>
    <form v-if="actions.open" class="flex flex-wrap items-end gap-2" @submit.prevent="changeOpen">
      <NumberInput v-model="open" :label="t('admin.offers.open')" :min="1" :max="100"/>
      <SecondaryButton type="submit" :disabled="busy || changing.busy.value">{{ t('admin.offers.applyOpen') }}</SecondaryButton>
    </form>
    <form v-if="actions.edit" class="flex flex-wrap items-end gap-2" @submit.prevent="changeEnd">
      <NumberInput v-model="endHours" :label="t('admin.offers.endHours')" :min="0" :max="8760"/>
      <SecondaryButton type="submit" :disabled="busy || changing.busy.value || endHours === undefined" data-testid="admin-offer-end">{{ t('admin.offers.applyEnd') }}</SecondaryButton>
    </form>
    <form v-if="actions.edit" class="flex flex-wrap items-end gap-2" @submit.prevent="changeBudget">
      <DecimalInput v-model="budget" :label="t('admin.offers.budgetEdit')" :min="0"/>
      <SecondaryButton type="submit" :disabled="busy || changing.busy.value || budget === undefined" data-testid="admin-offer-budget">{{ t('admin.offers.applyBudget') }}</SecondaryButton>
    </form>
    <ButtonRow>
      <SecondaryButton v-if="actions.pause" :disabled="busy || changing.busy.value" data-testid="admin-offer-pause" @click="change('admin.offers.pause')">{{ t('admin.offers.pause') }}</SecondaryButton>
      <SecondaryButton v-if="actions.resume" :disabled="busy || changing.busy.value" data-testid="admin-offer-resume" @click="change('admin.offers.resume')">{{ t('admin.offers.resume') }}</SecondaryButton>
    </ButtonRow>
    <ConfirmStep
        v-if="actions.cancel"
        :trigger="t('admin.offers.cancel')"
        :question="t('admin.offers.cancelQuestion', {name: offer.name})"
        :confirm="t('admin.offers.cancelConfirm')"
        :busy="busy || changing.busy.value"
        @confirmed="change('admin.offers.cancel')"
    />
  </div>
</template>
