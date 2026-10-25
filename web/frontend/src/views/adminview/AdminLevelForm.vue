/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {AdminLevel, AdminLevels, LevelFieldKey} from '~/api/gateway'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import PanelBox from '@/components/container/PanelBox.vue'
import ActionNotice from '@/components/feedback/ActionNotice.vue'
import ConfirmStep from '@/components/feedback/ConfirmStep.vue'
import DecimalInput from '@/components/input/DecimalInput.vue'
import NumberInput from '@/components/input/NumberInput.vue'
import TextInput from '@/components/input/TextInput.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'

/**
 * One company level in one form, as the in-game level menu shows it: its name, what a company needs to reach it, what
 * it allows, and its position. Saving sends every field at once, so one invalid value changes nothing. The tax and the
 * alert limit left empty fall back to the global values. Removing asks once more.
 */
const props = defineProps<{
  level: AdminLevel
  count: number
  editable: boolean
}>()

const emit = defineEmits<{
  saved: [level: AdminLevel]
  removed: [levels: AdminLevels]
}>()

const {t} = useI18n()
const action = useAction()
const name = ref(props.level.name)
const position = ref<number | undefined>(props.level.level)
const values = reactive(numbers(props.level))
const missing = computed(() => FIELDS.some(field => !field.optional && values[field.key] === undefined))

/**
 * @param level the level
 * @returns the numbers of the level by field
 */
function numbers(level: AdminLevel): Record<NumericField, number | undefined> {
  return {
    order_count: level.orderCount,
    member_count: level.memberCount,
    earned_money: level.earnedMoney,
    delivered_items: level.deliveredItems,
    max_members: level.maxMembers,
    max_orders: level.maxOrders,
    max_order_size: level.maxOrderSize,
    delivery_hours_bonus: level.deliveryHoursBonus,
    tax_percent: level.taxPercent ?? undefined,
    max_alerts: level.maxAlerts ?? undefined,
  }
}

/** Saves every field and the position. */
async function save(): Promise<void> {
  const entered: Partial<Record<LevelFieldKey, string>> = {name: name.value}
  for (const field of FIELDS) entered[field.key] = values[field.key] === undefined ? '' : String(values[field.key])
  const result = await action.run('admin.levels.edit', {level: props.level.level, values: entered, position: position.value})
  if (result) emit('saved', result)
}

/** Removes the level. */
async function remove(): Promise<void> {
  const result = await action.run('admin.levels.remove', {level: props.level.level})
  if (result) emit('removed', result)
}

/** The fields holding numbers. */
type NumericField = Exclude<LevelFieldKey, 'name'>

/** A number field of the form. */
interface Field {
  key: NumericField
  group: 'requirements' | 'limits'
  decimal?: boolean
  optional?: boolean
  max?: number
}

const FIELDS: Field[] = [
  {key: 'order_count', group: 'requirements'},
  {key: 'member_count', group: 'requirements'},
  {key: 'earned_money', group: 'requirements', decimal: true},
  {key: 'delivered_items', group: 'requirements'},
  {key: 'max_members', group: 'limits'},
  {key: 'max_orders', group: 'limits'},
  {key: 'max_order_size', group: 'limits'},
  {key: 'delivery_hours_bonus', group: 'limits'},
  {key: 'tax_percent', group: 'limits', decimal: true, optional: true, max: 100},
  {key: 'max_alerts', group: 'limits', optional: true},
]
</script>

<template>
  <PanelBox padded :aria-label="t('admin.levels.edit', {level: level.level})" data-testid="admin-level">
    <SectionHeader>{{ t('admin.levels.edit', {level: level.level}) }}</SectionHeader>
    <form class="flex flex-col gap-4" @submit.prevent="save">
      <div class="grid gap-3 sm:grid-cols-2">
        <TextInput v-model="name" :label="t('admin.levels.name')" :hint="t('admin.levels.nameHint')" :disabled="!editable"/>
        <NumberInput v-model="position" :label="t('admin.levels.position')" :min="1" :max="count" :disabled="!editable"/>
      </div>
      <fieldset v-for="group in (['requirements', 'limits'] as const)" :key="group" class="m-0 grid gap-3 border-0 p-0 sm:grid-cols-2">
        <legend class="mb-2 text-sm font-semibold">{{ t(`admin.levels.${group}`) }}</legend>
        <component
            :is="field.decimal ? DecimalInput : NumberInput"
            v-for="field in FIELDS.filter(entry => entry.group === group)"
            :key="field.key"
            v-model="values[field.key]"
            :label="t(`admin.levels.field.${field.key}`)"
            :hint="field.optional ? t('admin.levels.globalHint') : undefined"
            :min="0"
            :max="field.max"
            :disabled="!editable"
        />
      </fieldset>
      <ActionNotice :problem="action.problem.value"/>
      <PrimaryButton v-if="editable" type="submit" :disabled="missing || name.trim() === '' || position === undefined || action.busy.value">{{ t('admin.levels.save') }}</PrimaryButton>
    </form>
    <ConfirmStep
        v-if="editable"
        :trigger="t('admin.levels.remove')"
        :question="t('admin.levels.removeQuestion', {level: level.level})"
        :confirm="t('admin.levels.removeConfirm')"
        :busy="action.busy.value"
        @confirmed="remove"
    />
  </PanelBox>
</template>
