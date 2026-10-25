/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {AdminPlayer} from '~/api/gateway'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PanelBox from '@/components/container/PanelBox.vue'
import ActionNotice from '@/components/feedback/ActionNotice.vue'
import Alert from '@/components/feedback/Alert.vue'
import DecimalInput from '@/components/input/DecimalInput.vue'
import NumberInput from '@/components/input/NumberInput.vue'
import TextInput from '@/components/input/TextInput.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import AdminPlayerFacts from './AdminPlayerFacts.vue'

/**
 * A player for an admin, as `/companyadmin player` and `grant` show them: their company and role, their own orders,
 * the items waiting for them and their latest messages; and money or items granted to them, the items waiting for
 * `/order collect`. Each needs the permission of its command.
 */
const {t} = useI18n()
const looking = useAction()
const granting = useAction()
const name = ref('')
const player = ref<AdminPlayer | null>(null)
const money = ref<number>()
const material = ref('')
const amount = ref<number>()
const done = ref('')

/** Looks the typed player up. */
async function lookup(): Promise<void> {
  done.value = ''
  granting.reset()
  const result = await looking.run('admin.players.lookup', {player: name.value.trim()})
  if (result) player.value = result
}

/** Deposits money for the shown player. */
async function grantMoney(): Promise<void> {
  if (!player.value || money.value === undefined) return
  const result = await granting.run('admin.players.grant-money', {player: player.value.player.name, amount: money.value})
  if (!result) return
  done.value = t('admin.players.grantedMoney', {amount: result.amount, name: result.player})
  money.value = undefined
}

/** Stores items for the shown player. */
async function grantItems(): Promise<void> {
  if (!player.value || amount.value === undefined || material.value.trim() === '') return
  const result = await granting.run('admin.players.grant-items', {player: player.value.player.name, material: material.value.trim(), amount: amount.value})
  if (!result) return
  done.value = t('admin.players.grantedItems', {amount: result.amount, name: result.player})
  amount.value = undefined
}
</script>

<template>
  <div class="grid items-start gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.4fr)]">
    <PanelBox padded :aria-label="t('admin.players.find')">
      <SectionHeader>{{ t('admin.players.find') }}</SectionHeader>
      <form class="flex flex-wrap items-end gap-2" @submit.prevent="lookup">
        <TextInput v-model="name" :label="t('admin.orders.playerName')"/>
        <SecondaryButton type="submit" :disabled="name.trim() === '' || looking.busy.value">{{ t('admin.players.lookup') }}</SecondaryButton>
      </form>
      <ActionNotice :problem="looking.problem.value"/>
    </PanelBox>
    <PanelBox v-if="player" padded :aria-label="player.player.name" data-testid="admin-player">
      <SectionHeader>{{ player.player.name }}</SectionHeader>
      <AdminPlayerFacts :player="player"/>
      <Alert v-if="done" variant="success">{{ done }}</Alert>
      <ActionNotice :problem="granting.problem.value"/>
      <form class="flex flex-wrap items-end gap-2" @submit.prevent="grantMoney">
        <DecimalInput v-model="money" :label="t('admin.players.money')" :min="0.01"/>
        <SecondaryButton type="submit" :disabled="money === undefined || granting.busy.value">{{ t('admin.players.grantMoney') }}</SecondaryButton>
      </form>
      <form class="flex flex-wrap items-end gap-2" @submit.prevent="grantItems">
        <TextInput v-model="material" :label="t('admin.players.material')" placeholder="minecraft:oak_log"/>
        <NumberInput v-model="amount" :label="t('admin.players.amount')" :min="1" :max="100000"/>
        <SecondaryButton type="submit" :disabled="amount === undefined || material.trim() === '' || granting.busy.value">{{ t('admin.players.grantItems') }}</SecondaryButton>
      </form>
    </PanelBox>
  </div>
</template>
