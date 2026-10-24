/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import type {AcceptableValue} from 'reka-ui'
import MutedText from '@/components/typography/MutedText.vue'
import LabelledField from './LabelledField.vue'
import DropdownGroups from './dropdown/DropdownGroups.vue'
import DropdownListbox from './dropdown/DropdownListbox.vue'
import DropdownOption from './dropdown/DropdownOption.vue'
import DropdownPanel from './dropdown/DropdownPanel.vue'
import DropdownSearch from './dropdown/DropdownSearch.vue'
import DropdownTrigger from './dropdown/DropdownTrigger.vue'
import {groupOptions, type SelectOption} from './dropdown/groupOptions'

/**
 * One choice out of a long list, found by typing, with its label above it. The field reads the current choice, or the
 * value itself where it is not among the options; the search matches labels and values alike. Entries sit under their
 * group headings, and the `option` slot draws an entry richer than its label, such as an item with its icon.
 *
 * <p>A list of more than `limit` matches shows only the first ones and says so, so a list of every material stays
 * quick to open: typing narrows it to what the player means.
 */
const model = defineModel<string>()

const props = withDefaults(defineProps<{
  label: string
  options: SelectOption[]
  placeholder?: string
  disabled?: boolean
  /** The most entries shown at once. */
  limit?: number
}>(), {
  placeholder: undefined,
  limit: 100,
})

defineSlots<{
  option?(props: {option: SelectOption}): unknown
}>()

const {t} = useI18n()

const search = ref('')
const open = ref(false)

const matches = computed(() => {
  const typed = search.value.trim().toLowerCase()
  if (!typed) return props.options
  return props.options.filter(option =>
    option.label.toLowerCase().includes(typed) || option.value.toLowerCase().includes(typed))
})
const shown = computed(() => groupOptions(matches.value.slice(0, props.limit)))
const selectedLabel = computed(() => props.options.find(option => option.value === model.value)?.label ?? model.value ?? '')

watch(open, isOpen => {
  if (!isOpen) search.value = ''
})

/**
 * Takes the entry the list chose and closes it.
 *
 * @param value the chosen value
 */
function select(value: AcceptableValue | AcceptableValue[] | undefined): void {
  model.value = String(value ?? '')
  open.value = false
}
</script>

<template>
  <LabelledField v-slot="{id, labelId}" :label="label">
    <DropdownPanel v-model:open="open" match-width panel-class="max-h-80">
      <template #trigger>
        <DropdownTrigger :id="id" :aria-labelledby="`${labelId} ${id}-value`" :open="open" :disabled="disabled">
          <span :id="`${id}-value`" :class="selectedLabel ? '' : 'text-ink-faint'">{{ selectedLabel || placeholder || '' }}</span>
        </DropdownTrigger>
      </template>
      <DropdownListbox :model-value="model" :label="label" @update:model-value="select">
        <template #head>
          <DropdownSearch v-model="search" :placeholder="placeholder ?? label"/>
        </template>
        <DropdownGroups v-slot="{option}" :groups="shown">
          <DropdownOption :value="option.value">
            <slot name="option" :option="option">{{ option.label }}</slot>
          </DropdownOption>
        </DropdownGroups>
        <MutedText v-if="matches.length === 0" tag="p" class="px-3 py-2">{{ t('common.noMatch') }}</MutedText>
        <MutedText v-else-if="matches.length > limit" tag="p" class="px-3 py-2">
          {{ t('common.narrowSearch', {shown: limit, total: matches.length}) }}
        </MutedText>
      </DropdownListbox>
    </DropdownPanel>
  </LabelledField>
</template>
