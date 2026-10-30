/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import {useNow} from '@vueuse/core'
import type {EventRow, MarketItemView, ServerEventView} from '~/api/gateway'
import TrendText from '@/components/display/TrendText.vue'
import {money, percentChange, trendOf} from '~/util/format'
import {offerTime} from '~/util/events'

/**
 * The price ticker across the top: the server events that run with their effect and time left, the event offers that
 * run, each with its progress and time left, each linking to the Events page or its own page, then the market's most
 * ordered items with their average price and, once the plugin keeps price history, their change. It scrolls sideways
 * by touch; nothing moves on its own.
 */
withDefaults(defineProps<{
  items: readonly MarketItemView[]
  offers?: readonly EventRow[]
  serverEvents?: readonly ServerEventView[]
}>(), {offers: () => [], serverEvents: () => []})

const {t, locale} = useI18n()
const now = useNow({interval: 60_000})
</script>

<template>
  <section
      :aria-label="t('ticker.label')"
      class="tabular flex gap-6 overflow-x-auto whitespace-nowrap border-b border-line-soft bg-deep px-4 py-2 text-xs md:px-6"
  >
    <NuxtLink
        v-for="event in serverEvents"
        :key="`server-event-${event.id}`"
        :to="{name: 'events'}"
        class="flex gap-1.5 text-ink no-underline"
        data-testid="ticker-server-event"
    >
      <span class="font-semibold text-accent">{{ t('ticker.event') }}</span>
      <span class="text-ink-muted">{{ event.name }}</span>
      <span>{{ t(`events.server.effect.${event.kind}`, {value: event.value}) }}</span>
      <span class="text-ink-muted">{{ t(`events.time.${offerTime(event, now).key}`, {hours: offerTime(event, now).hours}) }}</span>
    </NuxtLink>
    <NuxtLink
        v-for="offer in offers"
        :key="`offer-${offer.id}`"
        :to="{name: 'events', query: {offer: String(offer.id)}}"
        class="flex gap-1.5 text-ink no-underline"
        data-testid="ticker-offer"
    >
      <span class="font-semibold text-accent">{{ t('ticker.event') }}</span>
      <span class="text-ink-muted">{{ offer.name }}</span>
      <span>{{ offer.percent }} %</span>
      <span class="text-ink-muted">{{ t(`events.time.${offerTime(offer, now).key}`, {hours: offerTime(offer, now).hours}) }}</span>
    </NuxtLink>
    <span v-for="entry in items" :key="entry.item.material" class="flex gap-1.5">
      <span class="text-ink-muted">{{ entry.item.name }}</span>
      <span>{{ entry.avg === undefined ? t('market.noPrice') : money(entry.avg, locale) }}</span>
      <TrendText v-if="entry.change !== undefined" :trend="trendOf(entry.change)">{{ percentChange(entry.change, locale) }}</TrendText>
    </span>
  </section>
</template>
