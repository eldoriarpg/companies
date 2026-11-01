/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {ClaimedOrderView} from '~/api/generated/protocol'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ActionNotice from '@/components/feedback/ActionNotice.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {count} from '~/util/format'
import {lineName} from '~/util/orders'

/**
 * Delivers to an order the company works on from the player's inventory, exactly as `/company order deliver` does:
 * everything the player carries for it, or one line with what that line still misses. It needs the player online on
 * the server, since it takes the items from the inventory there; while the player is not, it says so instead. The
 * order's progress follows through its patch, and a completed order leaves the positions with it, so what was
 * delivered is told to the panel, which outlives the row; a new delivery clears what the panel said before.
 */
const props = defineProps<{
  order: ClaimedOrderView
}>()

const emit = defineEmits<{
  delivered: [text: string]
}>()

const {t, locale} = useI18n()
const conditions = useConditionTexts()
const {playerOnline} = useDashboard()
const action = useAction()

const open = computed(() => props.order.lines
  .map((line, index) => ({
    line: index + 1,
    name: lineName(line, conditions),
    missing: line.item.amount - line.delivered,
  }))
  .filter(line => line.missing > 0))

/**
 * Says what a delivery moved.
 *
 * @param delivered how many items were delivered
 * @param finished whether the order is complete
 */
function report(delivered: number, finished: boolean): void {
  const values = {count: count(delivered, locale.value), name: props.order.name}
  if (delivered === 0) emit('delivered', t('deliver.nothing', values))
  else emit('delivered', t(finished ? 'deliver.finished' : 'deliver.delivered', values))
}

/** Delivers everything the player carries for the order. */
async function deliverAll(): Promise<void> {
  emit('delivered', '')
  const result = await action.run('orders.deliver.all', {order: props.order.id})
  if (result) report(result.delivered, result.done)
}

/**
 * Delivers what one line misses.
 *
 * @param line the line, from one
 */
async function deliverLine(line: number): Promise<void> {
  emit('delivered', '')
  const result = await action.run('orders.deliver.line', {order: props.order.id, line})
  if (result) report(result.delivered, result.done)
}
</script>

<template>
  <div class="flex flex-col gap-2 md:col-span-4">
    <ActionNotice :problem="action.problem.value"/>
    <MutedText v-if="!playerOnline" tag="p">{{ t('deliver.joinFirst') }}</MutedText>
    <template v-else-if="open.length > 0">
      <ButtonRow>
        <SecondaryButton :disabled="action.busy.value" @click="deliverAll">{{ t('deliver.all') }}</SecondaryButton>
        <template v-if="open.length > 1">
          <SecondaryButton v-for="line in open" :key="line.line" :disabled="action.busy.value" @click="deliverLine(line.line)">
            {{ t('deliver.line', {name: line.name, missing: count(line.missing, locale)}) }}
          </SecondaryButton>
        </template>
      </ButtonRow>
    </template>
  </div>
</template>
