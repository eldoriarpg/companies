/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {Snapshot} from '~/api/generated/protocol'
import FigureValue from '@/components/display/FigureValue.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {openLabel} from '~/util/labels'

/** The player's key figures in tiles: the company's level and rank, unread messages and their own orders. */
const props = defineProps<{
  snapshot: Snapshot
}>()

const {t, te} = useI18n()

const waiting = computed(() => props.snapshot.orders.filter(order => order.waiting.length > 0).length)
const tiles = computed(() => {
  const company = props.snapshot.company
  const own = props.snapshot.rankings.own
  return [
    company && {id: 'level', label: t('portfolio.level'), value: String(company.level.level), note: company.level.name},
    own && {id: 'rank', label: t('portfolio.rank'), value: `#${own.place}`, note: openLabel({t, te}, `rankings.kind.${props.snapshot.rankings.ranking}`, props.snapshot.rankings.ranking)},
    {id: 'inbox', label: t('portfolio.unread'), value: String(props.snapshot.player.unreadInbox), note: t('portfolio.unreadNote')},
    {id: 'orders', label: t('portfolio.ownOrders'), value: String(props.snapshot.orders.length), note: t('portfolio.waiting', {count: waiting.value})},
  ].filter(tile => Boolean(tile)) as {id: string, label: string, value: string, note: string}[]
})
</script>

<template>
  <div class="grid grid-cols-2 gap-3">
    <div v-for="tile in tiles" :key="tile.id" class="border border-line bg-surface p-3">
      <FigureValue :label="tile.label" size="large">
        {{ tile.value }}
        <template #note>
          <MutedText>{{ tile.note }}</MutedText>
        </template>
      </FigureValue>
    </div>
  </div>
</template>
