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
import AdminOrders from './adminview/AdminOrders.vue'

/**
 * The admin tools of the server, for players with admin rights: one tab per area the server offers, each checked by
 * the plugin against the admin's own permission for it, as `/companyadmin` does.
 */
const {t} = useI18n()
const {supports} = useDashboard()
const tabs = computed(() => [
  {key: 'orders', label: t('admin.tabs.orders'), feature: Feature['admin.orders']},
].filter(tab => supports(tab.feature)))
const tab = ref('orders')
</script>

<template>
  <ViewContent :title="t('nav.admin')">
    <Alert v-if="tabs.length === 0" variant="notice">{{ t('admin.noTools') }}</Alert>
    <template v-else>
      <TabBar v-model="tab" :tabs="tabs" :label="t('nav.admin')" panel-id="admin-panel"/>
      <div id="admin-panel" role="tabpanel">
        <AdminOrders v-if="tab === 'orders'"/>
      </div>
    </template>
  </ViewContent>
</template>
