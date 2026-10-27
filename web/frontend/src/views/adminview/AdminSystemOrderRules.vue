/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {SystemOrderRule, SystemOrderRuleItem} from '~/api/gateway'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import RowButton from '@/components/button/RowButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PanelBox from '@/components/container/PanelBox.vue'
import PlainList from '@/components/container/PlainList.vue'
import ActionNotice from '@/components/feedback/ActionNotice.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import NoticeBadge from '@/components/badge/NoticeBadge.vue'
import CheckboxInput from '@/components/input/CheckboxInput.vue'
import DecimalInput from '@/components/input/DecimalInput.vue'
import NumberInput from '@/components/input/NumberInput.vue'
import SelectInput from '@/components/input/SelectInput.vue'
import TextInput from '@/components/input/TextInput.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import RuleItemsEditor from './RuleItemsEditor.vue'

/**
 * The defined rules of the server's own orders, edited as one list: a rule is chosen from the list and changed in its
 * form, rules are added and removed, and all of them are saved together as the next revision of
 * `system-orders.yml`. A rule orders one of its entries per order: a material, criteria such as `#logs enchants:2`, or
 * an exact item, which is captured in game and only shown and removed here. A save refused for a problem names the
 * rule and the field; one refused because someone saved meanwhile shows the rules saved then.
 */
const {t} = useI18n()
const loading = useAction()
const saving = useAction()
const revision = ref(0)
const editable = ref(false)
const rules = ref<SystemOrderRule[]>([])
const chosen = ref(0)
const errors = ref<Record<string, string>>({})
const done = ref('')
const kept = ref(false)
const rule = computed(() => rules.value[chosen.value])
const incomplete = computed(() => rules.value.findIndex(entry => !complete(entry)))
const failing = computed(() => [...new Set(Object.keys(errors.value)
  .map(path => /^rules\.(\d+)/.exec(path)?.[1])
  .filter((index): index is string => index !== undefined)
  .map(Number))])
const modes = computed(() => ['FIXED', 'AVERAGE'].map(value => ({value, label: t(`admin.systemorders.rules.mode.${value}`)})))
const sources = computed(() => ['MINTED', 'ACCOUNT'].map(value => ({value, label: t(`admin.systemorders.rules.source.${value}`)})))

/**
 * How an entry reads in the list of rules.
 *
 * @param item the entry
 * @returns the label
 */
function itemLabel(item: SystemOrderRuleItem): string {
  if (item.item != null) return item.name || t('admin.systemorders.rules.exactItem')
  return item.criteria ?? item.material ?? ''
}

onMounted(() => {
  void load()
})

/** Loads the rules of the latest revision. */
async function load(): Promise<void> {
  const result = await loading.run('admin.systemorders.rules', {})
  if (!result) return
  revision.value = result.revision
  editable.value = result.editable
  rules.value = result.rules.map(filled)
  chosen.value = 0
}

/**
 * A rule with every field set, the file's defaults for those it leaves out.
 *
 * @param rule a rule as the file writes it
 * @returns the rule for the form
 */
function filled(rule: Partial<SystemOrderRule>): SystemOrderRule {
  return {
    id: rule.id ?? '',
    name: rule.name ?? '',
    enabled: rule.enabled ?? true,
    material: '',
    items: rule.items && rule.items.length > 0
      ? rule.items.map(sparse)
      : [{material: rule.material ?? ''}],
    pick: String(rule.pick ?? '').toUpperCase() === 'ROTATE' ? 'ROTATE' : 'RANDOM',
    amount: rule.amount ?? 64,
    price: {mode: 'AVERAGE', value: 1, factor: 1, jitter: 0, min: 0, max: 0, ...rule.price},
    everyHours: rule.everyHours ?? 24,
    lifetimeHours: rule.lifetimeHours ?? 12,
    maxOpen: rule.maxOpen ?? 1,
    source: {type: 'MINTED', account: '', dailyBudget: 0, ...rule.source},
  }
}

/**
 * An entry with only the fields of its kind, since the plugin stores every field of an entry.
 *
 * @param entry an entry as the file writes it
 * @returns the entry for the form
 */
function sparse(entry: SystemOrderRuleItem): SystemOrderRuleItem {
  if (entry.item !== undefined && entry.item !== null) return {item: entry.item, name: entry.name ?? ''}
  if (entry.criteria) return {criteria: entry.criteria, resolve: String(entry.resolve ?? '').toUpperCase() === 'ONE' ? 'ONE' : 'ANY'}
  return {material: entry.material ?? ''}
}

