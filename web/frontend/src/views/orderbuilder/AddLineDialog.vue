/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {ItemDefinition, KnownItems, OrderCatalogue} from '~/api/gateway'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ItemMark from '@/components/display/ItemMark.vue'
import Modal from '@/components/feedback/Modal.vue'
import SearchSelectInput from '@/components/input/SearchSelectInput.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import ItemEditor from './ItemEditor.vue'
import {definable, type LineSource, trimmed} from '~/util/orderDraft'

/**
 * Picks the item of a new line: a material of the server, one of the player's saved items, or an exact item of an
 * earlier order, found by typing. A material that can be given something opens the item editor beneath; left as it
 * is, the plain material is ordered. The line is added with the choice and the dialog closes.
 */
const open = defineModel<boolean>('open', {required: true})

const props = defineProps<{
  catalogue: OrderCatalogue
  known: KnownItems
}>()

const emit = defineEmits<{
  add: [source: LineSource]
}>()

const {t} = useI18n()
const picked = ref('')
const definition = ref<ItemDefinition>({material: ''})

const options = computed(() => [
  ...props.known.saved.map(entry => ({value: entry.ref, label: `${entry.label} · ${entry.item.name}`, group: t('builder.saved')})),
  ...props.known.earlier.map(entry => ({value: entry.ref, label: `${entry.label} · ${entry.item.name}`, group: t('builder.earlier')})),
  ...props.catalogue.materials.map(entry => ({value: entry.item.material, label: entry.item.name, group: t('builder.materials')})),
])
const material = computed(() => props.catalogue.materials.find(entry => entry.item.material === picked.value))
const knownItem = computed(() => [...props.known.saved, ...props.known.earlier].find(entry => entry.ref === picked.value))
const editable = computed(() => material.value !== undefined && definable(material.value))
const complete = computed(() => {
  if (!material.value) return knownItem.value !== undefined
  const levels = Object.values(definition.value.enchantments ?? {})
  const trim = definition.value.trim
  return levels.every(level => level > 0) && (!trim || Boolean(trim.pattern) === Boolean(trim.material))
})

watch(picked, value => {
  definition.value = {material: value}
})
watch(open, isOpen => {
  if (isOpen) picked.value = ''
})

/**
 * The item an option stands for, to draw it.
 *
 * @param value the option's value
 * @returns the item
 */
function itemOf(value: string) {
  return props.catalogue.materials.find(entry => entry.item.material === value)?.item
    ?? [...props.known.saved, ...props.known.earlier].find(entry => entry.ref === value)?.item
}

/** Adds the line and closes the dialog. */
function add(): void {
  if (knownItem.value) emit('add', {kind: 'known', known: knownItem.value})
  else if (material.value) {
    const chosen = trimmed(definition.value)
    emit('add', chosen ? {kind: 'defined', material: material.value, definition: chosen} : {kind: 'material', material: material.value})
  }
  open.value = false
}
</script>

<template>
  <Modal v-model="open" size="lg" mobile-full>
    <div class="flex flex-col gap-4">
      <SectionHeader>{{ t('builder.addLine') }}</SectionHeader>
      <SearchSelectInput v-model="picked" :label="t('builder.item')" :options="options" :placeholder="t('builder.itemPlaceholder')">
        <template #option="{option}">
          <ItemMark v-if="itemOf(option.value)" :item="itemOf(option.value)!" size="small"/>
          <span class="truncate">{{ option.label }}</span>
        </template>
      </SearchSelectInput>
      <MutedText v-if="knownItem && knownItem.details.length > 0" tag="p">{{ knownItem.details.join(', ') }}</MutedText>
      <template v-if="editable && material">
        <SectionHeader :note="t('builder.editorNote')">{{ t('builder.editor') }}</SectionHeader>
        <ItemEditor v-model="definition" :material="material" :catalogue="catalogue"/>
      </template>
      <ButtonRow>
        <SecondaryButton data-cancel @click="open = false">{{ t('builder.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="!picked || !complete" @click="add">{{ t('builder.add') }}</PrimaryButton>
      </ButtonRow>
    </div>
  </Modal>
</template>
