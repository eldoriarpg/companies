/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import {Feature} from '~/api/generated/protocol'
import ChipButton from '@/components/button/ChipButton.vue'
import SearchInput from '@/components/input/SearchInput.vue'
import SelectInput from '@/components/input/SelectInput.vue'
import {ORDER_SORTINGS, type OrderFilter, type OrderSorting} from '~/composables/useOrderBook'

/** The search of the order book: the order's name, the quick filters and the sorting of the in-game search. */
const query = defineModel<string>('query', {required: true})
const filter = defineModel<OrderFilter>('filter', {required: true})
const sort = defineModel<OrderSorting>('sort', {required: true})

defineProps<{
  total?: number
}>()

const {t} = useI18n()
const {supports} = useDashboard()
const filters = computed<readonly OrderFilter[]>(() => (supports(Feature['orders.servermarks'])
  ? ['all', 'small', 'large-payout', 'players', 'server']
  : ['all', 'small', 'large-payout']))
const sortings = computed(() => ORDER_SORTINGS.map(value => ({value, label: t(`orders.sort.${value}`)})))
const sortChoice = computed({
  get: () => sort.value as string,
  set: (value: string) => {
    sort.value = value as OrderSorting
  },
})
</script>

<template>
  <div class="flex flex-col gap-2 md:flex-row md:flex-wrap md:items-center">
    <div class="md:w-80">
      <SearchInput v-model="query" :label="t('orders.search')" :placeholder="total === undefined ? t('orders.search') : t('orders.searchCount', {count: total})"/>
    </div>
    <div class="flex gap-1.5 overflow-x-auto" role="group" :aria-label="t('orders.filters')">
      <ChipButton v-for="choice in filters" :key="choice" :pressed="filter === choice" @click="filter = choice">{{ t(`orders.filter.${choice}`) }}</ChipButton>
    </div>
    <SelectInput v-model="sortChoice" :label="t('orders.sortBy')" :options="sortings" class="md:ml-auto"/>
  </div>
</template>
