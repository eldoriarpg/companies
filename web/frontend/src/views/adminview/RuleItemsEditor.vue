/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {SystemOrderRule, SystemOrderRuleItem} from '~/api/gateway'
import ButtonRow from '@/components/button/ButtonRow.vue'
import IconButton from '@/components/button/IconButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SelectInput from '@/components/input/SelectInput.vue'
import TextInput from '@/components/input/TextInput.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'

/**
 * What a defined rule orders, one entry picked per order: materials and criteria are typed and switched between,
 * exact items are only shown and removed, as they are captured in game. With several entries, how the next one is
 * picked.
 */
const rule = defineModel<SystemOrderRule>('rule', {required: true})

defineProps<{
  editable: boolean
}>()

const {t} = useI18n()
const kinds = computed(() => ['material', 'criteria'].map(value => ({value, label: t(`admin.systemorders.rules.kind.${value}`)})))
const resolves = computed(() => ['ANY', 'ONE'].map(value => ({value, label: t(`admin.systemorders.rules.resolve.${value}`)})))
const picks = computed(() => ['RANDOM', 'ROTATE'].map(value => ({value, label: t(`admin.systemorders.rules.pick.${value}`)})))

/**
 * Whether an entry orders criteria rather than a material.
 *
 * @param item the entry
 * @returns its kind in the form
 */
function kindOf(item: SystemOrderRuleItem): 'material' | 'criteria' {
  return item.criteria === undefined ? 'material' : 'criteria'
}

/**
 * What an entry names: its material or its criteria.
 *
 * @param item the entry
 * @returns the text
 */
function text(item: SystemOrderRuleItem): string {
  return item.criteria ?? item.material ?? ''
}

/**
 * Changes what an entry names, keeping its kind.
 *
 * @param item the entry
 * @param value the material or criteria
 */
function setText(item: SystemOrderRuleItem, value: string): void {
  if (kindOf(item) === 'criteria') item.criteria = value
  else item.material = value
}

/**
 * Switches an entry between a material and criteria, keeping what it names.
 *
 * @param item the entry
 * @param kind the new kind
 */
function setKind(item: SystemOrderRuleItem, kind: string): void {
  const value = text(item)
  delete item.material
  delete item.criteria
  if (kind === 'criteria') {
    item.criteria = value
    item.resolve = item.resolve ?? 'ANY'
  } else {
    item.material = value
    delete item.resolve
  }
}

/**
 * How an entry reads in the list of rules and in the form.
 *
 * @param item the entry
 * @returns the label
 */
function label(item: SystemOrderRuleItem): string {
  if (item.item !== undefined) return item.name || t('admin.systemorders.rules.exactItem')
  return text(item)
}
</script>

<template>
  <div class="flex flex-col gap-3">
    <SectionHeader :note="t('admin.systemorders.rules.itemsNote')">{{ t('admin.systemorders.rules.fields.items') }}</SectionHeader>
    <ul class="m-0 flex list-none flex-col gap-2 p-0" data-testid="admin-system-rule-items">
      <li v-for="(item, index) in rule.items" :key="index" class="flex flex-wrap items-end gap-3">
        <template v-if="item.item !== undefined">
          <span class="grow text-sm">{{ label(item) }}</span>
          <MutedText>{{ t('admin.systemorders.rules.exactItem') }}</MutedText>
        </template>
        <template v-else>
          <div class="w-40">
            <SelectInput :model-value="kindOf(item)" :label="t('admin.systemorders.rules.fields.kind')" :options="kinds" :disabled="!editable" @update:model-value="setKind(item, String($event))"/>
          </div>
          <div class="min-w-48 grow">
            <TextInput
                :model-value="text(item)"
                :label="kindOf(item) === 'criteria' ? t('admin.systemorders.rules.fields.criteria') : t('admin.systemorders.rules.fields.material')"
                :placeholder="kindOf(item) === 'criteria' ? '#logs bamboo_block' : 'IRON_INGOT'"
                :disabled="!editable"
                @update:model-value="setText(item, String($event))"
            />
          </div>
          <div v-if="kindOf(item) === 'criteria'" class="w-48">
            <SelectInput :model-value="item.resolve ?? 'ANY'" :label="t('admin.systemorders.rules.fields.resolve')" :options="resolves" :disabled="!editable" @update:model-value="item.resolve = $event === 'ONE' ? 'ONE' : 'ANY'"/>
          </div>
        </template>
        <IconButton v-if="editable" icon="trash" :label="t('admin.systemorders.rules.removeItem', {item: label(item) || index + 1})" @click="rule.items.splice(index, 1)"/>
      </li>
    </ul>
    <ButtonRow v-if="editable">
      <SecondaryButton @click="rule.items.push({material: ''})">{{ t('admin.systemorders.rules.addMaterial') }}</SecondaryButton>
      <SecondaryButton @click="rule.items.push({criteria: '', resolve: 'ANY'})">{{ t('admin.systemorders.rules.addCriteria') }}</SecondaryButton>
    </ButtonRow>
    <MutedText tag="p">{{ t('admin.systemorders.rules.captureNote', {command: `/companyadmin systemorders item add ${rule.id || '<id>'}`}) }}</MutedText>
    <SelectInput v-if="rule.items.length > 1" :model-value="rule.pick" :label="t('admin.systemorders.rules.fields.pick')" :options="picks" :disabled="!editable" @update:model-value="rule.pick = $event === 'ROTATE' ? 'ROTATE' : 'RANDOM'"/>
  </div>
</template>
