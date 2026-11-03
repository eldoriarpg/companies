/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {AreaJobKind, AreaList, AreaPreview, AreaRow, JobDetails, SchematicFit} from '~/api/gateway'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PostButtons from './PostButtons.vue'
import PanelBox from '@/components/container/PanelBox.vue'
import PlainList from '@/components/container/PlainList.vue'
import ActionNotice from '@/components/feedback/ActionNotice.vue'
import CheckboxInput from '@/components/input/CheckboxInput.vue'
import DecimalInput from '@/components/input/DecimalInput.vue'
import NumberInput from '@/components/input/NumberInput.vue'
import SelectInput from '@/components/input/SelectInput.vue'
import TextInput from '@/components/input/TextInput.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import AreaMap from './AreaMap.vue'
import type {MapCorner} from './AreaMap.vue'
import {money} from '~/util/format'

/**
 * The areas the player stored in game with `/order job area`: see one from above, post a dig, terraform, harvest or
 * build job on it, or delete it. A build job's schematic is chosen, turned and placed on the map; it stands on the
 * ground as if the player stood at its origin in game. A job is posted on the server whose world holds the area, where
 * its height and land are checked as in game; the price and the order fee are taken at once.
 */
const emit = defineEmits<{
  posted: [details: JobDetails]
}>()

const {t, locale} = useI18n()
const loading = useAction()
const action = useAction()
const reading = useAction()
const measuring = useAction()
const preview = ref<AreaPreview | null>(null)
const schematic = ref('')
const rotation = ref('0')
const fit = ref<SchematicFit | null>(null)
const place = ref<MapCorner>({x: 0, z: 0})
const belowCost = ref(false)
const supplied = ref(false)
const rotations = ['0', '90', '180', '270'].map(value => ({value, label: `${value}°`}))
const schematics = computed(() => [
  {value: '', label: t('jobs.areas.chooseSchematic')},
  ...(preview.value?.own ?? []).map(name => ({value: name, label: t('jobs.areas.ownSchematic', {name})})),
  ...(preview.value?.server ?? [])
      .filter(name => !(preview.value?.own ?? []).some(own => own.toLowerCase() === name.toLowerCase()))
      .map(name => ({value: name, label: name})),
])
const footprint = computed(() => fit.value ? {...place.value, width: fit.value.width, length: fit.value.length} : null)
const list = ref<AreaList | null>(null)
const chosen = ref<AreaRow | null>(null)
const kind = ref<string>('dig')
const title = ref('')
const price = ref<number | undefined>()
const hours = ref<number | undefined>(24)
const y = ref<number | undefined>()
const surface = ref('')
const unitPrice = ref<number | undefined>()
const kinds = computed(() => (['dig', 'terraform', 'harvest', 'build'] as const).map(value => ({value, label: t(`jobs.kind.${value}`)})))
const ready = computed(() => chosen.value !== null
    && title.value.trim().length > 0
    && (price.value ?? 0) > 0
    && (hours.value ?? 0) >= 1
    && (kind.value !== 'terraform' || y.value !== undefined)
    && (kind.value !== 'harvest' || (unitPrice.value ?? 0) > 0)
    && (kind.value !== 'build' || (fit.value !== null && (!(fit.value.cost > (price.value ?? 0)) || belowCost.value || supplied.value))))

onMounted(async () => {
  list.value = await loading.run('jobs.areas.list', {})
})

/**
 * Opens the form for a job on an area.
 */
async function open(area: AreaRow): Promise<void> {
  chosen.value = area
  y.value = undefined
  preview.value = null
  fit.value = null
  schematic.value = ''
  place.value = {x: 0, z: 0}
  await draw()
}

let openedWhileDrawing = false

/**
 * Draws the chosen area. An area opened while another is drawn is drawn once that one is done, and an answer for an
 * area that is no longer chosen is dropped.
 */