/** Adds a rule and opens it. */
function add(): void {
  rules.value.push(filled({}))
  chosen.value = rules.value.length - 1
}

/** Removes the open rule. */
function remove(): void {
  rules.value.splice(chosen.value, 1)
  chosen.value = Math.max(0, chosen.value - 1)
  errors.value = {}
}

/**
 * Whether every number of a rule holds a valid value, since a cleared or out of range field holds none.
 *
 * @param entry a rule
 * @returns true when it can be saved
 */
function complete(entry: SystemOrderRule): boolean {
  const numbers = [entry.amount, entry.everyHours, entry.lifetimeHours, entry.maxOpen, entry.price.jitter, entry.price.min,
    entry.price.max, entry.source.dailyBudget, entry.price.mode === 'FIXED' ? entry.price.value : entry.price.factor]
  return numbers.every(value => value !== undefined)
    && entry.items.length > 0
    && entry.items.every(item => item.item != null || (item.criteria ?? item.material ?? '').trim() !== '')
}

/** Saves every rule as the next revision. */
async function save(): Promise<void> {
  done.value = ''
  kept.value = false
  errors.value = {}
  const result = await saving.run('admin.systemorders.rules.save', {base: revision.value, rules: rules.value})
  if (!result) return
  if (result.result === 'invalid') {
    errors.value = result.errors
    chosen.value = failing.value[0] ?? chosen.value
    return
  }
  if (result.result === 'newer') {
    revision.value = result.revision
    rules.value = (result.rules ?? []).map(filled)
    chosen.value = 0
    done.value = t('admin.systemorders.rules.newer')
    return
  }
  revision.value = result.revision
  done.value = t(result.result === 'saved' ? 'admin.systemorders.rules.saved' : 'admin.systemorders.rules.unchanged')
  kept.value = !result.applied
}

/**
 * The problems of one rule, in the admin's language.
 *
 * @param index the rule's place in the list
 * @returns each problem with the field it concerns
 */
function problems(index: number): string[] {
  const prefix = `rules.${index}`
  return Object.entries(errors.value)
    .filter(([path]) => path === prefix || path.startsWith(`${prefix}.`) || path === 'rules' || path === '')
    .map(([path, problem]) => t('admin.systemorders.rules.problem', {
      field: path.startsWith(`${prefix}.`) ? fieldName(path.slice(prefix.length + 1)) : '-',
      problem: t(`admin.systemorders.rules.errors.${problem}`),
    }))
}

/**
 * The name of a field of a rule, an entry named by its place.
 *
 * @param field the field's path within the rule, such as `price.mode` or `items.2`
 * @returns the name
 */
function fieldName(field: string): string {
  const entry = /^items\.(\d+)$/.exec(field)
  if (entry) return t('admin.systemorders.rules.fields.entry', {number: Number(entry[1]) + 1})
  return t(`admin.systemorders.rules.fields.${field}`)
}
</script>

