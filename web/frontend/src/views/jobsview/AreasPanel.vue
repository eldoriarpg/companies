/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {AreaJobKind, AreaList, AreaRow, JobDetails} from '~/api/gateway'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PanelBox from '@/components/container/PanelBox.vue'
import PlainList from '@/components/container/PlainList.vue'
import ActionNotice from '@/components/feedback/ActionNotice.vue'
import DecimalInput from '@/components/input/DecimalInput.vue'
import NumberInput from '@/components/input/NumberInput.vue'
import SelectInput from '@/components/input/SelectInput.vue'
import TextInput from '@/components/input/TextInput.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'

/**
 * The areas the player stored in game with `/order job area`: post a dig, terraform or harvest job on one, or delete
 * it. A job is posted on the server whose world holds the area, where its height and land are checked as in game; the
 * price and the order fee are taken at once.
 */
const emit = defineEmits<{
  posted: [details: JobDetails]
}>()

const {t} = useI18n()
const loading = useAction()
const action = useAction()
const list = ref<AreaList | null>(null)
const chosen = ref<AreaRow | null>(null)
const kind = ref<string>('dig')
const title = ref('')
const price = ref<number | undefined>()
const hours = ref<number | undefined>(24)
const y = ref<number | undefined>()
const surface = ref('')
const unitPrice = ref<number | undefined>()
const kinds = computed(() => (['dig', 'terraform', 'harvest'] as const).map(value => ({value, label: t(`jobs.kind.${value}`)})))
const ready = computed(() => chosen.value !== null
    && title.value.trim().length > 0
    && (price.value ?? 0) > 0
    && (hours.value ?? 0) >= 1
    && (kind.value !== 'terraform' || y.value !== undefined)
    && (kind.value !== 'harvest' || (unitPrice.value ?? 0) > 0))

onMounted(async () => {
  list.value = await loading.run('jobs.areas.list', {})
})

/**
 * Opens the form for a job on an area.
 */
function open(area: AreaRow): void {
  chosen.value = area
  y.value = undefined
}

async function post(): Promise<void> {
  const area = chosen.value
  if (!ready.value || !area) return
  const result = await action.run('jobs.areas.post', {
    node: area.node,
    area: area.name,
    kind: kind.value as AreaJobKind,
    title: title.value.trim(),
    price: price.value ?? 0,
    hours: hours.value ?? 0,
    ...(kind.value === 'terraform' ? {y: y.value ?? 0, surface: surface.value.trim()} : {}),
    ...(kind.value === 'harvest' ? {unitPrice: unitPrice.value ?? 0} : {}),
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
        <MutedText tag="p">{{ t('jobs.contract.charged') }}</MutedText>
        <ButtonRow>
          <PrimaryButton :disabled="!ready || action.busy.value" data-testid="job-areas-post" @click="post">{{ t('jobs.contract.post') }}</PrimaryButton>
          <SecondaryButton @click="chosen = null">{{ t('jobs.contract.cancel') }}</SecondaryButton>
        </ButtonRow>
      </section>
      <ActionNotice :problem="action.problem.value"/>
    </div>
  </PanelBox>
</template>
