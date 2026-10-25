/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {AdminBoardPreset} from '~/api/gateway'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PanelBox from '@/components/container/PanelBox.vue'
import PlainList from '@/components/container/PlainList.vue'
import ActionNotice from '@/components/feedback/ActionNotice.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SelectInput from '@/components/input/SelectInput.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import AdminNpcPanel from './AdminNpcPanel.vue'
import {nodeLabel} from '~/util/nodes'

/**
 * The order boards of one server, as `/companyadmin board` offers them there: the presets with their open orders, the
 * board signs written again, and the NPCs that open a board. Board signs and NPCs belong to a server, so on a network
 * the admin picks the server first; a single server needs no choice.
 */
const {t} = useI18n()
const {server} = useDashboard()
const loading = useAction()
const reloading = useAction()
const presets = ref<AdminBoardPreset[] | null>(null)
const done = ref('')
const nodes = computed(() => server.value?.nodes ?? [])
const choices = computed(() => nodes.value.map(node => ({value: node.uid, label: nodeLabel(node, t(`admin.servers.role.${node.role}`))})))
const node = ref('')
const target = computed(() => (nodes.value.length > 1 && node.value ? {node: node.value} : {}))

watch(nodes, list => {
  if (!list.some(entry => entry.uid === node.value)) node.value = list[0]?.uid ?? ''
}, {immediate: true})
watch(() => target.value.node ?? '', load, {immediate: true})

/** Loads the presets of the chosen server. */
async function load(): Promise<void> {
  done.value = ''
  presets.value = null
  const result = await loading.run('admin.boards.presets', target.value)
  if (result) presets.value = result.presets
}

/** Writes the board signs of the chosen server again. */
async function reload(): Promise<void> {
  done.value = ''
  if (await reloading.run('admin.boards.reload', target.value)) done.value = t('admin.boards.reloaded')
}
</script>

<template>
  <div class="flex min-w-0 flex-col gap-4">
    <SelectInput v-if="nodes.length > 1" v-model="node" :label="t('admin.boards.server')" :options="choices"/>
    <div class="grid items-start gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.4fr)]">
      <PanelBox :aria-label="t('admin.boards.presets')">
        <template #head>
          <SectionHeader>{{ t('admin.boards.presets') }}</SectionHeader>
        </template>
        <ActionNotice :problem="loading.problem.value" class="mx-3 mt-3 md:mx-4"/>
        <template v-if="presets">
          <EmptyState v-if="presets.length === 0">{{ t('admin.boards.noPresets') }}</EmptyState>
          <PlainList v-else data-testid="admin-presets">
            <li v-for="preset in presets" :key="preset.name" class="flex justify-between gap-2 border-t border-line-soft px-3 py-2.5 text-sm md:px-4">
              <span class="min-w-0 truncate"><span class="font-semibold">{{ preset.name }}</span> · {{ preset.label }}</span>
              <span class="tabular shrink-0">{{ t('admin.boards.open', {count: preset.open}) }}</span>
            </li>
          </PlainList>
        </template>
        <div class="flex flex-col gap-2 border-t border-line-soft px-3 py-2.5 md:px-4">
          <SecondaryButton :disabled="reloading.busy.value" @click="reload">{{ t('admin.boards.reload') }}</SecondaryButton>
          <Alert v-if="done" variant="success">{{ done }}</Alert>
          <ActionNotice :problem="reloading.problem.value"/>
        </div>
      </PanelBox>
      <AdminNpcPanel :key="node" :target="target"/>
    </div>
  </div>
</template>
