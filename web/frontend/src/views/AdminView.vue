/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import ActionNotice from '@/components/feedback/ActionNotice.vue'
import Alert from '@/components/feedback/Alert.vue'
import ViewContent from '@/components/layout/ViewContent.vue'
import TabBar from '@/components/navigation/TabBar.vue'
import {Feature} from '~/api/generated/protocol'
import AdminBoards from './adminview/AdminBoards.vue'
import AdminCompanies from './adminview/AdminCompanies.vue'
import AdminLevels from './adminview/AdminLevels.vue'
import AdminOrders from './adminview/AdminOrders.vue'
import AdminPlayers from './adminview/AdminPlayers.vue'
import AdminServers from './adminview/AdminServers.vue'
import type {AdminSetup} from '~/api/gateway'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import AdminSettings from './adminview/AdminSettings.vue'
import AdminSystemOrders from './adminview/AdminSystemOrders.vue'
import AdminOffers from './adminview/AdminOffers.vue'
import AdminSetupView from './adminview/AdminSetup.vue'

/**
 * The admin tools of the server, for players with admin rights: one tab per area the server offers, each checked by
 * the plugin against the admin's own permission for it, as `/companyadmin` does.
 */
const {t} = useI18n()
const {supports} = useDashboard()
const checking = useAction()
const dismissing = useAction()
const setup = ref<AdminSetup | null>(null)
const tab = ref('')
const reminder = computed(() => setup.value !== null && !setup.value.dismissed && setup.value.pages.some(page => page.state === 'open') && tab.value !== 'setup')
const tabs = computed(() => [
  {key: 'orders', label: t('admin.tabs.orders'), features: [Feature['admin.orders']]},
  {key: 'players', label: t('admin.tabs.players'), features: [Feature['admin.players']]},
  {key: 'companies', label: t('admin.tabs.companies'), features: [Feature['admin.companies']]},
  {key: 'levels', label: t('admin.tabs.levels'), features: [Feature['admin.levels']]},
  {key: 'boards', label: t('admin.tabs.boards'), features: [Feature['admin.boards']]},
  {key: 'servers', label: t('admin.tabs.servers'), features: [Feature['admin.nodes'], Feature['admin.config']]},
  {key: 'settings', label: t('admin.tabs.settings'), features: [Feature['admin.settings']]},
  {key: 'systemorders', label: t('admin.tabs.systemorders'), features: [Feature['admin.systemorders']]},
  {key: 'offers', label: t('admin.tabs.offers'), features: [Feature['admin.offers']]},
].filter(tab => tab.features.some(feature => supports(feature))))
const canSetup = computed(() => supports(Feature['admin.setup']) && supports(Feature['admin.settings']))
const allTabs = computed(() => (canSetup.value ? [{key: 'setup', label: t('admin.tabs.setup'), features: [Feature['admin.setup']]}, ...tabs.value] : tabs.value))
watchEffect(() => {
  if (!allTabs.value.some(entry => entry.key === tab.value)) tab.value = tabs.value[0]?.key ?? allTabs.value[0]?.key ?? ''
})

watch(canSetup, async can => {
  if (!can || setup.value) return
  const result = await checking.run('admin.setup.get', {})
  if (result) setup.value = result
}, {immediate: true})

/** Hides the reminder to finish the setup, for every admin. */
async function dismiss(): Promise<void> {
  const result = await dismissing.run('admin.setup.mark', {page: 'banner', state: 'dismissed'})
  if (result) setup.value = result
}
</script>

<template>
  <ViewContent :title="t('nav.admin')">
    <Alert v-if="allTabs.length === 0" variant="notice">{{ t('admin.noTools') }}</Alert>
    <template v-else>
      <Alert v-if="reminder" variant="notice" data-testid="admin-setup-reminder">
        <span class="flex flex-col gap-2">
          {{ t('admin.setup.reminder') }}
          <ButtonRow>
            <PrimaryButton @click="tab = 'setup'">{{ t('admin.setup.continue') }}</PrimaryButton>
            <SecondaryButton v-if="setup?.editable" :disabled="dismissing.busy.value" @click="dismiss">{{ t('admin.setup.dismiss') }}</SecondaryButton>
          </ButtonRow>
          <ActionNotice :problem="dismissing.problem.value"/>
        </span>
      </Alert>
      <TabBar v-model="tab" :tabs="allTabs" :label="t('nav.admin')" panel-id="admin-panel"/>
      <div id="admin-panel" role="tabpanel">
        <AdminOrders v-if="tab === 'orders'"/>
        <AdminPlayers v-else-if="tab === 'players'"/>
        <AdminCompanies v-else-if="tab === 'companies'"/>
        <AdminLevels v-else-if="tab === 'levels'"/>
        <AdminBoards v-else-if="tab === 'boards'"/>
        <AdminServers v-else-if="tab === 'servers'"/>
        <AdminSettings v-else-if="tab === 'settings'"/>
        <AdminSystemOrders v-else-if="tab === 'systemorders'"/>
        <AdminOffers v-else-if="tab === 'offers'"/>
        <AdminSetupView v-else-if="tab === 'setup'" @open="tab = $event" @changed="setup = $event"/>
      </div>
    </template>
  </ViewContent>
</template>
