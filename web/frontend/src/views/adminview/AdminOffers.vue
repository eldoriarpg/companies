/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {AdminOffer, AdminOfferRow} from '~/api/gateway'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import RowButton from '@/components/button/RowButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PanelBox from '@/components/container/PanelBox.vue'
import PlainList from '@/components/container/PlainList.vue'
import ProgressBar from '@/components/display/ProgressBar.vue'
import ActionNotice from '@/components/feedback/ActionNotice.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import AdminOfferForm from './AdminOfferForm.vue'
import AdminOfferRecord from './AdminOfferRecord.vue'
import {dateTime, money} from '~/util/format'

/**
 * The event offers of the server, as `/companyadmin offers` offers them: the newest offers with their state, progress
 * and money, and one offer as its record shows it. New goals and contests are created in a form.
 */
const {t, locale} = useI18n()
const loading = useAction()
const opening = useAction()
const offers = ref<AdminOfferRow[] | null>(null)
const chosen = ref<AdminOffer | null>(null)
const creating = ref(false)

onMounted(() => {
  void load()
})

/** Loads the newest offers. */
async function load(): Promise<void> {
  const listed = await loading.run('admin.offers.list', {})
  if (listed) offers.value = listed.offers
}

/**
 * Opens an offer.
 *
 * @param offer the offer
 */
async function show(offer: AdminOfferRow): Promise<void> {
  creating.value = false
  const result = await opening.run('admin.offers.info', {offer: offer.id})
  if (result) chosen.value = result
}

/**
 * Shows an offer as an action answered it and lists the offers again.
 *
 * @param offer the offer afterwards
 */
async function changed(offer: AdminOffer): Promise<void> {
  creating.value = false
  chosen.value = offer
  await load()
}
</script>

<template>
  <div class="grid min-w-0 items-start gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.2fr)]">
    <PanelBox :aria-label="t('admin.offers.title')">
      <template #head>
        <SectionHeader>{{ t('admin.offers.title') }}</SectionHeader>
        <ButtonRow>
          <SecondaryButton :disabled="loading.busy.value" @click="load">{{ t('admin.offers.refresh') }}</SecondaryButton>
          <SecondaryButton data-testid="admin-offer-new" @click="creating = true">{{ t('admin.offers.new') }}</SecondaryButton>
        </ButtonRow>
      </template>
      <ActionNotice :problem="loading.problem.value" class="mx-3 mt-3 md:mx-4"/>
      <template v-if="offers">
        <EmptyState v-if="offers.length === 0">{{ t('admin.offers.none') }}</EmptyState>
        <PlainList v-else data-testid="admin-offers">
          <li v-for="offer in offers" :key="offer.id">
            <RowButton :selected="chosen?.id === offer.id" class="flex-col items-stretch gap-1 text-sm" @click="show(offer)">
              <span class="flex items-center gap-2">
                <span class="font-semibold">#{{ offer.id }} {{ offer.name }}</span>
                <PrimaryBadge>{{ t(`admin.offers.kind.${offer.kind}`) }}</PrimaryBadge>
                <InfoBadge>{{ t(`admin.offers.state.${offer.state}`) }}</InfoBadge>
              </span>
              <ProgressBar :value="offer.percent" :label="t('admin.offers.progress', {name: offer.name})" tone="accent"/>
              <span class="tabular text-ink-muted">
                {{ t('admin.offers.row', {item: offer.item.name, delivered: offer.delivered, amount: offer.amount, percent: offer.percent, ends: dateTime(offer.ends, locale)}) }}
              </span>
              <span class="tabular text-ink-muted">
                {{ t('admin.offers.money', {source: offer.account ?? t('admin.offers.minted'), budget: money(offer.budget, locale), spent: money(offer.spent, locale), left: money(offer.left, locale)}) }}
              </span>
            </RowButton>
          </li>
        </PlainList>
      </template>
    </PanelBox>
    <PanelBox :aria-label="creating ? t('admin.offers.new') : t('admin.offers.record')">
      <template #head>
        <SectionHeader>{{ creating ? t('admin.offers.new') : t('admin.offers.record') }}</SectionHeader>
      </template>
      <AdminOfferForm v-if="creating" @created="changed" @cancel="creating = false"/>
      <template v-else>
        <ActionNotice :problem="opening.problem.value" class="mx-3 mt-3 md:mx-4"/>
        <EmptyState v-if="!chosen">{{ t('admin.offers.choose') }}</EmptyState>
        <AdminOfferRecord v-else :offer="chosen" @changed="changed"/>
      </template>
    </PanelBox>
  </div>
</template>
