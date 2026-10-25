/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {AdminPlayer} from '~/api/gateway'
import MutedText from '@/components/typography/MutedText.vue'
import {count, dateTime} from '~/util/format'
import {openLabel} from '~/util/labels'

/** What an admin learns about a player: company and role, own orders, waiting items and the latest messages. */
defineProps<{
  player: AdminPlayer
}>()

const {t, te, locale} = useI18n()
</script>

<template>
  <div class="flex flex-col gap-3">
    <dl class="m-0 grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-sm">
      <dt class="text-ink-muted">{{ t('admin.orders.company') }}</dt>
      <dd class="m-0">{{ player.company ?? t('admin.players.noCompany') }}<template v-if="player.owner"> · {{ t('admin.players.owner') }}</template></dd>
      <template v-if="player.permissions.length > 0">
        <dt class="text-ink-muted">{{ t('admin.players.permissions') }}</dt>
        <dd class="m-0">{{ player.permissions.map(permission => openLabel({t, te}, `permission.${permission}`, permission)).join(', ') }}</dd>
      </template>
      <dt class="text-ink-muted">{{ t('admin.players.waiting') }}</dt>
      <dd class="tabular m-0">{{ t('admin.players.waitingItems', {worker: count(player.workerItems, locale), owner: count(player.ownerItems, locale)}) }}</dd>
      <dt class="text-ink-muted">{{ t('admin.players.unread') }}</dt>
      <dd class="tabular m-0">{{ count(player.unread, locale) }}</dd>
    </dl>
    <section class="flex flex-col gap-1">
      <span class="text-xs font-semibold">{{ t('admin.players.orders') }}</span>
      <MutedText v-if="player.orders.length === 0" tag="p">{{ t('admin.players.noOrders') }}</MutedText>
      <ul v-else class="m-0 flex list-none flex-col gap-0.5 p-0 text-sm">
        <li v-for="order in player.orders" :key="order.id">#{{ order.id }} {{ order.name }} · {{ openLabel({t, te}, `orderState.${order.state}`, order.state) }}</li>
      </ul>
    </section>
    <section class="flex flex-col gap-1">
      <span class="text-xs font-semibold">{{ t('admin.players.latest') }}</span>
      <MutedText v-if="player.latest.length === 0" tag="p">{{ t('admin.players.noMessages') }}</MutedText>
      <ul v-else class="m-0 flex list-none flex-col gap-1 p-0 text-sm">
        <li v-for="entry in player.latest" :key="entry.id"><span class="tabular text-xs text-ink-muted">{{ dateTime(entry.created, locale) }}</span> {{ entry.text }}</li>
      </ul>
    </section>
  </div>
</template>
