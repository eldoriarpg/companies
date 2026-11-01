/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {MarketItemView} from '~/api/gateway'
import RowButton from '@/components/button/RowButton.vue'
import PanelBox from '@/components/container/PanelBox.vue'
import PlainList from '@/components/container/PlainList.vue'
import ItemMark from '@/components/display/ItemMark.vue'
import TrendText from '@/components/display/TrendText.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import CapsLabel from '@/components/typography/CapsLabel.vue'
import {compact, money, percentChange, trendOf} from '~/util/format'

/**
 * The materials of the market as ticker rows: the average price per item, the lowest and highest, the volume, the
 * change against the week before where both weeks have enough orders, and the price of the server's shops, marked
 * where order prices go by it. Materials only the shops price come last. A row opens the material's figures. Without
 * items it says why, in the caller's words where given.
 */
defineProps<{
  items: readonly MarketItemView[]
  loading: boolean
  selected: string | null
  empty?: string
}>()

defineEmits<{
  open: [material: string]
}>()

const {t, locale} = useI18n()
const COLUMNS = 'md:grid md:grid-cols-[minmax(0,2fr)_5rem_5rem_5rem_4rem_4.5rem_5rem] md:gap-3'

/**
 * A price, or a dash where too few orders back one.
 *
 * @param value the price
 * @returns the price as text
 */
function price(value: number | undefined): string {
  return value === undefined ? '-' : money(value, locale.value)
}
</script>

<template>
  <PanelBox tag="div">
    <div :class="COLUMNS" class="hidden border-b md:grid border-line-soft px-3 py-2" aria-hidden="true">
      <CapsLabel>{{ t('market.columnItem') }}</CapsLabel>
      <CapsLabel class="text-right">{{ t('market.columnAverage') }}</CapsLabel>
      <CapsLabel class="text-right">{{ t('market.columnLow') }}</CapsLabel>
      <CapsLabel class="text-right">{{ t('market.columnHigh') }}</CapsLabel>
      <CapsLabel class="text-right">{{ t('market.columnVolume') }}</CapsLabel>
      <CapsLabel class="text-right">{{ t('market.columnChange') }}</CapsLabel>
      <CapsLabel class="text-right">{{ t('market.columnShop') }}</CapsLabel>
    </div>
    <EmptyState v-if="items.length === 0" :loading="loading">{{ empty ?? t('market.none') }}</EmptyState>
    <PlainList v-else :aria-busy="loading" data-testid="market">
      <li v-for="entry in items" :key="entry.item.material">
        <RowButton :selected="selected === entry.item.material" :class="COLUMNS" @click="$emit('open', entry.item.material)">
          <span class="flex min-w-0 grow items-center gap-2.5">
            <ItemMark :item="entry.item"/>
            <span class="flex min-w-0 flex-col gap-0.5">
              <span class="truncate text-sm font-semibold">{{ entry.item.name }}</span>
              <span class="tabular truncate text-[0.6875rem] text-ink-muted md:hidden">
                {{ t('market.rangeLine', {low: price(entry.min), high: price(entry.max), volume: entry.volume === undefined ? '-' : compact(entry.volume, locale)}) }}
              </span>
            </span>
          </span>
          <span class="tabular w-20 shrink-0 text-right text-sm font-semibold md:w-auto">{{ price(entry.avg) }}</span>
          <span class="tabular hidden text-right text-sm text-ink-soft md:block">{{ price(entry.min) }}</span>
          <span class="tabular hidden text-right text-sm text-ink-soft md:block">{{ price(entry.max) }}</span>
          <span class="tabular hidden text-right text-sm text-ink-soft md:block">{{ entry.volume === undefined ? '-' : compact(entry.volume, locale) }}</span>
          <TrendText v-if="entry.change !== undefined" :trend="trendOf(entry.change)" class="w-16 shrink-0 text-right text-xs md:w-auto md:text-sm">{{ percentChange(entry.change, locale) }}</TrendText>
          <span v-else class="tabular w-16 shrink-0 text-right text-xs text-ink-muted md:w-auto">-</span>
          <span
              class="tabular hidden text-right text-sm md:block"
              :class="entry.shop?.used ? 'font-semibold' : 'text-ink-soft'"
              :title="entry.shop ? t(entry.shop.used ? 'market.shopUsed' : 'market.shopNotUsed') : undefined"
          >{{ entry.shop ? price(entry.shop.price) : '-' }}</span>
        </RowButton>
      </li>
    </PlainList>
  </PanelBox>
</template>
