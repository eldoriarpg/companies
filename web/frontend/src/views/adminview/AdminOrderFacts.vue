/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {AdminOrder} from '~/api/gateway'
import ItemMark from '@/components/display/ItemMark.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {count, dateTime, money} from '~/util/format'

/** What an admin knows about an order: both sides, when it was placed and runs out, its price, and every line. */
defineProps<{
  order: AdminOrder
}>()

const {t, locale} = useI18n()
</script>

<template>
  <div class="flex flex-col gap-3">
  <dl class="m-0 grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-sm">
    <dt class="text-ink-muted">{{ t('admin.orders.owner') }}</dt>
    <dd class="m-0">{{ order.owner.name }}</dd>
    <template v-if="order.company">
      <dt class="text-ink-muted">{{ t('admin.orders.company') }}</dt>
      <dd class="m-0">{{ order.company }}</dd>
    </template>
    <template v-if="order.soloWorker">
      <dt class="text-ink-muted">{{ t('admin.orders.soloWorker') }}</dt>
      <dd class="m-0">{{ order.soloWorker }}</dd>
    </template>
    <dt class="text-ink-muted">{{ t('admin.orders.created') }}</dt>
    <dd class="tabular m-0">{{ dateTime(order.created, locale) }}</dd>
    <template v-if="order.deadline">
      <dt class="text-ink-muted">{{ order.removal ? t('admin.orders.removedAt') : t('admin.orders.dueAt') }}</dt>
      <dd class="tabular m-0">{{ dateTime(order.deadline, locale) }}</dd>
    </template>
    <dt class="text-ink-muted">{{ t('admin.orders.price') }}</dt>
    <dd class="tabular m-0">{{ money(order.price, locale) }} + {{ money(order.fee, locale) }}</dd>
  </dl>
  <ul class="m-0 flex list-none flex-col p-0">
    <li v-for="(line, index) in order.lines" :key="index" class="flex flex-col gap-1 border-t border-line-soft py-2">
      <div class="flex items-center gap-2">
        <ItemMark :item="line.item" size="small"/>
        <span class="grow truncate text-sm">{{ line.item.name }}</span>
        <span class="tabular text-sm">{{ count(line.delivered, locale) }} / {{ count(line.item.amount, locale) }}</span>
      </div>
      <MutedText v-if="Object.keys(line.workers).length > 0">
        {{ Object.entries(line.workers).map(([name, amount]) => `${name} ${count(amount, locale)}`).join(', ') }}
      </MutedText>
    </li>
  </ul>
  </div>
</template>
