/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import {useMediaQuery} from '@vueuse/core'
import BottomSheet from '@/components/feedback/BottomSheet.vue'
import NoticeBanner from '@/components/feedback/NoticeBanner.vue'
import SearchInput from '@/components/input/SearchInput.vue'
import ViewContent from '@/components/layout/ViewContent.vue'
import MutedText from '@/components/typography/MutedText.vue'
import MarketDetail from './marketview/MarketDetail.vue'
import MarketList from './marketview/MarketList.vue'

/**
 * The market: the price statistics of the materials, the most ordered first, searched by the plugin. A material's
 * figures open beside the list from tablet width and as a sheet on a phone. Prices of materials with too few orders
 * stay hidden, so no single order can be traced back; price history and its charts come with the plugin's history.
 */
const {t} = useI18n()
const market = useMarket()
const selection = useSelection('item', raw => raw)
const wide = useMediaQuery('(min-width: 1024px)')

onMounted(() => void market.loadTop())

const selected = computed(() => market.items.value.find(entry => entry.item.material === selection.id.value) ?? null)
const sheetOpen = computed({
  get: () => !wide.value && selected.value !== null,
  set: (open: boolean) => {
    if (!open) selection.close()
  },
})
</script>

<template>
  <ViewContent :title="t('nav.market')">
    <div class="md:w-80">
      <SearchInput v-model="market.query.value" :label="t('market.search')"/>
    </div>
    <NoticeBanner v-if="market.status.value === 'failed'" tone="problem">{{ t('market.failed') }}</NoticeBanner>
    <div class="grid items-start gap-4 lg:grid-cols-[minmax(0,2fr)_minmax(18rem,1fr)]">
      <MarketList :items="market.items.value" :loading="market.status.value === 'loading' || market.searching.value" :selected="selection.id.value" @open="selection.open"/>
      <MarketDetail v-if="wide && selected" :entry="selected" class="sticky top-4"/>
    </div>
    <MutedText tag="p">{{ t('market.privacy') }}</MutedText>
    <BottomSheet v-if="selected" v-model:open="sheetOpen" :title="selected.item.name">
      <MarketDetail :entry="selected" in-sheet/>
    </BottomSheet>
  </ViewContent>
</template>
