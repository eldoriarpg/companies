/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {AdminNpcBoards, NodeChoice} from '~/api/gateway'
import LinkButton from '@/components/button/LinkButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PanelBox from '@/components/container/PanelBox.vue'
import PlainList from '@/components/container/PlainList.vue'
import ActionNotice from '@/components/feedback/ActionNotice.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SelectInput from '@/components/input/SelectInput.vue'
import TextInput from '@/components/input/TextInput.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'

/**
 * The NPCs of one server that open a board, as `/companyadmin board npc` offers them: the bound NPCs, each unbound with
 * one click, and an NPC of a loaded NPC plugin bound to a preset, to materials or, left empty, to every open order. An
 * NPC is named as its plugin names it: a Citizens id or a FancyNpcs name.
 */
const props = defineProps<{
  target: NodeChoice
}>()

const {t} = useI18n()
const loading = useAction()
const changing = useAction()
const npcs = ref<AdminNpcBoards | null>(null)
const hook = ref('')
const npc = ref('')
const board = ref('')
const done = ref('')
const hooks = computed(() => (npcs.value?.hooks ?? []).map(name => ({value: name, label: name})))

onMounted(load)

/** Loads the bound NPCs and the loaded NPC plugins. */
async function load(): Promise<void> {
  const result = await loading.run('admin.boards.npc.list', props.target)
  if (!result) return
  npcs.value = result
  if (!result.hooks.includes(hook.value)) hook.value = result.hooks[0] ?? ''
}

/** Binds the typed NPC. */
async function link(): Promise<void> {
  done.value = ''
  const result = await changing.run('admin.boards.npc.link', {...props.target, hook: hook.value, npc: npc.value.trim(), board: board.value.trim()})
  if (!result) return
  npc.value = ''
  board.value = ''
  done.value = t('admin.boards.linked', {npc: result.npc, hook: result.hook})
  await load()
}

/**
 * Unbinds an NPC.
 *
 * @param hookName the NPC plugin
 * @param npcKey the NPC
 */
async function unlink(hookName: string, npcKey: string): Promise<void> {
  done.value = ''
  if (await changing.run('admin.boards.npc.unlink', {...props.target, hook: hookName, npc: npcKey})) {
    done.value = t('admin.boards.unlinked', {npc: npcKey, hook: hookName})
    await load()
  }
}
</script>

<template>
  <PanelBox :aria-label="t('admin.boards.npcs')" data-testid="admin-npcs">
    <template #head>
      <SectionHeader>{{ t('admin.boards.npcs') }}</SectionHeader>
    </template>
    <ActionNotice :problem="loading.problem.value" class="mx-3 mt-3 md:mx-4"/>
    <template v-if="npcs">
      <EmptyState v-if="npcs.bindings.length === 0">{{ t('admin.boards.noNpcs') }}</EmptyState>
      <PlainList v-else>
        <li v-for="binding in npcs.bindings" :key="`${binding.hook}-${binding.npc}`" class="flex items-center justify-between gap-2 border-t border-line-soft px-3 py-2.5 text-sm md:px-4">
          <span class="min-w-0 truncate">{{ binding.hook }} {{ binding.npc }} → {{ binding.board || t('admin.boards.everyOrder') }}</span>
          <LinkButton :disabled="changing.busy.value" @click="unlink(binding.hook, binding.npc)">{{ t('admin.boards.unlink') }}</LinkButton>
        </li>
      </PlainList>
      <Alert v-if="npcs.hooks.length === 0" variant="notice" class="mx-3 my-3 md:mx-4">{{ t('admin.boards.noHooks') }}</Alert>
      <form v-else class="flex flex-wrap items-end gap-2 border-t border-line-soft px-3 py-3 md:px-4" @submit.prevent="link">
        <SelectInput v-model="hook" :label="t('admin.boards.hook')" :options="hooks"/>
        <TextInput v-model="npc" :label="t('admin.boards.npc')"/>
        <TextInput v-model="board" :label="t('admin.boards.board')" :hint="t('admin.boards.boardHint')"/>
        <SecondaryButton type="submit" :disabled="npc.trim() === '' || changing.busy.value">{{ t('admin.boards.link') }}</SecondaryButton>
      </form>
    </template>
    <div v-if="done || changing.problem.value" class="px-3 pb-3 md:px-4">
      <Alert v-if="done" variant="success">{{ done }}</Alert>
      <ActionNotice :problem="changing.problem.value"/>
    </div>
  </PanelBox>
</template>
