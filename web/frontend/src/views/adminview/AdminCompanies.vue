/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {AdminTreasury} from '~/api/gateway'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PanelBox from '@/components/container/PanelBox.vue'
import ActionNotice from '@/components/feedback/ActionNotice.vue'
import Alert from '@/components/feedback/Alert.vue'
import TextInput from '@/components/input/TextInput.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import AdminCompanyChanges from './AdminCompanyChanges.vue'
import AdminTreasuryPanel from './AdminTreasuryPanel.vue'

/**
 * Any company for an admin, as `/companyadmin treasury`, `rename`, `transferowner` and `delete` offer it: its
 * treasury with the latest movements, money given or taken, a free rename, a new owner from its members, and its
 * dissolution. The company is named once at the top; each tool needs the permission of its command.
 */
const {t} = useI18n()
const opening = useAction()
const name = ref('')
const company = ref('')
const treasury = ref<AdminTreasury | null>(null)
const done = ref('')

/** Picks the typed company and shows its treasury where the admin may see it. */
async function open(): Promise<void> {
  done.value = ''
  company.value = name.value.trim()
  treasury.value = null
  const result = await opening.run('admin.companies.treasury', {company: company.value})
  if (result) treasury.value = result
}

/**
 * Shows what a change did, and follows a rename.
 *
 * @param text what happened
 * @param renamed the company's new name, when it was renamed
 */
function changed(text: string, renamed?: string): void {
  done.value = text
  if (renamed) {
    company.value = renamed
    name.value = renamed
  }
}

/** Closes a dissolved company. */
function deleted(text: string): void {
  done.value = text
  company.value = ''
  treasury.value = null
}
</script>

<template>
  <div class="grid items-start gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.4fr)]">
    <PanelBox padded :aria-label="t('admin.companies.find')">
      <SectionHeader>{{ t('admin.companies.find') }}</SectionHeader>
      <form class="flex flex-wrap items-end gap-2" @submit.prevent="open">
        <TextInput v-model="name" :label="t('admin.orders.companyName')"/>
        <SecondaryButton type="submit" :disabled="name.trim() === '' || opening.busy.value">{{ t('admin.companies.open') }}</SecondaryButton>
      </form>
      <ActionNotice :problem="opening.problem.value"/>
    </PanelBox>
    <div v-if="company || done" class="flex min-w-0 flex-col gap-4">
      <Alert v-if="done" variant="success">{{ done }}</Alert>
      <AdminTreasuryPanel v-if="treasury" v-model="treasury"/>
      <AdminCompanyChanges v-if="company" :company="company" @changed="changed" @deleted="deleted"/>
    </div>
  </div>
</template>
