/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import {useNow} from '@vueuse/core'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PanelBox from '@/components/container/PanelBox.vue'
import PlainList from '@/components/container/PlainList.vue'
import ActionNotice from '@/components/feedback/ActionNotice.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import Alert from '@/components/feedback/Alert.vue'
import CapsLabel from '@/components/typography/CapsLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import {when} from '~/util/format'

/**
 * Who wants in: the open applications with their message to accept or deny, and the invites the company sent, which
 * can be taken back. For members who may invite; the plugin checks every answer again. No patch carries them, so the
 * lists are loaded again after each answer and whenever the members change, as when another member accepted someone.
 * How players join is set with the profile.
 */
const props = defineProps<{
  members: number
}>()

const {t, locale} = useI18n()
const now = useNow({interval: 60_000})
const applications = useApplications()
const action = useAction()
const done = ref('')

onMounted(() => void applications.load())
watch(() => props.members, () => void applications.load())

/**
 * Answers an application or takes back an invite, then loads the lists again.
 *
 * @param name the action
 * @param player the player it is about
 * @param text what to say when it is done
 */
async function answer(name: 'company.applications.accept' | 'company.applications.deny' | 'company.members.revoke', player: string, text: string): Promise<void> {
  done.value = ''
  if (await action.run(name, {player})) done.value = text
  await applications.load()
}
</script>

<template>
  <PanelBox :aria-label="t('company.applications')">
    <template #head>
      <SectionHeader :note="String(applications.result.value?.total ?? 0)">{{ t('company.applications') }}</SectionHeader>
    </template>
    <div class="flex flex-col gap-2 px-3 pt-3 md:px-4 empty:hidden">
      <Alert v-if="done" variant="success">{{ done }}</Alert>
      <ActionNotice :problem="action.problem.value"/>
    </div>
    <Alert v-if="applications.status.value === 'failed'" variant="problem" class="m-3 md:mx-4">{{ t('company.applicationsFailed') }}</Alert>
    <template v-else-if="applications.result.value">
      <EmptyState v-if="applications.result.value.entries.length === 0">{{ t('company.noApplications') }}</EmptyState>
      <PlainList v-else data-testid="applications">
        <li v-for="entry in applications.result.value.entries" :key="entry.player.uuid" class="flex flex-col gap-2 border-b border-line-soft px-3 py-3 last:border-b-0 md:px-4">
          <div class="flex justify-between gap-2">
            <span class="truncate text-sm font-semibold">{{ entry.player.name }}</span>
            <span class="tabular shrink-0 text-xs text-ink-muted">{{ when(entry.created, now, locale) }}</span>
          </div>
          <MutedText v-if="entry.message" tag="p">{{ entry.message }}</MutedText>
          <ButtonRow>
            <PrimaryButton :disabled="action.busy.value" @click="answer('company.applications.accept', entry.player.uuid, t('company.accepted', {name: entry.player.name}))">{{ t('company.accept') }}</PrimaryButton>
            <SecondaryButton :disabled="action.busy.value" @click="answer('company.applications.deny', entry.player.uuid, t('company.denied', {name: entry.player.name}))">{{ t('company.deny') }}</SecondaryButton>
          </ButtonRow>
        </li>
      </PlainList>
      <template v-if="(applications.result.value.invites ?? []).length > 0">
        <CapsLabel class="border-t border-line-soft px-3 pb-1 pt-3 md:px-4">{{ t('company.sentInvites') }}</CapsLabel>
        <PlainList data-testid="invites">
          <li v-for="invite in applications.result.value.invites" :key="invite.player.uuid" class="flex items-center justify-between gap-3 border-b border-line-soft px-3 py-2.5 last:border-b-0 md:px-4">
            <span class="flex min-w-0 flex-col gap-0.5">
              <span class="truncate text-sm font-semibold">{{ invite.player.name }}</span>
              <span class="truncate text-xs text-ink-muted">{{ t('company.invitedBy', {name: invite.inviter.name}) }}</span>
            </span>
            <SecondaryButton :disabled="action.busy.value" @click="answer('company.members.revoke', invite.player.uuid, t('company.revoked', {name: invite.player.name}))">{{ t('company.revoke') }}</SecondaryButton>
          </li>
        </PlainList>
      </template>
    </template>
  </PanelBox>
</template>
