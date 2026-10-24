/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ActionNotice from '@/components/feedback/ActionNotice.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {count} from '~/util/format'

/**
 * Receives a delivered own order into the player's inventory, as `/order receive` does: what fits goes in, the rest
 * waits to be collected. It needs the player online on the server; while the player is not, it says so instead. The
 * order leaves the list through its patch, so what was received is told to the panel, which outlives the row.
 */
const props = defineProps<{
  order: number
  name: string
}>()

const emit = defineEmits<{
  received: [text: string]
}>()

const {t, locale} = useI18n()
const {playerOnline} = useDashboard()
const action = useAction()

/** Receives the order. */
async function receive(): Promise<void> {
  const result = await action.run('orders.deliver.receive', {order: props.order})
  if (!result) return
  emit('received', result.waiting > 0
    ? t('receive.partly', {name: props.name, waiting: count(result.waiting, locale.value)})
    : t('receive.done', {name: props.name}))
}
</script>

<template>
  <div class="flex flex-col gap-2">
    <ActionNotice :problem="action.problem.value"/>
    <MutedText v-if="!playerOnline" tag="p">{{ t('receive.joinFirst') }}</MutedText>
    <ButtonRow v-else>
      <PrimaryButton :disabled="action.busy.value" @click="receive">{{ t('receive.button') }}</PrimaryButton>
    </ButtonRow>
  </div>
</template>
