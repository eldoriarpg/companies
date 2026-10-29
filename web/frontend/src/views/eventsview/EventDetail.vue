/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import {useNow} from '@vueuse/core'
import type {EventDetails} from '~/api/gateway'
import {Feature} from '~/api/generated/protocol'
import ButtonLink from '@/components/button/ButtonLink.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import FigureValue from '@/components/display/FigureValue.vue'
import ProgressBar from '@/components/display/ProgressBar.vue'
import ActionNotice from '@/components/feedback/ActionNotice.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import {count, dateTime, money} from '~/util/format'
import {closedState, joinable, offerTime} from '~/util/events'

/**
 * One event offer: its progress and time, for a goal the companies that delivered most and its chunks in the order
 * book, for a contest the prizes, the ranking and the part of the player's company. Members who manage the company's
 * orders join or leave a contest here; the plugin checks that again and its refusal is shown as it words it.
 * Delivering into a contest is done in game. A closed offer shows its final ranking and prize winners without
 * buttons. After a refused join or leave the offer is read again, so the button follows what the plugin knows.
 */
const props = defineProps<{
  details: EventDetails
  inSheet?: boolean
}>()

const emit = defineEmits<{
  changed: [details: EventDetails]
  /** The plugin refused a change, so the offer is read again. */
  stale: [offer: number]
}>()

const {t, locale} = useI18n()
const {supports} = useDashboard()
const action = useAction()
const now = useNow({interval: 60_000})
const offer = computed(() => props.details.offer)
const time = computed(() => offerTime(offer.value, now.value))
const contest = computed(() => offer.value.kind === 'contest')
const canJoin = computed(() => joinable(offer.value, props.details.canManage))
const closed = computed(() => closedState(offer.value.state))

watch(() => offer.value.id, () => action.reset())

/** Lets the company join or leave the contest. */
async function toggle(): Promise<void> {
  const joined = props.details.own?.joined ?? false
  const result = await action.run(joined ? 'events.leave' : 'events.join', {offer: offer.value.id})
  if (result) emit('changed', result)
  else if (action.problem.value?.kind === 'refused') emit('stale', offer.value.id)
}
</script>

