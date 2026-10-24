/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import DateInput from '@/components/input/DateInput.vue'
import NumberInput from '@/components/input/NumberInput.vue'
import SelectInput from '@/components/input/SelectInput.vue'
import ToggleSetting from '@/components/input/ToggleSetting.vue'
import type {DraftRepetition} from '~/util/orderDraft'

/**
 * Whether the order repeats, how often, and until when: a number of copies or a last day. Each copy is placed and
 * paid for when the one before is done; the server sets the limits and says so when one is crossed. The earliest last
 * day offered is the reader's tomorrow; the server decides by its own day.
 */
const repetition = defineModel<DraftRepetition>({required: true})

const {t} = useI18n()
const intervals = computed(() => [
  {value: 'daily', label: t('builder.daily')},
  {value: 'weekly', label: t('builder.weekly')},
])
const ends = computed(() => [
  {value: 'times', label: t('builder.endTimes')},
  {value: 'until', label: t('builder.endUntil')},
])
const tomorrow = computed(() => {
  const day = new Date()
  day.setDate(day.getDate() + 1)
  return [day.getFullYear(), day.getMonth() + 1, day.getDate()].map(part => String(part).padStart(2, '0')).join('-')
})

/**
 * Changes one field of the repetition.
 *
 * @param field the field
 * @param value its new value
 */
function set<K extends keyof DraftRepetition>(field: K, value: DraftRepetition[K]): void {
  repetition.value = {...repetition.value, [field]: value}
}
</script>

<template>
  <div class="flex flex-col gap-3">
    <ToggleSetting :model-value="repetition.enabled" :label="t('builder.repeat')" :hint="t('builder.repeatHint')" @update:model-value="value => set('enabled', value)"/>
    <div v-if="repetition.enabled" class="flex flex-wrap items-start gap-3">
      <SelectInput :model-value="repetition.interval" :label="t('builder.interval')" :options="intervals" @update:model-value="value => set('interval', value as DraftRepetition['interval'])"/>
      <SelectInput :model-value="repetition.end" :label="t('builder.end')" :options="ends" @update:model-value="value => set('end', value as DraftRepetition['end'])"/>
      <NumberInput v-if="repetition.end === 'times'" :model-value="repetition.times" :label="t('builder.times')" :min="1" @update:model-value="value => set('times', value)"/>
      <DateInput v-else :model-value="repetition.until" :label="t('builder.until')" :min="tomorrow" @update:model-value="value => set('until', value)"/>
    </div>
  </div>
</template>
