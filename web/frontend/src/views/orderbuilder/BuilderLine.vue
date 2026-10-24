/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {OrderCatalogue} from '~/api/gateway'
import IconButton from '@/components/button/IconButton.vue'
import ItemMark from '@/components/display/ItemMark.vue'
import DecimalInput from '@/components/input/DecimalInput.vue'
import NumberInput from '@/components/input/NumberInput.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {money} from '~/util/format'
import {type DraftLine, detailsOf, itemOf} from '~/util/orderDraft'

/**
 * One line of the order being built: its item with what it carries, the amount and the price of one item, with the
 * market's average beside the price where the market knows the material.
 */
const line = defineModel<DraftLine>({required: true})

const props = defineProps<{
  catalogue: OrderCatalogue | null
  average?: number
}>()

defineEmits<{
  remove: []
}>()

const {t, locale} = useI18n()
const item = computed(() => itemOf(line.value.source))
const details = computed(() => detailsOf(line.value.source, props.catalogue))
const total = computed(() => (line.value.amount !== undefined && line.value.unitPrice !== undefined ? line.value.amount * line.value.unitPrice : undefined))
</script>

<template>
  <li class="flex flex-col gap-3 border-b border-line-soft px-3 py-3 last:border-b-0 md:px-4">
    <div class="flex items-start gap-3">
      <ItemMark :item="item"/>
      <div class="flex min-w-0 grow flex-col">
        <span class="truncate text-sm font-semibold">{{ item.name }}</span>
        <MutedText v-if="details.length > 0">{{ details.join(', ') }}</MutedText>
      </div>
      <IconButton icon="trash" :label="t('builder.remove', {name: item.name})" @click="$emit('remove')"/>
    </div>
    <div class="flex flex-wrap items-start gap-3">
      <NumberInput v-model="line.amount" :label="t('builder.amount')" :min="1"/>
      <DecimalInput
          v-model="line.unitPrice"
          :label="t('builder.unitPrice')"
          :hint="average === undefined ? undefined : t('builder.average', {price: money(average, locale)})"
          :min="0"
      />
      <span v-if="total !== undefined" class="tabular ml-auto self-end pb-3 text-sm">{{ money(total, locale) }}</span>
    </div>
  </li>
</template>
