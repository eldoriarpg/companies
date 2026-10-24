/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import {useMediaQuery} from '@vueuse/core'
import BottomSheet from '@/components/feedback/BottomSheet.vue'
import NoticeBanner from '@/components/feedback/NoticeBanner.vue'
import ViewContent from '@/components/layout/ViewContent.vue'
import PageSwitcher from '@/components/navigation/PageSwitcher.vue'
import OrderDetail from './ordersview/OrderDetail.vue'
import OrderFilters from './ordersview/OrderFilters.vue'
import OrderList from './ordersview/OrderList.vue'
import {orderIdOf} from '~/composables/useSelection'

/**
 * The order book: the open orders of the server, searched and filtered by the plugin, a page at a time. An order's
 * details open beside the list from tablet width and as a sheet on a phone; the address names the open order, so
 * the back gesture closes it. The owner of an order is never shown.
 */
const {t} = useI18n()
const book = useOrderBook()
const market = useMarket()
const selection = useSelection('order', orderIdOf)
const wide = useMediaQuery('(min-width: 1024px)')

onMounted(() => {
  void book.load()
  void market.loadTop()
})
watch(selection.id, id => void book.open(id), {immediate: true})

const sheetOpen = computed({
  get: () => !wide.value && book.detail.value !== null,
  set: (open: boolean) => {
    if (!open) selection.close()
  },
})
</script>

<template>
  <ViewContent :title="t('nav.orders')">
    <OrderFilters v-model:query="book.query.value" v-model:filter="book.filter.value" v-model:sort="book.sort.value" :total="book.result.value?.total"/>
    <NoticeBanner v-if="book.status.value === 'failed'" tone="problem">{{ t('orders.failed') }}</NoticeBanner>
    <div class="grid items-start gap-4 lg:grid-cols-[minmax(0,2fr)_minmax(18rem,1fr)]">
      <div class="flex min-w-0 flex-col gap-3">
        <OrderList
            :orders="book.result.value?.orders ?? []"
            :loading="book.status.value === 'loading'"
            :averages="market.averages.value"
            :selected="selection.id.value"
            @open="selection.open"
        />
        <PageSwitcher v-model:page="book.page.value" :pages="book.result.value?.pages ?? 1"/>
      </div>
      <OrderDetail v-if="wide && book.detail.value" :order="book.detail.value" :averages="market.averages.value" class="sticky top-4"/>
    </div>
    <BottomSheet v-if="book.detail.value" v-model:open="sheetOpen" :title="book.detail.value.name" :description="t('orders.anonymous')">
      <OrderDetail :order="book.detail.value" :averages="market.averages.value" in-sheet/>
    </BottomSheet>
  </ViewContent>
</template>
