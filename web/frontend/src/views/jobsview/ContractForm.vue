/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import PostButtons from './PostButtons.vue'
import type {AreaList, JobDetails} from '~/api/gateway'
import ActionNotice from '@/components/feedback/ActionNotice.vue'
import CheckboxInput from '@/components/input/CheckboxInput.vue'
import DecimalInput from '@/components/input/DecimalInput.vue'
import NumberInput from '@/components/input/NumberInput.vue'
import SelectInput from '@/components/input/SelectInput.vue'
import TextAreaInput from '@/components/input/TextAreaInput.vue'
import TextInput from '@/components/input/TextInput.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'

/**
 * Posts a contract: work described in words, which a company takes like an order and the owner approves when it is
 * done. The price and the order fee are taken at once. It may be placed at one of the player's stored areas or where
 * they were last in game; every company then sees that place.
 */
const emit = defineEmits<{
  posted: [details: JobDetails]
  close: []
}>()

const {t} = useI18n()
const action = useAction()
const loading = useAction()
const title = ref('')
const description = ref('')
const price = ref<number | undefined>()
const hours = ref<number | undefined>(24)
const place = ref(false)
const area = ref('')
const areas = ref<AreaList | null>(null)
const areaOptions = computed(() => [
  {value: '', label: t('jobs.contract.noArea')},
  ...(areas.value?.areas ?? []).map(stored => ({value: stored.name, label: stored.name})),
])

onMounted(async () => {
  areas.value = await loading.run('jobs.areas.list', {})
})
const ready = computed(() => title.value.trim().length > 0 && (price.value ?? 0) > 0 && (hours.value ?? 0) >= 1)

async function post(draft = false): Promise<void> {
  if (!ready.value) return
  const result = await action.run('jobs.contract', {
    draft,
    title: title.value.trim(),
    description: description.value,
    price: price.value ?? 0,
    hours: hours.value ?? 0,
    place: area.value === '' && place.value,
    ...(area.value === '' ? {} : {area: area.value}),
  })
  if (result) emit('posted', result)
}
</script>

<template>
  <section class="flex flex-col gap-3 border border-line bg-surface p-4" data-testid="contract-form">
    <SectionHeader>{{ t('jobs.contract.title') }}</SectionHeader>
    <MutedText tag="p">{{ t('jobs.contract.intro') }}</MutedText>
    <TextInput v-model="title" :label="t('jobs.contract.name')" :maxlength="64"/>
    <TextAreaInput v-model="description" :label="t('jobs.contract.description')" :maxlength="512" :rows="4"/>
    <div class="grid grid-cols-2 gap-3">
      <DecimalInput v-model="price" :label="t('jobs.contract.price')" :min="0.01" data-testid="contract-price"/>
      <NumberInput v-model="hours" :label="t('jobs.contract.hours')" :min="1"/>
    </div>
    <SelectInput v-if="(areas?.areas.length ?? 0) > 0" v-model="area" :label="t('jobs.contract.area')" :options="areaOptions" data-testid="contract-area"/>
    <CheckboxInput v-if="area === ''" v-model="place" :label="t('jobs.contract.place')" data-testid="contract-place"/>
    <MutedText tag="p">{{ t('jobs.contract.charged') }}</MutedText>
    <ActionNotice :problem="action.problem.value"/>
    <PostButtons :disabled="!ready || action.busy.value" test-id="contract" @post="post" @back="emit('close')"/>
  </section>
</template>
