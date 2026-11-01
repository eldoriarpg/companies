/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {AdminOrder} from '~/api/gateway'
import ConfirmStep from '@/components/feedback/ConfirmStep.vue'
import {money} from '~/util/format'

/**
 * The ways to delete an order, each asking once more: with a refund to the owner or with price and fee for the
 * collector, or, for an order created without charging its owner, a plain delete that moves no money.
 */
defineProps<{
  order: AdminOrder
  busy: boolean
}>()

defineEmits<{
  cancel: [refund: boolean]
}>()

const {t, locale} = useI18n()
</script>

<template>
  <ConfirmStep
      v-if="!order.charged"
      :trigger="t('admin.orders.cancelUncharged')"
      :question="t('admin.orders.cancelUnchargedQuestion', {name: order.name})"
      :confirm="t('admin.orders.cancelConfirm')"
      :busy="busy"
      @confirmed="$emit('cancel', false)"
  />
  <template v-else>
    <ConfirmStep
        :trigger="t('admin.orders.cancelRefund')"
        :question="t('admin.orders.cancelRefundQuestion', {name: order.name, amount: money(order.refundOnCancel, locale)})"
        :confirm="t('admin.orders.cancelConfirm')"
        :busy="busy"
        @confirmed="$emit('cancel', true)"
    />
    <ConfirmStep
        :trigger="t('admin.orders.cancelKeep')"
        :question="t('admin.orders.cancelKeepQuestion', {name: order.name, amount: money(order.keptOnCancel, locale)})"
        :confirm="t('admin.orders.cancelConfirm')"
        :busy="busy"
        @confirmed="$emit('cancel', false)"
    />
  </template>
</template>
