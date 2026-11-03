/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {AreaCost, AreaRow} from '~/api/gateway'
import CheckboxInput from '@/components/input/CheckboxInput.vue'
import TextInput from '@/components/input/TextInput.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {money} from '~/util/format'

/**
 * Where the materials of an area job go and come from: whether the blocks the company digs go into the owner's stock,
 * and whether the owner supplies the fill of a terraform job, each only when the server allows it. For a dig or
 * terraform job it reads what the area would dig out and fill in, and asks for a tick when the fill the company brings,
 * less the dug blocks it keeps, is worth more than the price; the server checks that again when the job is posted.
 */
const props = defineProps<{
  area: AreaRow
  kind: string
  y?: number
  surface: string
  price?: number
  allowDug: boolean
  allowFill: boolean
}>()

const dugToStock = defineModel<boolean>('dugToStock', {required: true})
const supplied = defineModel<boolean>('supplied', {required: true})
const fill = defineModel<string>('fill', {required: true})
const confirm = defineModel<boolean>('confirm', {required: true})
const blocked = defineModel<boolean>('blocked', {required: true})

const {t, locale} = useI18n()
const reading = useAction()
const cost = ref<AreaCost | null>(null)
const counted = computed(() => props.kind === 'dig' || (props.kind === 'terraform' && props.y !== undefined))
const supplying = computed(() => props.kind === 'terraform' && supplied.value)
const balance = computed(() => {
  const read = cost.value
  if (!read) return 0
  return (supplying.value ? 0 : read.fillCost) - (dugToStock.value ? 0 : read.dugValue)
})

const READ_DELAY = 300
let asked = 0
let waiting: ReturnType<typeof setTimeout> | undefined

/**
 * Reads what the job would dig out and fill in, a moment after the last change, so typing does not scan the area for
 * every key. Only the answer to the latest read counts.
 */
function read(): void {
  cost.value = null
  clearTimeout(waiting)
  const ask = ++asked
  if (!counted.value) return
  waiting = setTimeout(async () => {
    const answer = await reading.run('jobs.areas.materials', {
      node: props.area.node,
      area: props.area.name,
      kind: props.kind as 'dig' | 'terraform',
      ...(props.kind === 'terraform' ? {y: props.y, surface: props.surface.trim(), fill: supplying.value ? fill.value.trim() : ''} : {}),
    })
    if (ask === asked && counted.value) cost.value = answer
  }, READ_DELAY)
}

watch(() => [props.area, props.kind, props.y, props.surface, supplying.value, fill.value], read, {immediate: true})
onBeforeUnmount(() => clearTimeout(waiting))
watch(() => props.kind, kind => {
  if (kind !== 'terraform') supplied.value = false
})
watchEffect(() => {
  blocked.value = cost.value !== null && balance.value > (props.price ?? 0) && !confirm.value
})
</script>

<template>
  <div class="flex flex-col gap-2" data-testid="job-areas-materials">
    <CheckboxInput v-if="allowDug && kind !== 'harvest'" v-model="dugToStock" :label="t('jobs.areas.dugToStock')" data-testid="job-areas-dug"/>
    <template v-if="allowFill && kind === 'terraform'">
      <CheckboxInput v-model="supplied" :label="t('jobs.areas.suppliedFill')" data-testid="job-areas-fill"/>
      <TextInput v-if="supplied" v-model="fill" :label="t('jobs.areas.fillBlock')" :maxlength="64" data-testid="job-areas-fill-block"/>
    </template>
    <MutedText v-if="cost" tag="p" data-testid="job-areas-cost">{{ t('jobs.areas.materials', {dug: cost.dugBlocks, dugValue: money(cost.dugValue, locale), fill: cost.fillBlocks, fillCost: money(cost.fillCost, locale)}) }}</MutedText>
    <MutedText v-if="cost && cost.unpriced > 0" tag="p">{{ t('jobs.areas.fitUnpriced', {count: cost.unpriced}) }}</MutedText>
    <CheckboxInput v-if="cost && balance > (price ?? 0)" v-model="confirm" :label="t('jobs.areas.belowMaterials', {cost: money(balance, locale)})" data-testid="job-areas-below-materials"/>
  </div>
</template>