<template>
  <aside :aria-label="t('events.details')" :class="inSheet ? '' : 'border border-line bg-surface p-4'" class="flex min-w-0 flex-col gap-4" data-testid="event-detail">
    <div v-if="!inSheet" class="flex flex-col gap-1">
      <SectionHeader>{{ offer.name }}</SectionHeader>
      <MutedText>{{ t(`events.kind.${offer.kind}`) }} · {{ offer.item.name }}</MutedText>
    </div>
    <ProgressBar :value="offer.percent" :label="t('events.progress', {name: offer.name})" tone="accent"/>
    <div class="grid grid-cols-3 gap-2">
      <FigureValue :label="t('events.figure.delivered')">{{ count(offer.delivered, locale) }} / {{ count(offer.amount, locale) }}</FigureValue>
      <FigureValue :label="t('events.figure.perItem')">{{ money(offer.unitPrice, locale) }}</FigureValue>
      <FigureValue :label="t(`events.figure.${time.key}`)">{{ closed ? t(`events.state.${offer.state}`) : t('events.hours', {hours: time.hours}) }}</FigureValue>
    </div>
    <MutedText tag="p">{{ t('events.period', {starts: dateTime(offer.starts, locale), ends: dateTime(offer.ends, locale)}) }}</MutedText>
    <MutedText v-if="closed" tag="p" data-testid="event-closed">{{ t('events.closedNote', {state: t(`events.state.${offer.state}`)}) }}</MutedText>
    <template v-if="!contest">
      <MutedText v-if="details.chunkSize" tag="p">{{ t('events.chunks', {size: details.chunkSize}) }}</MutedText>
      <section class="flex flex-col gap-2">
        <SectionHeader>{{ t('events.contributors') }}</SectionHeader>
        <MutedText v-if="details.contributors.length === 0" tag="p">{{ t('events.noContributors') }}</MutedText>
        <ol v-else class="m-0 list-none border border-line bg-ground px-3 py-0.5" data-testid="event-contributors">
          <li v-for="entry in details.contributors" :key="entry.rank" class="flex items-center justify-between gap-3 border-b border-line-soft py-2 text-sm last:border-b-0">
            <span class="min-w-0 truncate"><span class="tabular text-ink-muted">#{{ entry.rank }}</span> {{ entry.alone ? t('events.alone') : entry.name }}</span>
            <span class="tabular">{{ t('events.items', {count: count(entry.delivered, locale)}) }}</span>
          </li>
        </ol>
      </section>
      <ButtonLink v-if="supports(Feature['orders.browse'])" :to="{name: 'orders', query: {offer: String(offer.id)}}" class="self-start" data-testid="event-orders">{{ t('events.showOrders') }}</ButtonLink>
    </template>
    <template v-else>
      <section class="flex flex-col gap-2">
        <SectionHeader>{{ t('events.prizes') }}</SectionHeader>
        <MutedText v-if="details.prizes.length === 0" tag="p">{{ t('events.noPrizes') }}</MutedText>
        <ol v-else class="m-0 list-none border border-line bg-ground px-3 py-0.5" data-testid="event-prizes">
          <li v-for="prize in details.prizes" :key="prize.place" class="flex items-center justify-between gap-3 border-b border-line-soft py-2 text-sm last:border-b-0">
            <span><span class="tabular text-ink-muted">#{{ prize.place }}</span> {{ prize.winner ?? '' }}<span v-if="closed" class="text-ink-muted"> · {{ t(`events.prizeState.${prize.state}`) }}</span></span>
            <span class="tabular">{{ money(prize.amount, locale) }}</span>
          </li>
        </ol>
      </section>
      <section class="flex flex-col gap-2">
        <SectionHeader>{{ closed ? t('events.finalRanking') : t('events.ranking') }}</SectionHeader>
        <MutedText v-if="details.ranking.length === 0" tag="p">{{ t('events.noRanking') }}</MutedText>
        <ol v-else class="m-0 list-none border border-line bg-ground px-3 py-0.5" data-testid="event-ranking">
          <li
              v-for="entry in details.ranking"
              :key="entry.place"
              :aria-current="entry.own ? 'true' : undefined"
              :class="entry.own ? 'bg-selected' : ''"
              class="flex items-center justify-between gap-3 border-b border-line-soft py-2 text-sm last:border-b-0"
          >
            <span class="min-w-0 truncate" :class="entry.own ? 'text-accent' : ''"><span class="tabular text-ink-muted">#{{ entry.place }}</span> {{ entry.name }}<span v-if="!entry.active" class="text-ink-muted"> · {{ t('events.left') }}</span></span>
            <span class="tabular">{{ t('events.items', {count: count(entry.delivered, locale)}) }}</span>
          </li>
        </ol>
      </section>
      <section v-if="details.own" class="flex flex-col gap-1 text-sm" data-testid="event-own">
        <SectionHeader>{{ details.own.company }}</SectionHeader>
        <p class="m-0">{{ details.own.joined ? t('events.ownJoined') : t('events.ownNotJoined') }}</p>
        <p v-if="details.own.delivered > 0" class="m-0 tabular">{{ t('events.ownPart', {items: count(details.own.delivered, locale), paid: money(details.own.paid, locale), place: details.own.place ?? '-'}) }}</p>
      </section>
      <MutedText v-else tag="p">{{ t('events.noCompany') }}</MutedText>
      <ActionNotice :problem="action.problem.value"/>
      <template v-if="canJoin">
        <SecondaryButton v-if="details.own?.joined" :disabled="action.busy.value" data-testid="event-leave" @click="toggle">{{ t('events.leave') }}</SecondaryButton>
        <PrimaryButton v-else :disabled="action.busy.value" full-width data-testid="event-join" @click="toggle">{{ t('events.join') }}</PrimaryButton>
      </template>
      <MutedText v-else-if="!closed && details.member && !details.canManage" tag="p">{{ t('events.joinNeedsPermission') }}</MutedText>
      <MutedText v-if="!closed" tag="p">{{ t('events.deliverInGame', {id: offer.id}) }}</MutedText>
    </template>
  </aside>
</template>
