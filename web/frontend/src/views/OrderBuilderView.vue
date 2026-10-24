/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PanelBox from '@/components/container/PanelBox.vue'
import ActionNotice from '@/components/feedback/ActionNotice.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import TextInput from '@/components/input/TextInput.vue'
import ViewContent from '@/components/layout/ViewContent.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import AddLineDialog from './orderbuilder/AddLineDialog.vue'
import BuilderLine from './orderbuilder/BuilderLine.vue'
import RepetitionFields from './orderbuilder/RepetitionFields.vue'
import {Feature} from '~/api/generated/protocol'
import {money} from '~/util/format'
import {type LineSource, itemOf} from '~/util/orderDraft'

/**
 * Placing an order from the dashboard: a name, the lines with amount and price of one item, and whether it repeats.
 * The plugin quotes the order while it is built and checks it as in game; placing it charges the player, who may be
 * offline, and opens the portfolio, where the new order shows up under their orders. A server that does not offer
 * placing orders gets a notice instead.
 */
const MAX_NAME = 31

const {t, locale} = useI18n()
const router = useRouter()
const builder = useOrderBuilder()
const market = useMarket()
const {supports} = useDashboard()
const offered = computed(() => supports(Feature['orders.create']))
const adding = ref(false)
const editing = ref<number | null>(null)
const editedSource = computed(() => builder.lines.value.find(line => line.id === editing.value)?.source ?? null)

watch(offered, isOffered => {
  if (!isOffered) return
  void builder.load()
  void market.loadTop()
}, {immediate: true})

/**
 * Opens the dialog for a new line, or for changing a line's item.
 *
 * @param id the line to change, none for a new one
 */
function openDialog(id: number | null): void {
  editing.value = id
  adding.value = true
}

/**
 * Adds a line, priced at the market's average where it knows the material.
 *
 * @param source the line's item
 */
function add(source: LineSource): void {
  if (editing.value !== null) builder.replaceLine(editing.value, source)
  else builder.addLine(source, market.averages.value.get(itemOf(source).material))
}

/** Places the order and opens the player's orders. */
async function place(): Promise<void> {
  const name = builder.name.value.trim()
  if (await builder.submit() === null) return
  await router.push({name: 'portfolio', query: {placed: name}})
}
</script>

<template>
  <ViewContent :title="t('builder.title')" standalone>
    <Alert v-if="!offered" variant="notice">{{ t('builder.disabled') }}</Alert>
    <Alert v-else-if="builder.status.value === 'failed'" variant="problem">
      <span>{{ t('builder.loadFailed') }}</span>
      <ButtonRow>
        <SecondaryButton @click="builder.reload()">{{ t('builder.retry') }}</SecondaryButton>
      </ButtonRow>
    </Alert>
    <EmptyState v-else-if="builder.status.value !== 'ready'" loading/>
    <template v-if="builder.status.value === 'ready' && builder.catalogue.value && builder.known.value">
      <PanelBox padded>
        <TextInput v-model="builder.name.value" :label="t('builder.name')" :hint="t('builder.nameHint')" :maxlength="MAX_NAME"/>
      </PanelBox>
      <PanelBox :aria-label="t('builder.lines')">
        <template #head>
          <SectionHeader :note="t('builder.linesNote')">{{ t('builder.lines') }}</SectionHeader>
          <SecondaryButton icon="plus" @click="openDialog(null)">{{ t('builder.addLine') }}</SecondaryButton>
        </template>
        <EmptyState v-if="builder.lines.value.length === 0">{{ t('builder.noLines') }}</EmptyState>
        <ul v-else class="m-0 list-none p-0">
          <BuilderLine
              v-for="(line, index) in builder.lines.value"
              :key="line.id"
              v-model="builder.lines.value[index]!"
              :catalogue="builder.catalogue.value"
              :average="market.averages.value.get(itemOf(line.source).material)"
              @remove="builder.removeLine(line.id)"
              @edit="openDialog(line.id)"
          />
        </ul>
      </PanelBox>
      <PanelBox padded>
        <RepetitionFields v-model="builder.repetition.value"/>
      </PanelBox>
      <PanelBox padded :aria-label="t('builder.cost')">
        <SectionHeader>{{ t('builder.cost') }}</SectionHeader>
        <div aria-live="polite">
          <MutedText v-if="builder.quote.value.status === 'incomplete'" tag="p">{{ t('builder.incomplete') }}</MutedText>
          <span v-else-if="builder.quote.value.status === 'quoting'" class="flex items-center gap-2 text-sm text-ink-muted">
            <Spinner size="sm"/>{{ t('builder.quoting') }}
          </span>
          <Alert v-else-if="builder.quote.value.status === 'refused'" variant="problem">{{ builder.quote.value.text }}</Alert>
          <Alert v-else-if="builder.quote.value.status === 'failed'" variant="problem">{{ t('builder.quoteFailed') }}</Alert>
          <dl v-else class="m-0 grid grid-cols-[1fr_auto] gap-x-4 gap-y-1 text-sm">
            <dt class="text-ink-muted">{{ t('builder.price') }}</dt>
            <dd class="tabular m-0 text-right">{{ money(builder.quote.value.quote.price, locale) }}</dd>
            <dt class="text-ink-muted">{{ t('builder.fee') }}</dt>
            <dd class="tabular m-0 text-right">{{ money(builder.quote.value.quote.fee, locale) }}</dd>
            <dt class="font-semibold">{{ t('builder.total') }}</dt>
            <dd class="tabular m-0 text-right font-semibold">{{ money(builder.quote.value.quote.total, locale) }}</dd>
          </dl>
        </div>
        <ActionNotice :problem="builder.problem.value"/>
        <ButtonRow>
          <PrimaryButton
              :disabled="builder.quote.value.status !== 'quoted' || builder.placing.value"
              @click="place"
          >
            {{ builder.quote.value.status === 'quoted' ? t('builder.place', {total: money(builder.quote.value.quote.total, locale)}) : t('builder.placePlain') }}
          </PrimaryButton>
        </ButtonRow>
        <MutedText tag="p">{{ t('builder.placeNote') }}</MutedText>
      </PanelBox>
      <AddLineDialog v-model:open="adding" :editing="editedSource" :catalogue="builder.catalogue.value" :known="builder.known.value" @add="add"/>
    </template>
  </ViewContent>
</template>
