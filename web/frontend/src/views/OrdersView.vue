/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import {useMediaQuery} from '@vueuse/core'
import ButtonLink from '@/components/button/ButtonLink.vue'
import BottomSheet from '@/components/feedback/BottomSheet.vue'
import Alert from '@/components/feedback/Alert.vue'
import ViewContent from '@/components/layout/ViewContent.vue'
import PageSwitcher from '@/components/navigation/PageSwitcher.vue'
import OrderDetail from './ordersview/OrderDetail.vue'
import OrderFilters from './ordersview/OrderFilters.vue'
import OrderList from './ordersview/OrderList.vue'
import {Feature} from '~/api/generated/protocol'
import {orderIdOf} from '~/composables/useSelection'

/**
 * The order book: the open orders of the server, searched and filtered by the plugin, a page at a time. An order's
 * details open beside the list from tablet width and as a sheet on a phone; the address names the open order, so
 * the back gesture closes it. The owner of an order is never shown. The address may name an event offer, from its page
 * on the Events page, to show only its chunks.
 */
const {t} = useI18n()
const book = useOrderBook()
const market = useMarket()
const selection = useSelection('order', orderIdOf)
const wide = useMediaQuery('(min-width: 1024px)')
const {supports} = useDashboard()
const route = useRoute()
const router = useRouter()

watch(() => route.query.offer, raw => {
  book.offer.value = typeof raw === 'string' ? orderIdOf(raw) : null
}, {immediate: true})
const offerName = computed(() => {
  const chosen = book.offer.value
  if (chosen === null) return null
  return book.result.value?.orders.find(order => order.offer?.id === chosen)?.offer?.name ?? `#${chosen}`
})

/** Shows every order again instead of the chunks of one event offer. */
function clearOffer(): void {
  void router.replace({query: {...route.query, offer: undefined}})
}

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
    <div v-if="supports(Feature['orders.create'])" class="flex justify-end">
      <ButtonLink :to="{name: 'order-new'}" icon="plus">{{ t('builder.newOrder') }}</ButtonLink>
    </div>
    <OrderFilters v-model:query="book.query.value" v-model:filter="book.filter.value" v-model:sort="book.sort.value" :total="book.result.value?.total" :offer-name="offerName" @clear-offer="clearOffer"/>
    <Alert v-if="book.status.value === 'failed'" variant="problem">{{ t('orders.failed') }}</Alert>
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
      <OrderDetail v-if="wide && book.detail.value" :order="book.detail.value" :averages="market.averages.value" :can-accept="book.result.value?.canAccept ?? false" class="sticky top-4" @accepted="book.load()"/>
    </div>
    <BottomSheet v-if="book.detail.value" v-model:open="sheetOpen" :title="book.detail.value.name" :description="book.detail.value.server && supports(Feature['orders.servermarks']) ? t('orders.byServer') : t('orders.anonymous')">
      <OrderDetail :order="book.detail.value" :averages="market.averages.value" :can-accept="book.result.value?.canAccept ?? false" in-sheet @accepted="book.load()"/>
    </BottomSheet>
  </ViewContent>
</template>