async function draw(): Promise<void> {
  if (reading.busy.value) {
    openedWhileDrawing = true
    return
  }
  const area = chosen.value
  if (!area) return
  const drawn = await reading.run('jobs.areas.preview', {node: area.node, area: area.name})
  if (openedWhileDrawing) {
    openedWhileDrawing = false
    await draw()
    return
  }
  if (chosen.value === area) preview.value = drawn
}

let changedWhileMeasuring = false

/**
 * Reads the size and cost of the chosen schematic, turned, and keeps its place on the map inside the area. A choice
 * made while one is read is read once that one is done, so only the latest choice counts.
 */
async function measure(): Promise<void> {
  fit.value = null
  belowCost.value = false
  if (measuring.busy.value) {
    changedWhileMeasuring = true
    return
  }
  const area = chosen.value
  if (!area || schematic.value === '') return
  const measured = await measuring.run('jobs.areas.schematic', {node: area.node, area: area.name, schematic: schematic.value, rotation: Number(rotation.value)})
  if (changedWhileMeasuring) {
    changedWhileMeasuring = false
    await measure()
    return
  }
  if (!measured || !preview.value) return
  fit.value = measured
  place.value = {
    x: Math.min(place.value.x, Math.max(0, preview.value.width - measured.width)),
    z: Math.min(place.value.z, Math.max(0, preview.value.length - measured.length)),
  }
}

watch([schematic, rotation], measure)

async function post(draft = false): Promise<void> {
  const area = chosen.value
  if (!ready.value || !area) return
  const result = kind.value === 'build'
    ? await action.run('jobs.areas.build', {
      node: area.node,
      area: area.name,
      schematic: schematic.value,
      rotation: Number(rotation.value),
      x: place.value.x,
      z: place.value.z,
      title: title.value.trim(),
      price: price.value ?? 0,
      hours: hours.value ?? 0,
      confirm: belowCost.value,
      draft,
      supplied: supplied.value,
    })
    : await action.run('jobs.areas.post', {
    node: area.node,
    area: area.name,
    kind: kind.value as AreaJobKind,
    title: title.value.trim(),
    price: price.value ?? 0,
    hours: hours.value ?? 0,
    ...(kind.value === 'terraform' ? {y: y.value ?? 0, surface: surface.value.trim()} : {}),
    ...(kind.value === 'harvest' ? {unitPrice: unitPrice.value ?? 0} : {}),
    draft,
  })
  if (!result) return
  chosen.value = null
  title.value = ''
  emit('posted', result)
}

async function remove(area: AreaRow): Promise<void> {
  const changed = await action.run('jobs.areas.delete', {name: area.name})
  if (!changed) return
  list.value = changed
  if (chosen.value?.name === area.name) chosen.value = null
}
</script>

