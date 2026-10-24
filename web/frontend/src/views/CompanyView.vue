/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import NoticeBanner from '@/components/feedback/NoticeBanner.vue'
import ViewContent from '@/components/layout/ViewContent.vue'
import MembersPanel from './companyview/MembersPanel.vue'
import ProfilePanel from './companyview/ProfilePanel.vue'

/**
 * The player's company: its profile with level and rank, its treasury, and its members with what they delivered.
 * Inside the company every member is named. Managing it from the web comes with the later steps.
 */
const {t} = useI18n()
const {company, snapshot} = useDashboard()
</script>

<template>
  <ViewContent :title="company?.name ?? t('nav.company')">
    <NoticeBanner v-if="!company">{{ t('portfolio.noCompany') }}</NoticeBanner>
    <div v-else class="grid items-start gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,2fr)]">
      <ProfilePanel :company="company" :ranking="snapshot?.rankings"/>
      <MembersPanel :members="company.members"/>
    </div>
  </ViewContent>
</template>
