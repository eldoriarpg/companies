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
import AreasPanel from './companyview/AreasPanel.vue'
import FoundCompany from './companyview/FoundCompany.vue'
import MembersPanel from './companyview/MembersPanel.vue'
import ProfilePanel from './companyview/ProfilePanel.vue'

/**
 * The player's company: its profile with level and rank, its treasury, its members with what they delivered, and for
 * members who may invite, who wants in, and its areas and home. Inside the company every member is named. What a member may change is
 * offered where the server switched it on; leaving and dissolving are confirmed here once the company is gone from the
 * snapshot. A player without a company can found one here where the server offers it.
 */
const {t} = useI18n()
const {company, snapshot, supports} = useDashboard()
const left = ref(false)
const ended = ref('')
const founds = computed(() => supports(Feature['company.create']))
const recruits = computed(() => supports(Feature['company.applications'])
    && holds(snapshot.value?.player.permissions ?? [], MemberPermission.INVITE))
</script>

<template>
  <ViewContent :title="company?.name ?? t('nav.company')">
    <Alert v-if="left" variant="success">{{ t('company.left') }}</Alert>
    <Alert v-if="ended" variant="success">{{ ended }}</Alert>
    <template v-if="!company">
      <Alert variant="notice">{{ t(founds ? 'company.noCompanyFound' : 'portfolio.noCompany') }}</Alert>
      <FoundCompany v-if="founds" @founded="name => ended = t('company.foundedNow', {name})"/>
    </template>
    <div v-else class="grid items-start gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,2fr)]">
      <ProfilePanel :company="company" :ranking="snapshot?.rankings"/>
      <div class="flex min-w-0 flex-col gap-4">
        <MembersPanel :members="company.members" :company="company.name" @left="left = true" @disbanded="ended = t('company.disbanded')"/>
        <ApplicationsPanel v-if="recruits" :members="company.members.length"/>
        <AreasPanel v-if="company.areas" :areas="company.areas" :home="company.home"/>
      </div>
    </div>
  </ViewContent>
</template>
