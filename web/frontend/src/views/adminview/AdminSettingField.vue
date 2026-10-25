/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {AdminSetting} from '~/api/gateway'
import CheckboxInput from '@/components/input/CheckboxInput.vue'
import DecimalInput from '@/components/input/DecimalInput.vue'
import NumberInput from '@/components/input/NumberInput.vue'
import SelectInput from '@/components/input/SelectInput.vue'
import TextAreaInput from '@/components/input/TextAreaInput.vue'
import TextInput from '@/components/input/TextInput.vue'
import ToggleSwitch from '@/components/input/ToggleSwitch.vue'
import {Feature} from '~/api/generated/protocol'

/**
 * One shared setting as an input fitting its type. The value is what the editor would save: a flag, a number, a text,
 * a list or switches by name. A secret is never shown: its value is `undefined` to keep it, a text to replace it and
 * an empty text to clear it, and it cannot be changed while an environment variable sets it.
 */
const model = defineModel<unknown>()

const props = defineProps<{
  setting: AdminSetting
  label: string
  /** The key and what to know about it, shown under the input. */
  hint: string
  /** Whether the admin may only look. */
  disabled?: boolean
}>()

const {t} = useI18n()

const flag = computed({
  get: () => model.value === true,
  set: value => (model.value = value),
})
const number = computed({
  get: () => (typeof model.value === 'number' ? model.value : undefined),
  set: value => (model.value = value),
})
const text = computed({
  get: () => (typeof model.value === 'string' ? model.value : ''),
  set: value => (model.value = value),
})
const lines = computed({
  get: () => (Array.isArray(model.value) ? model.value.join('\n') : ''),
  set: value => (model.value = value.split('\n').map(line => line.trim()).filter(line => line !== '')),
})
const options = computed(() => props.setting.choices.map(choice => ({value: choice, label: choice})))
const switches = computed(() => (model.value ?? {}) as Record<string, boolean>)
const fromEnvironment = computed(() => props.setting.secret?.source === 'environment')
const clearing = computed({
  get: () => model.value === '',
  set: value => (model.value = value ? '' : undefined),
})

/**
 * @param choice one of the setting's choices
 * @returns whether it is picked
 */
function picked(choice: string): boolean {
  return Array.isArray(model.value) && model.value.includes(choice)
}

/**
 * Picks or drops one choice.
 *
 * @param choice the choice
 * @param on whether it is picked
 */
function pick(choice: string, on: boolean): void {
  const current = new Set(Array.isArray(model.value) ? model.value as string[] : [])
  if (on) current.add(choice)
  else current.delete(choice)
  model.value = props.setting.choices.filter(entry => current.has(entry))
}

/**
 * Switches one named part on or off.
 *
 * @param name the part
 * @param on whether it is on
 */
function toggle(name: string, on: boolean): void {
  const stored = (props.setting.value ?? {}) as Record<string, boolean>
  const next = {...switches.value, [name]: on}
  model.value = Object.fromEntries(Object.entries(next).filter(([key, value]) => key in stored || !value))
}
</script>

<template>
  <div class="flex min-w-0 flex-col gap-1">
    <ToggleSwitch v-if="setting.type === 'FLAG'" v-model="flag" :label="label" :disabled="disabled"/>
    <NumberInput v-else-if="setting.type === 'WHOLE'" v-model="number" :label="label" :hint="hint" :min="setting.min ?? undefined" :max="setting.max ?? undefined" :disabled="disabled"/>
    <DecimalInput v-else-if="setting.type === 'DECIMAL'" v-model="number" :label="label" :hint="hint" :min="setting.min ?? undefined" :max="setting.max ?? undefined" :disabled="disabled"/>
    <SelectInput v-else-if="setting.type === 'CHOICE'" v-model="text" :label="label" :options="options" :disabled="disabled"/>
    <TextAreaInput v-else-if="setting.type === 'TEXT_LIST'" v-model="lines" :label="label" :hint="t('admin.settings.onePerLine')" :rows="4" :disabled="disabled"/>
    <fieldset v-else-if="setting.type === 'CHOICES'" class="flex flex-col gap-1">
      <legend class="text-xs font-semibold text-ink-muted">{{ label }}</legend>
      <CheckboxInput v-for="choice in setting.choices" :key="choice" :model-value="picked(choice)" :label="choice" :disabled="disabled" @update:model-value="pick(choice, $event)"/>
    </fieldset>
    <fieldset v-else-if="setting.type === 'FLAG_MAP'" class="flex flex-col gap-1">
      <legend class="text-xs font-semibold text-ink-muted">{{ label }}</legend>
      <CheckboxInput v-for="name in Object.values(Feature)" :key="name" :model-value="switches[name] !== false" :label="name" :disabled="disabled" @update:model-value="toggle(name, $event)"/>
    </fieldset>
    <template v-else-if="setting.type === 'SECRET'">
      <TextInput
          :model-value="clearing ? '' : text"
          :label="label"
          :hint="fromEnvironment ? t('admin.settings.secretEnvironment') : t('admin.settings.secretHint')"
          :placeholder="setting.secret?.set ? t('admin.settings.secretKept') : ''"
          :disabled="disabled || fromEnvironment || clearing"
          @update:model-value="model = $event === '' ? undefined : $event"
      />
      <CheckboxInput v-if="setting.secret?.set && !fromEnvironment && !disabled" v-model="clearing" :label="t('admin.settings.secretClear')"/>
    </template>
    <TextInput v-else v-model="text" :label="label" :hint="hint" :disabled="disabled"/>
    <span v-if="setting.type === 'FLAG' || setting.type === 'CHOICE' || setting.type === 'CHOICES' || setting.type === 'FLAG_MAP' || setting.type === 'SECRET'" class="text-xs text-ink-muted">{{ hint }}</span>
  </div>
</template>
