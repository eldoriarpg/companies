/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {CatalogueMaterial, ItemDefinition, OrderCatalogue} from '~/api/gateway'
import CheckboxInput from '@/components/input/CheckboxInput.vue'
import NumberInput from '@/components/input/NumberInput.vue'
import SelectInput from '@/components/input/SelectInput.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import {excluded} from '~/util/orderDraft'

/**
 * What survival can give an item of one material, and nothing else, so every order stays deliverable: enchantments up
 * to their survival level and never two that exclude each other, a potion type, a leather dye, an armour trim. The
 * server offers only what the material takes.
 */
const definition = defineModel<ItemDefinition>({required: true})

const props = defineProps<{
  material: CatalogueMaterial
  catalogue: OrderCatalogue
}>()

const {t} = useI18n()

const enchantments = computed(() => props.material.enchantments
  .map(key => props.catalogue.enchantments.find(entry => entry.key === key))
  .filter(entry => entry !== undefined))
const chosen = computed(() => Object.keys(definition.value.enchantments ?? {}))
const blocked = computed(() => excluded(chosen.value, props.catalogue))
const none = computed(() => ({value: '', label: t('builder.none')}))
const potions = computed(() => [none.value, ...props.catalogue.potions.map(entry => ({value: entry.key, label: entry.name}))])
const dyes = computed(() => [none.value, ...props.catalogue.dyes.map(entry => ({value: entry.key, label: entry.name}))])
const patterns = computed(() => [none.value, ...props.catalogue.trimPatterns.map(entry => ({value: entry.key, label: entry.name}))])
const trimMaterials = computed(() => [none.value, ...props.catalogue.trimMaterials.map(entry => ({value: entry.key, label: entry.name}))])

/**
 * Puts an enchantment on at its highest level, or takes it off.
 *
 * @param key the enchantment
 * @param on whether it is on
 * @param max its highest level
 */
function toggle(key: string, on: boolean, max: number): void {
  const others = Object.entries(definition.value.enchantments ?? {}).filter(([other]) => other !== key)
  definition.value = {...definition.value, enchantments: Object.fromEntries(on ? [...others, [key, max]] : others)}
}

/**
 * Sets an enchantment's level.
 *
 * @param key the enchantment
 * @param level the level, none while the field cannot be read
 */
function level(key: string, level: number | undefined): void {
  definition.value = {...definition.value, enchantments: {...definition.value.enchantments, [key]: level ?? 0}}
}

/**
 * Sets one of the single choices; the empty choice takes it off.
 *
 * @param field the choice
 * @param value the chosen key
 */
function choose(field: 'potion' | 'dye', value: string): void {
  definition.value = {...definition.value, [field]: value || undefined}
}

/**
 * Sets one half of the trim.
 *
 * @param half the pattern or the material
 * @param value the chosen key
 */
function trim(half: 'pattern' | 'material', value: string): void {
  const current = definition.value.trim ?? {pattern: '', material: ''}
  definition.value = {...definition.value, trim: {...current, [half]: value}}
}
</script>

<template>
  <div class="flex flex-col gap-4">
    <section v-if="enchantments.length > 0" class="flex flex-col gap-1">
      <SectionHeader :note="t('builder.enchantmentsNote')">{{ t('builder.enchantments') }}</SectionHeader>
      <ul class="m-0 flex list-none flex-col p-0">
        <li v-for="enchantment in enchantments" :key="enchantment.key" class="flex flex-wrap items-end justify-between gap-2 border-b border-line-soft py-1 last:border-b-0">
          <CheckboxInput
              :model-value="chosen.includes(enchantment.key)"
              :label="enchantment.name"
              :disabled="blocked.has(enchantment.key)"
              @update:model-value="on => toggle(enchantment.key, on, enchantment.max)"
          />
          <NumberInput
              v-if="chosen.includes(enchantment.key)"
              :model-value="definition.enchantments?.[enchantment.key]"
              :label="t('builder.level', {name: enchantment.name})"
              :min="1"
              :max="enchantment.max"
              @update:model-value="value => level(enchantment.key, value)"
          />
        </li>
      </ul>
    </section>
    <SelectInput
        v-if="material.potion"
        :model-value="definition.potion ?? ''"
        :label="t('builder.potion')"
        :options="potions"
        @update:model-value="value => choose('potion', value)"
    />
    <SelectInput
        v-if="material.dye"
        :model-value="definition.dye ?? ''"
        :label="t('builder.dye')"
        :options="dyes"
        @update:model-value="value => choose('dye', value)"
    />
    <div v-if="material.trim" class="flex flex-wrap gap-3">
      <SelectInput
          :model-value="definition.trim?.pattern ?? ''"
          :label="t('builder.trimPattern')"
          :options="patterns"
          @update:model-value="value => trim('pattern', value)"
      />
      <SelectInput
          :model-value="definition.trim?.material ?? ''"
          :label="t('builder.trimMaterial')"
          :options="trimMaterials"
          @update:model-value="value => trim('material', value)"
      />
    </div>
  </div>
</template>
