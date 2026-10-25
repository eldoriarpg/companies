/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import {Feature, MemberPermission, type MemberView} from '~/api/generated/protocol'
import LinkButton from '@/components/button/LinkButton.vue'
import PanelBox from '@/components/container/PanelBox.vue'
import PlainList from '@/components/container/PlainList.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import Alert from '@/components/feedback/Alert.vue'
import SearchInput from '@/components/input/SearchInput.vue'
import CapsLabel from '@/components/typography/CapsLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import {roleOf} from '~/composables/useShell'
import {count, money} from '~/util/format'
import {editable, mayKick} from '~/util/permissions'
import DisbandCompany from './DisbandCompany.vue'
import LeaveCompany from './LeaveCompany.vue'
import MemberActions from './MemberActions.vue'

/**
 * The company's members, most items delivered first: name, role and permissions, items delivered, orders and money
 * earned. Where the server offers it, a member's name opens what the player may do to them: change the permissions
 * the player holds, or remove them; the owner can also hand the company to a member, and dissolve it where the server
 * offers that. Every member but the owner can leave. Inviting a player stays in game.
 */
const props = defineProps<{
  members: readonly MemberView[]
  company: string
}>()

const emit = defineEmits<{
  left: []
  disbanded: []
}>()

const {t, te, locale} = useI18n()
const {supports, snapshot} = useDashboard()
const own = computed(() => snapshot.value?.player.permissions ?? [])
const manages = computed(() => supports(Feature['company.members']))
const mayEdit = computed(() => (manages.value ? editable(own.value) : []))
const owns = computed(() => own.value.includes(MemberPermission.OWNER))
const dissolves = computed(() => owns.value && supports(Feature['company.disband']))
const open = ref<string | null>(null)
const done = ref('')

/**
 * Whether the player may do anything to a member.
 *
 * @param member the member
 * @returns true when there is a permission to change or the member can be removed
 */
function actionable(member: MemberView): boolean {
  if (!manages.value || member.uuid === snapshot.value?.player.uuid || member.permissions.includes(MemberPermission.OWNER)) return false
  return mayEdit.value.length > 0 || mayKick(own.value, member.permissions) || owns.value
}

/**
 * Closes a member's actions with a message.
 *
 * @param text what happened
 */
function finish(text: string): void {
  done.value = text
  open.value = null
}
const COLUMNS = 'md:grid md:grid-cols-[minmax(0,1.5fr)_minmax(0,2fr)_5rem_4rem_6rem] md:items-center md:gap-3'

const sorted = computed(() => [...props.members]
    .sort((a, b) => b.stats.items - a.stats.items)
    .map(member => ({member, role: roleOf(member.permissions, {t, te})})))
const {query, found} = useListSearch(sorted, row => [row.member.name, row.role])
</script>

<template>
  <PanelBox :aria-label="t('company.members')">
    <template #head>
      <SectionHeader :note="String(members.length)">{{ t('company.members') }}</SectionHeader>
      <div class="w-full md:w-56">
        <SearchInput v-model="query" :label="t('company.searchMembers')"/>
      </div>
    </template>
    <div v-if="found.length > 0" :class="COLUMNS" class="hidden border-b md:grid border-line-soft px-4 py-2" aria-hidden="true">
      <CapsLabel>{{ t('company.columnMember') }}</CapsLabel>
      <CapsLabel>{{ t('company.columnPermissions') }}</CapsLabel>
      <CapsLabel class="text-right">{{ t('company.columnItems') }}</CapsLabel>
      <CapsLabel class="text-right">{{ t('company.columnOrders') }}</CapsLabel>
      <CapsLabel class="text-right">{{ t('company.columnEarned') }}</CapsLabel>
    </div>
    <Alert v-if="done" variant="success" class="m-3 md:mx-4">{{ done }}</Alert>
    <EmptyState v-if="found.length === 0">{{ t('common.noMatch') }}</EmptyState>
    <PlainList v-else data-testid="members">
      <li v-for="row in found" :key="row.member.uuid" :class="COLUMNS" class="flex flex-wrap items-center justify-between gap-3 border-b border-line-soft px-3 py-2.5 last:border-b-0 md:px-4">
        <span class="flex min-w-0 flex-col gap-0.5 md:contents">
          <LinkButton
              v-if="actionable(row.member)"
              :aria-expanded="open === row.member.uuid"
              class="truncate text-sm"
              @click="open = open === row.member.uuid ? null : row.member.uuid"
          >{{ row.member.name }}</LinkButton>
          <span v-else class="truncate text-sm font-semibold">{{ row.member.name }}</span>
          <span class="truncate text-xs text-ink-muted md:text-sm md:text-ink-soft">{{ row.role }}</span>
        </span>
        <span class="tabular shrink-0 text-right text-sm">{{ count(row.member.stats.items, locale) }}</span>
        <span class="tabular hidden text-right text-sm md:block">{{ count(row.member.stats.orders, locale) }}</span>
        <span class="tabular hidden text-right text-sm md:block">{{ money(row.member.stats.earned, locale) }}</span>
        <MemberActions
            v-if="open === row.member.uuid"
            :member="row.member"
            :editable="mayEdit"
            :kickable="mayKick(own, row.member.permissions)"
            :transferable="owns"
            @saved="name => finish(t('company.permissionsSaved', {name}))"
            @kicked="name => finish(t('company.kicked', {name}))"
            @transferred="name => finish(t('company.transferred', {name}))"
        />
      </li>
    </PlainList>
    <MutedText tag="p" class="border-t border-line-soft px-3 py-2.5 md:px-4">{{ t('company.inviteInGame') }}</MutedText>
    <LeaveCompany v-if="manages && !owns" :company="company" @left="emit('left')"/>
    <DisbandCompany v-if="dissolves" :company="company" @disbanded="emit('disbanded')"/>
  </PanelBox>
</template>
