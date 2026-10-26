/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
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
  {key: 'setup', label: t('admin.tabs.setup'), features: [Feature['admin.setup']]},
  {key: 'orders', label: t('admin.tabs.orders'), features: [Feature['admin.orders']]},
  {key: 'players', label: t('admin.tabs.players'), features: [Feature['admin.players']]},
  {key: 'companies', label: t('admin.tabs.companies'), features: [Feature['admin.companies']]},
  {key: 'levels', label: t('admin.tabs.levels'), features: [Feature['admin.levels']]},
  {key: 'boards', label: t('admin.tabs.boards'), features: [Feature['admin.boards']]},
  {key: 'servers', label: t('admin.tabs.servers'), features: [Feature['admin.nodes'], Feature['admin.config']]},
  {key: 'settings', label: t('admin.tabs.settings'), features: [Feature['admin.settings']]},
].filter(tab => tab.features.some(feature => supports(feature))))
watchEffect(() => {
  if (!tabs.value.some(entry => entry.key === tab.value)) tab.value = tabs.value.find(entry => entry.key !== 'setup')?.key ?? tabs.value[0]?.key ?? ''
})

onMounted(async () => {
  if (!supports(Feature['admin.setup'])) return
  const result = await checking.run('admin.setup.get', {})
  if (result) setup.value = result
})

/** Hides the reminder to finish the setup, for every admin. */
async function dismiss(): Promise<void> {
  const result = await dismissing.run('admin.setup.mark', {page: 'banner', state: 'dismissed'})
  if (result) setup.value = result
}
</script>

<template>
  <ViewContent :title="t('nav.admin')">
    <Alert v-if="tabs.length === 0" variant="notice">{{ t('admin.noTools') }}</Alert>
    <template v-else>
      <Alert v-if="reminder" variant="notice" data-testid="admin-setup-reminder">
        <span class="flex flex-col gap-2">
          {{ t('admin.setup.reminder') }}
          <ButtonRow>
            <PrimaryButton @click="tab = 'setup'">{{ t('admin.setup.continue') }}</PrimaryButton>
            <SecondaryButton :disabled="dismissing.busy.value" @click="dismiss">{{ t('admin.setup.dismiss') }}</SecondaryButton>
          </ButtonRow>
        </span>
      </Alert>
      <TabBar v-model="tab" :tabs="tabs" :label="t('nav.admin')" panel-id="admin-panel"/>
      <div id="admin-panel" role="tabpanel">
        <AdminOrders v-if="tab === 'orders'"/>
        <AdminPlayers v-else-if="tab === 'players'"/>
        <AdminCompanies v-else-if="tab === 'companies'"/>
        <AdminLevels v-else-if="tab === 'levels'"/>
        <AdminBoards v-else-if="tab === 'boards'"/>
        <AdminServers v-else-if="tab === 'servers'"/>
        <AdminSettings v-else-if="tab === 'settings'"/>
        <AdminSetupView v-else-if="tab === 'setup'" @open="tab = $event" @changed="setup = $event"/>
      </div>
    </template>
  </ViewContent>
</template>
