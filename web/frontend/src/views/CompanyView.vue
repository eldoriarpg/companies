/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import Alert from '@/components/feedback/Alert.vue'
import ViewContent from '@/components/layout/ViewContent.vue'
import {Feature, MemberPermission} from '~/api/generated/protocol'
import {holds} from '~/util/permissions'
import ApplicationsPanel from './companyview/ApplicationsPanel.vue'
import MembersPanel from './companyview/MembersPanel.vue'
import ProfilePanel from './companyview/ProfilePanel.vue'

/**
 * The player's company: its profile with level and rank, its treasury, its members with what they delivered, and for
 * members who may invite, who wants in. Inside the company every member is named. What a member may change is
 * offered where the server switched it on; leaving is confirmed here once the company is gone from the snapshot.
 */
const {t} = useI18n()
const {company, snapshot, supports} = useDashboard()
const left = ref(false)
const recruits = computed(() => supports(Feature['company.applications'])
    && holds(snapshot.value?.player.permissions ?? [], MemberPermission.INVITE))
</script>

<template>
  <ViewContent :title="company?.name ?? t('nav.company')">
    <Alert v-if="left" variant="success">{{ t('company.left') }}</Alert>
    <Alert v-if="!company" variant="notice">{{ t('portfolio.noCompany') }}</Alert>
    <div v-else class="grid items-start gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,2fr)]">
      <ProfilePanel :company="company" :ranking="snapshot?.rankings"/>
      <div class="flex min-w-0 flex-col gap-4">
        <MembersPanel :members="company.members" :company="company.name" @left="left = true"/>
        <ApplicationsPanel v-if="recruits" :members="company.members.length"/>
      </div>
    </div>
  </ViewContent>
</template>
