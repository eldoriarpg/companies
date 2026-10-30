/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import {useMediaQuery, useNow} from '@vueuse/core'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import RowButton from '@/components/button/RowButton.vue'
import PanelBox from '@/components/container/PanelBox.vue'
import PlainList from '@/components/container/PlainList.vue'
import ItemMark from '@/components/display/ItemMark.vue'
import ProgressBar from '@/components/display/ProgressBar.vue'
import Alert from '@/components/feedback/Alert.vue'
import BottomSheet from '@/components/feedback/BottomSheet.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import ViewContent from '@/components/layout/ViewContent.vue'
import MutedText from '@/components/typography/MutedText.vue'
import RowTitle from '@/components/typography/RowTitle.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import EventDetail from './eventsview/EventDetail.vue'
import {orderIdOf} from '~/composables/useSelection'
import {count, money} from '~/util/format'
import {offerMaterial, offerTime} from '~/util/events'

/**
 * The server events and event offers of the server, as `/company events` lists them: the server events that run or
 * start within a day with their effect, scope, time left and budget, then the goals and contests that run, are paused or
 * start within a day, each with its item, progress and time left, the server event it belongs to, and for a contest the
 * place of the player's company. An
 * offer's page opens beside the list from tablet width and as a sheet on a phone; the address names it. Delivering
 * into a contest stays in game, as it takes the items from the inventory.
 */
const {t, locale} = useI18n()
const events = useEvents()
const selection = useSelection('offer', orderIdOf)
const wide = useMediaQuery('(min-width: 1024px)')
const now = useNow({interval: 60_000})

onMounted(() => {
  void events.load()
})
watch(selection.id, id => void events.open(id), {immediate: true})

const sheetOpen = computed({
  get: () => !wide.value && events.detail.value !== null,
  set: (open: boolean) => {
    if (!open) selection.close()
  },
})
const offers = computed(() => events.list.value?.offers ?? [])
const serverEvents = computed(() => events.list.value?.serverEvents ?? [])
const eventNames = computed(() => new Map(serverEvents.value.map(event => [event.id, event.name])))

/**
 * The scope of a server event in words.
 *
 * @param event the event
 * @param event.materials the materials it is limited to
 * @param event.levels the company levels it is limited to
 * @returns its materials and levels, or that it applies to every order
 */
function scopeOf(event: {materials: string[], levels: number[]}): string {
  const parts: string[] = []
  if (event.materials.length > 0) parts.push(t('events.server.materials', {materials: event.materials.join(', ')}))
  if (event.levels.length > 0) parts.push(t('events.server.levels', {levels: event.levels.join(', ')}))
  return parts.length > 0 ? parts.join(' · ') : t('events.server.everything')
}
</script>

<template>
  <ViewContent :title="t('nav.events')">
    <MutedText tag="p">{{ t('events.intro') }}</MutedText>
    <Alert v-if="events.status.value === 'failed'" variant="problem">{{ t('events.failed') }}</Alert>
    <PanelBox :aria-label="t('events.server.title')" data-testid="server-events">
      <template #head>
        <SectionHeader>{{ t('events.server.title') }}</SectionHeader>
      </template>
      <EmptyState v-if="serverEvents.length === 0" :loading="events.status.value === 'loading'">{{ t('events.server.none') }}</EmptyState>
      <PlainList v-else>
        <li v-for="event in serverEvents" :key="event.id" class="flex flex-col gap-1 px-3 py-2 text-sm md:px-4">
          <span class="flex min-w-0 items-center gap-1.5">
            <span class="truncate font-semibold">{{ event.name }}</span>
            <PrimaryBadge>{{ t(`events.server.effect.${event.kind}`, {value: event.value}) }}</PrimaryBadge>
            <span class="ml-auto shrink-0 tabular text-xs">{{ t(`events.time.${offerTime(event, now).key}`, {hours: offerTime(event, now).hours}) }}</span>
          </span>
          <span class="text-[0.6875rem] text-ink-muted">{{ scopeOf(event) }}</span>
          <span v-if="event.budgetLeft != null" class="tabular text-[0.6875rem] text-ink-muted">{{ t('events.server.budgetLeft', {left: money(event.budgetLeft, locale)}) }}</span>
        </li>
      </PlainList>
    </PanelBox>
    <div class="grid items-start gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(20rem,1fr)]">
      <PanelBox tag="div">
        <EmptyState v-if="offers.length === 0" :loading="events.status.value === 'loading'">{{ t('events.none') }}</EmptyState>
        <PlainList v-else data-testid="events">
          <li v-for="offer in offers" :key="offer.id">
            <RowButton :selected="selection.id.value === offer.id" class="flex-col items-stretch gap-2" @click="selection.open(offer.id)">
              <span class="flex min-w-0 items-center gap-2.5">
                <ItemMark :item="{material: offerMaterial(offer.item.icon), name: offer.item.name}"/>
                <span class="flex min-w-0 grow flex-col gap-0.5">
                  <span class="flex min-w-0 items-center gap-1.5">
                    <RowTitle>{{ offer.name }}</RowTitle>
                    <PrimaryBadge>{{ t(`events.kind.${offer.kind}`) }}</PrimaryBadge>
                    <SecondaryBadge v-if="offer.state !== 'active'">{{ t(`events.state.${offer.state}`) }}</SecondaryBadge>
                  </span>
                  <span class="truncate text-[0.6875rem] text-ink-muted">{{ offer.item.name }}</span>
                  <span v-if="offer.event != null && eventNames.has(offer.event)" class="truncate text-[0.6875rem] text-accent">{{ t('events.server.part', {name: eventNames.get(offer.event)}) }}</span>
                </span>
                <span class="flex shrink-0 flex-col items-end gap-0.5 text-xs">
                  <span class="tabular">{{ t(`events.time.${offerTime(offer, now).key}`, {hours: offerTime(offer, now).hours}) }}</span>
                  <span v-if="offer.place" class="tabular text-accent">{{ t('events.place', {place: offer.place}) }}</span>
                  <span v-else-if="offer.joined" class="text-ink-muted">{{ t('events.joined') }}</span>
                </span>
              </span>
              <ProgressBar :value="offer.percent" :label="t('events.progress', {name: offer.name})" tone="accent"/>
              <span class="tabular text-[0.6875rem] text-ink-muted">{{ t('events.delivered', {delivered: count(offer.delivered, locale), amount: count(offer.amount, locale), percent: offer.percent}) }}</span>
            </RowButton>
          </li>
        </PlainList>
      </PanelBox>
      <EventDetail
          v-if="wide && events.detail.value"
          :details="events.detail.value"
          class="sticky top-4"
          @changed="events.changed"
          @stale="events.refresh"
      />
      <MutedText v-else-if="wide && events.detailStatus.value === 'failed'" tag="p">{{ t('events.detailFailed') }}</MutedText>
    </div>
    <BottomSheet v-if="events.detail.value" v-model:open="sheetOpen" :title="events.detail.value.offer.name" :description="t(`events.kind.${events.detail.value.offer.kind}`)">
      <EventDetail :details="events.detail.value" in-sheet @changed="events.changed" @stale="events.refresh"/>
    </BottomSheet>
  </ViewContent>
</template>
