/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {AdminPerk} from '~/api/gateway'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PanelBox from '@/components/container/PanelBox.vue'
import PlainList from '@/components/container/PlainList.vue'
import ActionNotice from '@/components/feedback/ActionNotice.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import MutedText from '@/components/typography/MutedText.vue'
import RowTitle from '@/components/typography/RowTitle.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'

/**
 * The perks of a company for an admin, as `/companyadmin perks` offers them: every registered perk with whether the
 * company owns it, granted or taken without payment, and the perks it owns that are no longer registered. Shown once
 * the list could be read, so only to admins whose permissions allow it; the list is read again after each change.
 */
const props = defineProps<{
  company: string
}>()

const emit = defineEmits<{
  changed: [text: string]
}>()

const {t} = useI18n()
const loading = useAction()
const action = useAction()
const perks = ref<AdminPerk[] | null>(null)

/** Reads the company's perks; a list read before stays when reading again fails. */
async function load(): Promise<void> {
  const result = await loading.run('admin.companies.perks', {company: props.company})
  if (result) perks.value = result.perks
}

/**
 * Grants or takes a perk.
 *
 * @param perk the perk
 */
async function toggle(perk: AdminPerk): Promise<void> {
  const name = perk.owned ? 'admin.companies.perks.take' : 'admin.companies.perks.grant'
  const result = await action.run(name, {company: props.company, perk: perk.id})
  if (!result) return
  emit('changed', t(result.owned ? 'admin.companies.perkGranted' : 'admin.companies.perkTaken', {perk: perk.name, company: props.company}))
  await load()
}

onMounted(load)
</script>

<template>
  <PanelBox v-if="perks" padded :aria-label="t('admin.companies.perks', {company})" data-testid="admin-perks">
    <SectionHeader>{{ t('admin.companies.perks', {company}) }}</SectionHeader>
    <ActionNotice :problem="action.problem.value"/>
    <EmptyState v-if="perks.length === 0">{{ t('admin.companies.noPerks') }}</EmptyState>
    <PlainList v-else>
      <li v-for="perk in perks" :key="perk.id" class="flex items-center justify-between gap-2 border-b border-line-soft py-2 last:border-b-0">
        <span class="flex min-w-0 flex-col">
          <RowTitle>{{ perk.name }}</RowTitle>
          <MutedText tag="span">{{ t(perk.owned ? 'admin.companies.perkOwned' : 'admin.companies.perkNotOwned') }}<template v-if="!perk.offered"> · {{ t('admin.companies.perkOff') }}</template></MutedText>
        </span>
        <SecondaryButton :disabled="action.busy.value" @click="toggle(perk)">{{ t(perk.owned ? 'admin.companies.perkTake' : 'admin.companies.perkGrant') }}</SecondaryButton>
      </li>
    </PlainList>
  </PanelBox>
</template>
