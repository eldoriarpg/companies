/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import {OrderState} from '~/api/generated/protocol'
import type {AdminOrder} from '~/api/gateway'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PanelBox from '@/components/container/PanelBox.vue'
import ActionNotice from '@/components/feedback/ActionNotice.vue'
import Alert from '@/components/feedback/Alert.vue'
import ConfirmStep from '@/components/feedback/ConfirmStep.vue'
import NumberInput from '@/components/input/NumberInput.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import AdminOrderCancel from './AdminOrderCancel.vue'
import AdminOrderFacts from './AdminOrderFacts.vue'
import {money} from '~/util/format'
import {openLabel} from '~/util/labels'

/**
 * One order as an admin sees it: both sides, every line with who delivered what, when it runs out, and what the admin
 * may do to it as `/companyadmin order` does: take a claimed order from its company, delete it with or without a
 * refund, mark a claimed order delivered, or give it more time. Every change but more time asks once more and names
 * what it moves.
 */
const props = defineProps<{
  order: AdminOrder
}>()

const emit = defineEmits<{
  changed: [order: number]
  removed: [text: string]
}>()

const {t, te, locale} = useI18n()
const action = useAction()
const done = ref('')
const hours = ref<number>()
const claimed = computed(() => props.order.state === OrderState.CLAIMED)
const open = computed(() => claimed.value || props.order.state === OrderState.UNCLAIMED)

watch(() => props.order.id, () => {
  done.value = ''
  hours.value = undefined
  action.reset()
})

/** Takes the order from its company. */
async function unclaim(): Promise<void> {
  const result = await action.run('admin.orders.unclaim', {order: props.order.id})
  if (result) done.value = t(result.workers === 1 ? 'admin.orders.unclaimedOne' : 'admin.orders.unclaimed', {count: result.workers})
  emit('changed', props.order.id)
}

/**
 * Deletes the order.
 *
 * @param refund whether the owner gets price and fee back
 */
async function cancel(refund: boolean): Promise<void> {
  const result = await action.run('admin.orders.cancel', {order: props.order.id, refund})
  if (!result) {
    emit('changed', props.order.id)
    return
  }
  if (!result.charged) {
    emit('removed', t('admin.orders.cancelledUncharged', {name: props.order.name}))
    return
  }
  emit('removed', refund
    ? t('admin.orders.cancelledRefund', {name: props.order.name, amount: money(result.refunded, locale.value)})
    : t('admin.orders.cancelledKept', {name: props.order.name, amount: money(result.collected, locale.value)}))
}

/** Marks the order delivered. */
async function deliver(): Promise<void> {
  const result = await action.run('admin.orders.deliver', {order: props.order.id})
  if (result) done.value = t('admin.orders.delivered', {amount: money(result.refund, locale.value)})
  emit('changed', props.order.id)
}

/** Gives the order more time. */
async function extend(): Promise<void> {
  if (hours.value === undefined) return
  const result = await action.run('admin.orders.extend', {order: props.order.id, hours: hours.value})
  if (result) {
    done.value = t(result.hours === 1 ? 'admin.orders.extendedOne' : 'admin.orders.extended', {hours: result.hours})
    hours.value = undefined
  }
  emit('changed', props.order.id)
}
</script>

<template>
  <PanelBox :aria-label="t('admin.orders.detail', {id: order.id})" data-testid="admin-order">
    <template #head>
      <SectionHeader :note="openLabel({t, te}, `orderState.${order.state}`, order.state)">#{{ order.id }} {{ order.name }}</SectionHeader>
    </template>
    <div class="flex flex-col gap-3 p-3 md:p-4">
      <Alert v-if="done" variant="success">{{ done }}</Alert>
      <ActionNotice :problem="action.problem.value"/>
      <AdminOrderFacts :order="order"/>
      <template v-if="open">
        <form class="flex flex-wrap items-end gap-2" @submit.prevent="extend">
          <NumberInput v-model="hours" :label="t('admin.orders.hours')" :min="1" :max="8760"/>
          <SecondaryButton type="submit" :disabled="hours === undefined || action.busy.value">{{ t('admin.orders.extend') }}</SecondaryButton>
        </form>
        <ConfirmStep
            v-if="claimed"
            :trigger="t('admin.orders.unclaim')"
            :question="t('admin.orders.unclaimQuestion', {name: order.name})"
            :confirm="t('admin.orders.unclaimConfirm')"
            :busy="action.busy.value"
            @confirmed="unclaim"
        />
        <ConfirmStep
            v-if="claimed"
            :trigger="t('admin.orders.deliver')"
            :question="t('admin.orders.deliverQuestion', {name: order.name, amount: money(order.refundOnDeliver, locale)})"
            :confirm="t('admin.orders.deliverConfirm')"
            :busy="action.busy.value"
            @confirmed="deliver"
        />
        <AdminOrderCancel :order="order" :busy="action.busy.value" @cancel="cancel"/>
      </template>
    </div>
  </PanelBox>
</template>
