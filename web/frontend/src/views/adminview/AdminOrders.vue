/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import PlainList from '@/components/container/PlainList.vue'
import type {AdminOrder, AdminOrderPage} from '~/api/gateway'
import ButtonRow from '@/components/button/ButtonRow.vue'
import LinkButton from '@/components/button/LinkButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PanelBox from '@/components/container/PanelBox.vue'
import ActionNotice from '@/components/feedback/ActionNotice.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import NumberInput from '@/components/input/NumberInput.vue'
import SelectInput from '@/components/input/SelectInput.vue'
import TextInput from '@/components/input/TextInput.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import AdminOrderDetail from './AdminOrderDetail.vue'
import {dateTime} from '~/util/format'
import {openLabel} from '~/util/labels'

/**
 * Any order of the server for an admin: opened by its id, or found among the orders of a player or a company, a page
 * at a time. An opened order shows everything about it and what the admin may do to it.
 */
const {t, te, locale} = useI18n()
const finding = useAction()
const opening = useAction()
const by = ref<'player' | 'company'>('player')
const query = ref('')
const id = ref<number>()
const page = ref<AdminOrderPage | null>(null)
const searched = ref<OrderSearch | null>(null)
const order = ref<AdminOrder | null>(null)
const removed = ref('')
const reloading = useAction()
const choices = computed(() => [
  {value: 'player', label: t('admin.orders.byPlayer')},
  {value: 'company', label: t('admin.orders.byCompany')},
])

/**
 * Finds the orders of the player or company typed in.
 *
 * @param number the page, from one
 */
async function find(number = 1): Promise<void> {
  const name = query.value.trim()
  if (name === '') return
  await show({by: by.value, name}, number)
}

/**
 * Shows a page of the last search, whatever is typed meanwhile.
 *
 * @param number the page, from one
 */
async function turn(number: number): Promise<void> {
  if (searched.value) await show(searched.value, number)
}

/**
 * Runs a search and keeps it as the shown one once it answered; a page left empty by a deletion falls back to the one
 * before.
 *
 * @param search the search
 * @param number the page, from one
 */
async function show(search: OrderSearch, number: number): Promise<void> {
  const result = await finding.run('admin.orders.search', search.by === 'player' ? {player: search.name, page: number} : {company: search.name, page: number})
  if (!result) return
  searched.value = search
  if (result.orders.length === 0 && number > 1) {
    await show(search, number - 1)
    return
  }
  page.value = result
}

/**
 * Loads an order again after it was changed, and the page of the search with it, so both show the order as it is now.
 * An order the plugin no longer knows is closed; one that could not be loaded right now stays as it was shown.
 *
 * @param id the order's id
 */
async function refresh(id: number): Promise<void> {
  const result = await reloading.run('admin.orders.info', {order: id})
  if (result) setOrder(result)
  else if (reloading.problem.value?.kind === 'refused') setOrder(null)
  if (page.value) await turn(page.value.page)
}

/**
 * Closes a deleted order and says what happened to it.
 *
 * @param text what happened
 */
async function deleted(text: string): Promise<void> {
  setOrder(null)
  removed.value = text
  if (page.value) await turn(page.value.page)
}

/**
 * Opens an order.
 *
 * @param order the order's id
 */
async function open(order: number): Promise<void> {
  removed.value = ''
  const result = await opening.run('admin.orders.info', {order})
  if (result) setOrder(result)
}

/**
 * Shows an order, or closes it.
 *
 * @param next the order, none to close it
 */
function setOrder(next: AdminOrder | null): void {
  order.value = next
}

/** The search the shown page belongs to. */
interface OrderSearch {
  by: 'player' | 'company'
  name: string
}
</script>

<template>
  <div class="grid items-start gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.4fr)]">
    <div class="flex min-w-0 flex-col gap-4">
      <PanelBox padded :aria-label="t('admin.orders.open')">
        <SectionHeader>{{ t('admin.orders.open') }}</SectionHeader>
        <form class="flex flex-wrap items-end gap-2" @submit.prevent="id !== undefined && open(id)">
          <NumberInput v-model="id" :label="t('admin.orders.id')" :min="1"/>
          <SecondaryButton type="submit" :disabled="id === undefined || opening.busy.value">{{ t('admin.orders.openButton') }}</SecondaryButton>
        </form>
        <ActionNotice :problem="opening.problem.value"/>
      </PanelBox>
      <PanelBox :aria-label="t('admin.orders.find')">
        <template #head>
          <SectionHeader>{{ t('admin.orders.find') }}</SectionHeader>
        </template>
        <form class="flex flex-wrap items-end gap-2 px-3 py-3 md:px-4" @submit.prevent="find()">
          <SelectInput v-model="by" :label="t('admin.orders.by')" :options="choices"/>
          <TextInput v-model="query" :label="by === 'player' ? t('admin.orders.playerName') : t('admin.orders.companyName')"/>
          <SecondaryButton type="submit" :disabled="query.trim() === '' || finding.busy.value">{{ t('admin.orders.findButton') }}</SecondaryButton>
        </form>
        <ActionNotice :problem="finding.problem.value" class="mx-3 mb-3 md:mx-4"/>
        <template v-if="page">
          <EmptyState v-if="page.orders.length === 0">{{ t('admin.orders.none', {target: page.target}) }}</EmptyState>
          <PlainList v-else data-testid="admin-orders">
            <li v-for="row in page.orders" :key="row.id" class="flex flex-col gap-0.5 border-t border-line-soft px-3 py-2.5 md:px-4">
              <LinkButton class="truncate text-sm" @click="open(row.id)">#{{ row.id }} {{ row.name }}</LinkButton>
              <span class="text-xs text-ink-muted">
                {{ openLabel({t, te}, `orderState.${row.state}`, row.state) }} · {{ row.owner }}<template v-if="row.claimant"> · {{ row.claimant }}</template> · {{ dateTime(row.created, locale) }}
              </span>
            </li>
          </PlainList>
          <ButtonRow v-if="page.page > 1 || page.hasNext" pair class="border-t border-line-soft px-3 py-2.5 md:px-4">
            <SecondaryButton :disabled="page.page <= 1 || finding.busy.value" @click="turn(page.page - 1)">{{ t('paging.previous') }}</SecondaryButton>
            <SecondaryButton :disabled="!page.hasNext || finding.busy.value" @click="turn(page.page + 1)">{{ t('paging.next') }}</SecondaryButton>
          </ButtonRow>
        </template>
      </PanelBox>
    </div>
    <div class="flex min-w-0 flex-col gap-4">
      <Alert v-if="removed" variant="success">{{ removed }}</Alert>
      <ActionNotice :problem="reloading.problem.value"/>
      <AdminOrderDetail v-if="order" :order="order" @changed="refresh" @removed="deleted"/>
    </div>
  </div>
</template>
