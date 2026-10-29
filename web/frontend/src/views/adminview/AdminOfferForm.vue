/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {AdminOffer, AdminOfferDraft, OfferKind} from '~/api/gateway'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ActionNotice from '@/components/feedback/ActionNotice.vue'
import DecimalInput from '@/components/input/DecimalInput.vue'
import NumberInput from '@/components/input/NumberInput.vue'
import SelectInput from '@/components/input/SelectInput.vue'
import TextInput from '@/components/input/TextInput.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {draftOf, type OfferForm} from '~/util/offers'

/**
 * The form of a new goal or contest, as `/companyadmin offers goal` and `contest` take it: its name, its money, what it
 * orders as a material or criteria, the items in all and the price per item, for a goal the chunk size and the chunks
 * waiting at once, for a contest the prizes of the first places, the hours until it starts and that it runs, and a
 * budget of its own. The plugin checks it as the command does and its refusal is shown as it words it.
 */
const emit = defineEmits<{
  created: [offer: AdminOffer]
  cancel: []
}>()

const {t} = useI18n()
const creating = useAction()
const form = reactive<OfferForm>({
  kind: 'goal',
  name: '',
  money: 'minted',
  itemKind: 'material',
  item: '',
  amount: 1000,
  price: 1,
  chunk: 64,
  open: 3,
  prizes: '',
  start: 0,
  hours: 72,
  budget: undefined,
})
const kinds = computed(() => (['goal', 'contest'] as OfferKind[]).map(value => ({value, label: t(`admin.offers.kind.${value}`)})))
const itemKinds = computed(() => ['material', 'criteria'].map(value => ({value, label: t(`admin.offers.form.itemKind.${value}`)})))
const kind = computed({
  get: () => form.kind as string,
  set: (value: string) => {
    form.kind = value as OfferKind
  },
})
const itemKind = computed({
  get: () => form.itemKind as string,
  set: (value: string) => {
    form.itemKind = value as OfferForm['itemKind']
  },
})

/** Sends the form; a created offer is handed on. */
async function create(): Promise<void> {
  const draft: AdminOfferDraft = draftOf(form)
  const created = await creating.run('admin.offers.create', draft)
  if (created) emit('created', created)
}
</script>

<template>
  <form class="flex flex-col gap-3 px-3 py-3 md:px-4" data-testid="admin-offer-form" @submit.prevent="create">
    <div class="grid gap-3 md:grid-cols-2">
      <SelectInput v-model="kind" :label="t('admin.offers.form.kind')" :options="kinds"/>
      <TextInput v-model="form.name" :label="t('admin.offers.form.name')" :maxlength="31"/>
      <TextInput v-model="form.money" :label="t('admin.offers.form.money')" :hint="t('admin.offers.form.moneyHint')"/>
      <SelectInput v-model="itemKind" :label="t('admin.offers.form.itemKind.label')" :options="itemKinds"/>
      <TextInput
          v-model="form.item"
          :label="form.itemKind === 'material' ? t('admin.offers.form.material') : t('admin.offers.form.criteria')"
          :hint="form.itemKind === 'material' ? t('admin.offers.form.materialHint') : t('admin.offers.form.criteriaHint')"
          class="md:col-span-2"
      />
      <NumberInput v-model="form.amount" :label="t('admin.offers.form.amount')" :min="1"/>
      <DecimalInput v-model="form.price" :label="t('admin.offers.form.price')" :min="0"/>
      <template v-if="form.kind === 'goal'">
        <NumberInput v-model="form.chunk" :label="t('admin.offers.form.chunk')" :min="1"/>
        <NumberInput v-model="form.open" :label="t('admin.offers.form.open')" :min="1" :max="100"/>
      </template>
      <TextInput v-else v-model="form.prizes" :label="t('admin.offers.form.prizes')" :hint="t('admin.offers.form.prizesHint')" class="md:col-span-2"/>
      <NumberInput v-model="form.start" :label="t('admin.offers.form.start')" :min="0"/>
      <NumberInput v-model="form.hours" :label="t('admin.offers.form.hours')" :min="1"/>
      <DecimalInput v-model="form.budget" :label="t('admin.offers.form.budget')" :hint="t('admin.offers.form.budgetHint')" :min="0"/>
    </div>
    <MutedText tag="p">{{ t('admin.offers.form.exactItems') }}</MutedText>
    <ActionNotice :problem="creating.problem.value"/>
    <ButtonRow>
      <PrimaryButton type="submit" :disabled="creating.busy.value" data-testid="admin-offer-create">{{ t('admin.offers.form.create') }}</PrimaryButton>
      <SecondaryButton @click="emit('cancel')">{{ t('common.back') }}</SecondaryButton>
    </ButtonRow>
  </form>
</template>