<template>
  <div class="grid min-w-0 items-start gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.6fr)]" data-testid="admin-system-rules">
    <PanelBox :aria-label="t('admin.systemorders.rules.title')">
      <template #head>
        <SectionHeader>{{ t('admin.systemorders.rules.title') }}</SectionHeader>
        <SecondaryButton v-if="editable" @click="add">{{ t('admin.systemorders.rules.add') }}</SecondaryButton>
      </template>
      <ActionNotice :problem="loading.problem.value" class="mx-3 mt-3 md:mx-4"/>
      <EmptyState v-if="rules.length === 0">{{ t('admin.systemorders.rules.none') }}</EmptyState>
      <PlainList v-else>
        <li v-for="(entry, index) in rules" :key="index">
          <RowButton :selected="chosen === index" class="flex-col items-start text-sm" @click="chosen = index">
            <span class="flex items-center gap-2">
              <span class="font-semibold">{{ entry.name || entry.id || t('admin.systemorders.rules.unnamed') }}</span>
              <NoticeBadge v-if="failing.includes(index)">{{ t('admin.systemorders.rules.hasProblem') }}</NoticeBadge>
            </span>
            <span class="text-ink-muted">{{ entry.id }} · {{ entry.items.map(itemLabel).join(', ') }}</span>
          </RowButton>
        </li>
      </PlainList>
      <div class="flex flex-col gap-2 border-t border-line-soft px-3 py-2.5 md:px-4">
        <Alert v-if="!editable" variant="notice">{{ t('admin.systemorders.rules.readOnly') }}</Alert>
        <template v-else>
          <Alert v-if="failing.length > 0" variant="problem">{{ t('admin.systemorders.rules.problems', {count: failing.length}) }}</Alert>
          <Alert v-if="incomplete >= 0" variant="notice">
            {{ t('admin.systemorders.rules.incomplete', {rule: rules[incomplete]?.id || t('admin.systemorders.rules.unnamed')}) }}
          </Alert>
          <PrimaryButton :disabled="saving.busy.value || incomplete >= 0" @click="save">{{ t('admin.systemorders.rules.save') }}</PrimaryButton>
        </template>
        <Alert v-if="done" variant="success">{{ done }}</Alert>
        <Alert v-if="kept" variant="notice">{{ t('admin.systemorders.rules.notApplied') }}</Alert>
        <ActionNotice :problem="saving.problem.value"/>
      </div>
    </PanelBox>
    <PanelBox v-if="rule" :aria-label="t('admin.systemorders.rules.form')">
      <template #head>
        <SectionHeader>{{ rule.name || rule.id || t('admin.systemorders.rules.unnamed') }}</SectionHeader>
        <SecondaryButton v-if="editable" @click="remove">{{ t('admin.systemorders.rules.remove') }}</SecondaryButton>
      </template>
      <div class="flex flex-col gap-3 px-3 py-3 md:px-4" data-testid="admin-system-rule">
        <Alert v-for="problem in problems(chosen)" :key="problem" variant="problem">{{ problem }}</Alert>
        <div class="grid gap-3 sm:grid-cols-2">
          <TextInput v-model="rule.id" :label="t('admin.systemorders.rules.fields.id')" :maxlength="64" :disabled="!editable"/>
          <TextInput v-model="rule.name" :label="t('admin.systemorders.rules.fields.name')" :disabled="!editable"/>
          <NumberInput v-model="rule.amount" :label="t('admin.systemorders.rules.fields.amount')" :min="1" :disabled="!editable"/>
        </div>
        <RuleItemsEditor v-model:rule="rules[chosen]!" :editable="editable"/>
        <CheckboxInput v-model="rule.enabled" :label="t('admin.systemorders.rules.fields.enabled')" :disabled="!editable"/>
        <div class="grid gap-3 sm:grid-cols-3">
          <SelectInput v-model="rule.price.mode" :label="t('admin.systemorders.rules.fields.price.mode')" :options="modes" :disabled="!editable"/>
          <DecimalInput v-if="rule.price.mode === 'FIXED'" v-model="rule.price.value" :label="t('admin.systemorders.rules.fields.price.value')" :min="0" :disabled="!editable"/>
          <DecimalInput v-else v-model="rule.price.factor" :label="t('admin.systemorders.rules.fields.price.factor')" :min="0" :disabled="!editable"/>
          <DecimalInput v-model="rule.price.jitter" :label="t('admin.systemorders.rules.fields.price.jitter')" :min="0" :max="0.9" :disabled="!editable"/>
          <DecimalInput v-model="rule.price.min" :label="t('admin.systemorders.rules.fields.price.min')" :min="0" :disabled="!editable"/>
          <DecimalInput v-model="rule.price.max" :label="t('admin.systemorders.rules.fields.price.max')" :min="0" :disabled="!editable"/>
        </div>
        <div class="grid gap-3 sm:grid-cols-3">
          <NumberInput v-model="rule.everyHours" :label="t('admin.systemorders.rules.fields.everyHours')" :min="1" :disabled="!editable"/>
          <NumberInput v-model="rule.lifetimeHours" :label="t('admin.systemorders.rules.fields.lifetimeHours')" :min="1" :disabled="!editable"/>
          <NumberInput v-model="rule.maxOpen" :label="t('admin.systemorders.rules.fields.maxOpen')" :min="1" :disabled="!editable"/>
        </div>
        <div class="grid gap-3 sm:grid-cols-3">
          <SelectInput v-model="rule.source.type" :label="t('admin.systemorders.rules.fields.source.type')" :options="sources" :disabled="!editable"/>
          <TextInput v-if="rule.source.type === 'ACCOUNT'" v-model="rule.source.account" :label="t('admin.systemorders.rules.fields.source.account')" :disabled="!editable"/>
          <DecimalInput v-model="rule.source.dailyBudget" :label="t('admin.systemorders.rules.fields.source.dailyBudget')" :min="0" :disabled="!editable"/>
        </div>
      </div>
    </PanelBox>
  </div>
</template>