<template>
  <PanelBox v-if="list" :aria-label="t('jobs.areas.title')" data-testid="job-areas">
    <template #head>
      <SectionHeader>{{ t('jobs.areas.title') }}</SectionHeader>
    </template>
    <div class="flex flex-col gap-3 p-3 text-sm">
      <MutedText tag="p">{{ t('jobs.areas.intro', {max: list.max}) }}</MutedText>
      <PlainList v-if="list.areas.length > 0">
        <li v-for="area in list.areas" :key="area.name" class="flex items-center justify-between gap-3 py-1">
          <span class="flex min-w-0 flex-col">
            <span class="truncate font-semibold">{{ area.name }}</span>
            <span class="truncate text-[0.6875rem] text-ink-muted">{{ t('jobs.areas.size', {width: area.width, height: area.height, length: area.length, world: area.world, center: area.center}) }}</span>
          </span>
          <span class="flex shrink-0 gap-2">
            <PrimaryButton :disabled="action.busy.value" :data-testid="`job-areas-open-${area.name}`" @click="open(area)">{{ t('jobs.areas.open') }}</PrimaryButton>
            <SecondaryButton :disabled="action.busy.value" :data-testid="`job-areas-delete-${area.name}`" @click="remove(area)">{{ t('jobs.areas.delete') }}</SecondaryButton>
          </span>
        </li>
      </PlainList>
      <MutedText v-else tag="p">{{ t('jobs.areas.none') }}</MutedText>
      <section v-if="chosen" class="flex flex-col gap-3 border border-line p-3" data-testid="job-areas-form">
        <SectionHeader>{{ t('jobs.areas.formTitle', {name: chosen.name}) }}</SectionHeader>
        <AreaMap v-if="preview" :preview="preview" :footprint="kind === 'build' ? footprint : null" @place="(x, z) => place = {x, z}"/>
        <MutedText v-else-if="reading.busy.value" tag="p">{{ t('jobs.areas.drawing') }}</MutedText>
        <ActionNotice :problem="reading.problem.value ?? measuring.problem.value"/>
        <SelectInput v-model="kind" :label="t('jobs.areas.kind')" :options="kinds" data-testid="job-areas-kind"/>
        <MutedText tag="p">{{ t(`jobs.areas.explain.${kind}`) }}</MutedText>
        <TextInput v-model="title" :label="t('jobs.contract.name')" :maxlength="64" data-testid="job-areas-name"/>
        <div class="grid grid-cols-2 gap-3">
          <DecimalInput v-model="price" :label="t(kind === 'harvest' ? 'jobs.areas.budget' : 'jobs.contract.price')" :min="0.01" data-testid="job-areas-price"/>
          <NumberInput v-model="hours" :label="t('jobs.contract.hours')" :min="1"/>
        </div>
        <div v-if="kind === 'terraform'" class="grid grid-cols-2 gap-3">
          <NumberInput v-model="y" :label="t('jobs.areas.y')" data-testid="job-areas-y"/>
          <TextInput v-model="surface" :label="t('jobs.areas.surface')" :maxlength="64"/>
        </div>
        <DecimalInput v-if="kind === 'harvest'" v-model="unitPrice" :label="t('jobs.areas.unitPrice')" :min="0.01" data-testid="job-areas-unit"/>
        <template v-if="kind === 'build'">
          <div class="grid grid-cols-2 gap-3">
            <SelectInput v-model="schematic" :label="t('jobs.areas.schematic')" :options="schematics" data-testid="job-areas-schematic"/>
            <SelectInput v-model="rotation" :label="t('jobs.areas.rotation')" :options="rotations" data-testid="job-areas-rotation"/>
          </div>
          <MutedText v-if="fit" tag="p" data-testid="job-areas-fit">{{ t('jobs.areas.fit', {width: fit.width, height: fit.height, length: fit.length, blocks: fit.blocks, cost: money(fit.cost, locale)}) }}</MutedText>
          <MutedText v-if="fit && fit.unpriced > 0" tag="p">{{ t('jobs.areas.fitUnpriced', {count: fit.unpriced}) }}</MutedText>
          <CheckboxInput v-model="supplied" :label="t('jobs.areas.supplied')" data-testid="job-areas-supplied"/>
          <MutedText v-if="supplied && fit?.unmakeable?.length" tag="p" data-testid="job-areas-unmakeable">{{ t('jobs.areas.unmakeable', {types: fit.unmakeable.join(', ')}) }}</MutedText>
          <CheckboxInput v-if="!supplied && fit && fit.cost > (price ?? 0)" v-model="belowCost" :label="t('jobs.areas.belowCost')" data-testid="job-areas-below"/>
        </template>
        <MutedText tag="p">{{ t('jobs.contract.charged') }}</MutedText>
        <PostButtons :disabled="!ready || action.busy.value" test-id="job-areas" @post="post" @back="chosen = null"/>
      </section>
      <ActionNotice :problem="action.problem.value"/>
    </div>
  </PanelBox>
</template>
