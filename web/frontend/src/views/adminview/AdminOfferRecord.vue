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
import SectionHeader from '@/components/typography/SectionHeader.vue'
import AdminOfferActions from './AdminOfferActions.vue'
import AdminOfferFacts from './AdminOfferFacts.vue'
import {money} from '~/util/format'

/**
 * One event offer as its record shows it, like the decision record of a system order: who created it, its money and
 * progress, its contributors or its ranking and settled prizes, with pausing, resuming, cancelling, the chunks of a
 * goal waiting at once and settling a prize stuck while being paid. Each change answers the offer afterwards.
 */
const props = defineProps<{
  offer: AdminOffer
}>()

const emit = defineEmits<{
  changed: [offer: AdminOffer]
}>()

const {t, locale} = useI18n()
const changing = useAction()
watch(() => props.offer.id, () => changing.reset())

/**
 * Notes the outcome of a prize stuck while being paid.
 *
 * @param place the prize's place
 * @param outcome whether the money reached the company
 */
async function prize(place: number, outcome: 'paid' | 'failed'): Promise<void> {
  const result = await changing.run('admin.offers.prize', {offer: props.offer.id, place, outcome})
  if (result) emit('changed', result)
}
</script>

<template>
  <div class="flex flex-col gap-3 px-3 py-3 text-sm md:px-4" data-testid="admin-offer-record">
    <p class="m-0 font-semibold">
      #{{ offer.id }} {{ offer.name }} · {{ t(`admin.offers.kind.${offer.kind}`) }} · {{ t(`admin.offers.state.${offer.state}`) }}
    </p>
    <AdminOfferFacts :offer="offer"/>
    <section v-if="offer.kind === 'goal'" class="flex flex-col gap-1">
      <SectionHeader>{{ t('admin.offers.contributors') }}</SectionHeader>
      <p v-if="offer.contributors.length === 0" class="m-0 text-ink-muted">{{ t('admin.offers.noContributors') }}</p>
      <ol v-else class="m-0 list-none p-0" data-testid="admin-offer-contributors">
        <li v-for="entry in offer.contributors" :key="entry.rank" class="tabular">
          #{{ entry.rank }} {{ entry.name ?? '-' }}{{ entry.alone ? ` (${t('admin.offers.alone')})` : '' }} · {{ t('admin.offers.delivered', {items: entry.delivered, chunks: entry.chunks}) }}
        </li>
      </ol>
    </section>
    <template v-else>
      <section class="flex flex-col gap-1">
        <SectionHeader>{{ t('admin.offers.ranking') }}</SectionHeader>
        <p v-if="offer.ranking.length === 0" class="m-0 text-ink-muted">{{ t('admin.offers.noEntries') }}</p>
        <ol v-else class="m-0 list-none p-0" data-testid="admin-offer-ranking">
          <li v-for="entry in offer.ranking" :key="entry.company" class="tabular">
            #{{ entry.place }} {{ entry.name ?? `#${entry.company}` }}{{ entry.active ? '' : ` (${t('admin.offers.left')})` }} · {{ t('admin.offers.entry', {items: entry.delivered, paid: money(entry.paid, locale)}) }}
          </li>
        </ol>
      </section>
      <section v-if="offer.prizes.length > 0" class="flex flex-col gap-1">
        <SectionHeader>{{ t('admin.offers.settled') }}</SectionHeader>
        <ul class="m-0 list-none p-0" data-testid="admin-offer-prizes">
          <li v-for="entry in offer.prizes" :key="entry.place" class="flex flex-wrap items-center gap-2">
            <span class="tabular">#{{ entry.place }} {{ money(entry.amount, locale) }} · {{ entry.name ?? '-' }} · {{ t(`admin.offers.prize.${entry.state}`) }}</span>
            <ButtonRow v-if="entry.state === 'paying'">
              <SecondaryButton :disabled="changing.busy.value" @click="prize(entry.place, 'paid')">{{ t('admin.offers.resolvePaid') }}</SecondaryButton>
              <SecondaryButton :disabled="changing.busy.value" @click="prize(entry.place, 'failed')">{{ t('admin.offers.resolveFailed') }}</SecondaryButton>
            </ButtonRow>
          </li>
        </ul>
      </section>
    </template>
    <ActionNotice :problem="changing.problem.value"/>
    <AdminOfferActions :offer="offer" :busy="changing.busy.value" @changed="emit('changed', $event)"/>
  </div>
</template>
