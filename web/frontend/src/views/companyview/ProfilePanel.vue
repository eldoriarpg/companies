/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import {useNow} from '@vueuse/core'
import {Feature, MemberPermission, type CompanyView, type RankingPage} from '~/api/generated/protocol'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PanelBox from '@/components/container/PanelBox.vue'
import FigureValue from '@/components/display/FigureValue.vue'
import ProgressBar from '@/components/display/ProgressBar.vue'
import Alert from '@/components/feedback/Alert.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import {count, money, when} from '~/util/format'
import {openLabel} from '~/util/labels'
import {holds} from '~/util/permissions'
import ProfileEditor from './ProfileEditor.vue'
import RenameCompany from './RenameCompany.vue'
import TreasuryActions from './TreasuryActions.vue'

/**
 * The company's profile: tag and name, description, level with its progress, rank, the orders it works on, how it
 * takes new members, and the treasury where the player may see it. Where the server offers it, members who may edit
 * the profile change description, tag and colour here, the owner renames the company, and members of the treasury
 * move money and set the share.
 */
const props = defineProps<{
  company: CompanyView
  ranking?: RankingPage
}>()

const {t, te, locale} = useI18n()
const now = useNow({interval: 60_000})
const progress = computed(() => Math.round((props.company.level.progress ?? 0) * 100))
const {supports, snapshot} = useDashboard()
const own = computed(() => snapshot.value?.player.permissions ?? [])
const edits = computed(() => supports(Feature['company.profile']) && holds(own.value, MemberPermission.EDIT_PROFILE))
const renames = computed(() => supports(Feature['company.profile']) && own.value.includes(MemberPermission.OWNER))
const banks = computed(() => supports(Feature['company.treasury']))
const editing = ref<'none' | 'profile' | 'rename'>('none')
const done = ref('')

/**
 * Closes the editor with a message.
 *
 * @param text what happened
 */
function finish(text: string): void {
  done.value = text
  editing.value = 'none'
}
</script>

<template>
  <div class="flex flex-col gap-4">
    <PanelBox padded>
      <div class="flex items-center gap-2">
        <span v-if="company.tag" class="tabular bg-accent px-1.5 py-0.5 text-xs font-semibold text-accent-contrast">{{ company.tag }}</span>
        <SectionHeader>{{ company.name }}</SectionHeader>
      </div>
      <MutedText v-if="company.description" tag="p">{{ company.description }}</MutedText>
      <div class="grid grid-cols-3 gap-2">
        <FigureValue :label="t('company.level')">{{ company.level.level }}</FigureValue>
        <FigureValue :label="t('company.rank')">{{ ranking?.own ? `#${ranking.own.place}` : '-' }}</FigureValue>
        <FigureValue :label="t('company.orders')">{{ count(company.orders.length, locale) }}</FigureValue>
      </div>
      <ProgressBar :value="progress" tone="accent" :label="t('company.levelProgress', {level: company.level.name})"/>
      <MutedText tag="p">{{ t('company.levelNote', {level: company.level.name, progress}) }}</MutedText>
      <MutedText tag="p">{{ openLabel({t, te}, `company.joinMode.${company.joinMode}`, company.joinMode) }} · {{ t('company.founded', {time: when(company.founded, now, locale)}) }}</MutedText>
      <Alert v-if="done" variant="success">{{ done }}</Alert>
      <ProfileEditor v-if="editing === 'profile'" :company="company" @saved="finish(t('company.profileSaved'))" @close="editing = 'none'"/>
      <RenameCompany v-else-if="editing === 'rename'" @renamed="name => finish(t('company.renamed', {name}))" @close="editing = 'none'"/>
      <ButtonRow v-else-if="edits || renames">
        <SecondaryButton v-if="edits" @click="editing = 'profile'; done = ''">{{ t('company.editProfile') }}</SecondaryButton>
        <SecondaryButton v-if="renames" @click="editing = 'rename'; done = ''">{{ t('company.rename') }}</SecondaryButton>
      </ButtonRow>
    </PanelBox>
    <PanelBox v-if="company.treasury" padded>
      <FigureValue :label="t('company.treasury')" size="large">
        <span data-testid="company-treasury">{{ money(company.treasury.balance, locale) }}</span>
      </FigureValue>
      <MutedText v-if="company.treasury.share !== undefined" tag="p">{{ t('company.shareNote', {share: company.treasury.share}) }}</MutedText>
      <TreasuryActions v-if="banks" :treasury="company.treasury"/>
    </PanelBox>
  </div>
</template>
