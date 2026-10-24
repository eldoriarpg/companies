/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PanelBox from '@/components/container/PanelBox.vue'
import ActionNotice from '@/components/feedback/ActionNotice.vue'
import TextInput from '@/components/input/TextInput.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import {money} from '~/util/format'

/**
 * Founds a company for a player who has none, as `/company create` does: the plugin first checks the name and
 * names the price, then founds it and takes the price from the player's balance. The new company arrives through its
 * patch, so founding is told to the page.
 */
const MAX_NAME = 31

/** What founding costs, as the plugin quoted it for a free name. */
interface FoundingQuote {
  name: string
  price: number
}

const emit = defineEmits<{
  founded: [name: string]
}>()

const {t, locale} = useI18n()
const action = useAction()
const name = ref('')
const quote = ref<FoundingQuote | null>(null)

watch(name, () => {
  quote.value = null
})

/** Asks the plugin whether the name is free and what founding costs. */
async function check(): Promise<void> {
  const result = await action.run('company.create', {name: name.value.trim(), quote: true})
  if (result) quote.value = {name: result.name ?? name.value.trim(), price: result.price}
}

/** Founds the company. */
async function found(): Promise<void> {
  if (!quote.value) return
  const founded = quote.value.name
  if (!await action.run('company.create', {name: founded})) return
  quote.value = null
  name.value = ''
  emit('founded', founded)
}
</script>

<template>
  <PanelBox padded :aria-label="t('company.foundTitle')">
    <SectionHeader>{{ t('company.foundTitle') }}</SectionHeader>
    <MutedText tag="p">{{ t('company.foundLead') }}</MutedText>
    <form class="flex flex-wrap items-end gap-2" @submit.prevent="check">
      <TextInput v-model="name" :label="t('company.foundName')" :maxlength="MAX_NAME"/>
      <SecondaryButton type="submit" :disabled="action.busy.value || name.trim() === ''">{{ t('company.foundCheck') }}</SecondaryButton>
    </form>
    <ActionNotice :problem="action.problem.value"/>
    <template v-if="quote">
      <p class="m-0 text-sm">{{ t('company.foundPrice', {name: quote.name, price: money(quote.price, locale)}) }}</p>
      <ButtonRow>
        <PrimaryButton :disabled="action.busy.value" @click="found">{{ t('company.foundConfirm', {price: money(quote.price, locale)}) }}</PrimaryButton>
      </ButtonRow>
    </template>
  </PanelBox>
</template>
