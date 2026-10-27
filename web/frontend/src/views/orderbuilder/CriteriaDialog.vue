/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {CriteriaArgs, OrderCatalogue} from '~/api/gateway'
import ButtonRow from '@/components/button/ButtonRow.vue'
import IconButton from '@/components/button/IconButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ItemMark from '@/components/display/ItemMark.vue'
import Modal from '@/components/feedback/Modal.vue'
import NumberInput from '@/components/input/NumberInput.vue'
import SearchSelectInput from '@/components/input/SearchSelectInput.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {criteriaSource, entryName, type LineSource, roman} from '~/util/orderDraft'

/**
 * Builds a criteria line: any item of a set of materials and item tags, mixed, optionally with at least a number of
 * enchantments and with chosen enchantments at a minimum level. Entries and enchantments are added by typing and
 * removed again beside their name. Given the criteria of a line already there, the dialog opens on them to change
 * them instead.
 */
const open = defineModel<boolean>('open', {required: true})

const props = defineProps<{
  catalogue: OrderCatalogue
  /** The criteria of the line being changed, none for a new line. */
  editing?: CriteriaArgs | null
}>()

const emit = defineEmits<{
  add: [source: LineSource]
}>()

const {t} = useI18n()
const items = ref<string[]>([])
const minEnchantments = ref<number | undefined>(0)
const enchantments = ref<Record<string, number>>({})
const pickedItem = ref('')
const pickedEnchantment = ref('')
const level = ref<number | undefined>(1)

const itemOptions = computed(() => [
  ...(props.catalogue.tags ?? []).map(tag => ({value: tag.key, label: tag.name, group: t('builder.tags')})),
  ...props.catalogue.materials.map(entry => ({value: entry.item.material, label: entry.item.name, group: t('builder.materials')})),
].filter(option => !items.value.includes(option.value)))
const enchantmentOptions = computed(() => props.catalogue.enchantments
  .filter(entry => !(entry.key in enchantments.value))
  .map(entry => ({value: entry.key, label: entry.name})))
const chosenEnchantment = computed(() => props.catalogue.enchantments.find(entry => entry.key === pickedEnchantment.value))
const criteria = computed<CriteriaArgs>(() => ({
  items: items.value,
  minEnchantments: minEnchantments.value ?? 0,
  enchantments: enchantments.value,
}))
const source = computed(() => (items.value.length > 0 ? criteriaSource(criteria.value, props.catalogue) : null))

watch(pickedItem, value => {
  if (value === '') return
  items.value = [...items.value, value]
  nextTick(() => {
    pickedItem.value = ''
  })
})
watch(open, isOpen => {
  if (!isOpen) return
  items.value = [...(props.editing?.items ?? [])]
  minEnchantments.value = props.editing?.minEnchantments ?? 0
  enchantments.value = {...(props.editing?.enchantments ?? {})}
  pickedEnchantment.value = ''
  level.value = 1
}, {immediate: true})

/**
 * Draws an entry of the set: the item itself, or the first item of a tag.
 *
 * @param entry the material key or tag
 * @returns the item, none when the catalogue does not know it
 */
function itemOf(entry: string) {
  const key = entry.startsWith('#') ? props.catalogue.tags?.find(tag => tag.key === entry)?.materials[0] : entry
  return props.catalogue.materials.find(material => material.item.material === key)?.item
}

/** Requires the chosen enchantment at the entered level. */
function addEnchantment(): void {
  const entry = chosenEnchantment.value
  if (!entry || level.value === undefined) return
  enchantments.value = {...enchantments.value, [entry.key]: Math.min(entry.max, Math.max(1, level.value))}
  pickedEnchantment.value = ''
  level.value = 1
}

/**
 * Drops a required enchantment.
 *
 * @param key the enchantment
 */
function removeEnchantment(key: string): void {
  const {[key]: _, ...rest} = enchantments.value
  enchantments.value = rest
}

/** Adds the line and closes the dialog. */
function add(): void {
  if (source.value) emit('add', source.value)
  open.value = false
}
</script>

<template>
  <Modal v-model="open" size="lg" mobile-full>
    <div class="flex flex-col gap-4">
      <SectionHeader :note="t('builder.criteriaNote')">{{ editing ? t('builder.editCriteria') : t('builder.addCriteria') }}</SectionHeader>
      <SearchSelectInput v-model="pickedItem" :label="t('builder.criteriaItems')" :options="itemOptions" :placeholder="t('builder.criteriaItemsPlaceholder')">
        <template #option="{option}">
          <ItemMark v-if="itemOf(option.value)" :item="itemOf(option.value)!" size="small"/>
          <span class="truncate">{{ option.label }}</span>
        </template>
      </SearchSelectInput>
      <ul v-if="items.length > 0" class="m-0 flex list-none flex-col gap-1 p-0" data-criteria-items>
        <li v-for="entry in items" :key="entry" class="flex items-center gap-2 text-sm">
          <ItemMark v-if="itemOf(entry)" :item="itemOf(entry)!" size="small"/>
          <span class="grow truncate">{{ entryName(entry, catalogue) }}</span>
          <IconButton icon="trash" :label="t('builder.remove', {name: entryName(entry, catalogue)})" @click="items = items.filter(other => other !== entry)"/>
        </li>
      </ul>
      <MutedText v-else tag="p">{{ t('builder.criteriaEmpty') }}</MutedText>
      <SectionHeader :note="t('builder.conditionsNote')">{{ t('builder.conditions') }}</SectionHeader>
      <NumberInput v-model="minEnchantments" :label="t('builder.minEnchantments')" :min="0"/>
      <div class="flex flex-wrap items-end gap-3">
        <div class="min-w-48 grow">
          <SearchSelectInput v-model="pickedEnchantment" :label="t('builder.requiredEnchantment')" :options="enchantmentOptions"/>
        </div>
        <NumberInput v-model="level" :label="t('builder.minLevel')" :min="1" :max="chosenEnchantment?.max"/>
        <SecondaryButton :disabled="!chosenEnchantment || level === undefined" @click="addEnchantment">{{ t('builder.require') }}</SecondaryButton>
      </div>
      <ul v-if="Object.keys(enchantments).length > 0" class="m-0 flex list-none flex-col gap-1 p-0" data-criteria-enchantments>
        <li v-for="(minLevel, key) in enchantments" :key="key" class="flex items-center gap-2 text-sm">
          <span class="grow truncate">{{ catalogue.enchantments.find(entry => entry.key === key)?.name ?? key }} {{ roman(minLevel) }}+</span>
          <IconButton icon="trash" :label="t('builder.remove', {name: String(key)})" @click="removeEnchantment(String(key))"/>
        </li>
      </ul>
      <ButtonRow>
        <SecondaryButton data-cancel @click="open = false">{{ t('builder.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="!source" @click="add">{{ editing ? t('builder.save') : t('builder.add') }}</PrimaryButton>
      </ButtonRow>
    </div>
  </Modal>
</template>
