/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import InfoBadge from '@/components/badge/InfoBadge.vue'
import NoticeBadge from '@/components/badge/NoticeBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import PanelBox from '@/components/container/PanelBox.vue'
import PlainList from '@/components/container/PlainList.vue'
import ActionNotice from '@/components/feedback/ActionNotice.vue'
import Alert from '@/components/feedback/Alert.vue'
import ConfirmStep from '@/components/feedback/ConfirmStep.vue'
import MutedText from '@/components/typography/MutedText.vue'
import RowTitle from '@/components/typography/RowTitle.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import {PerkStateView, type PerkView} from '~/api/generated/protocol'
import {money} from '~/util/format'

/**
 * The server's perks as the company sees them: what each gives, its price and level, and whether the company owns it,
 * has it paused, may buy it or needs a higher level. Members who may buy perks buy one here once they confirmed the
 * price, paid from the treasury as in game; the list follows from the snapshot once the plugin stored it.
 */
const props = defineProps<{
  perks: PerkView[]
  mayBuy: boolean
}>()

const {t, locale} = useI18n()
const action = useAction()
const bought = ref('')

/**
 * Buys a perk from the treasury.
 *
 * @param perk the perk
 */
async function buy(perk: PerkView): Promise<void> {
  bought.value = ''
  if (await action.run('company.perks.buy', {perk: perk.id})) bought.value = t('company.perks.bought', {name: perk.name})
}

const owned = computed(() => props.perks.filter(perk => perk.state === PerkStateView.OWNED).length)
</script>

<template>
  <PanelBox :aria-label="t('company.perks.title')">
    <template #head>
      <SectionHeader :note="String(owned)">{{ t('company.perks.title') }}</SectionHeader>
    </template>
    <ActionNotice :problem="action.problem.value"/>
    <Alert v-if="bought" variant="success">{{ bought }}</Alert>
    <PlainList data-testid="perks">
      <li v-for="perk in perks" :key="perk.id" class="flex flex-col gap-1 border-b border-line-soft px-3 py-2.5 last:border-b-0 md:px-4">
        <span class="flex items-center justify-between gap-2">
          <RowTitle>{{ perk.name }}</RowTitle>
          <SuccessBadge v-if="perk.state === PerkStateView.OWNED">{{ t('company.perks.state.OWNED') }}</SuccessBadge>
          <NoticeBadge v-else-if="perk.state === PerkStateView.PAUSED">{{ t('company.perks.state.PAUSED') }}</NoticeBadge>
          <InfoBadge v-else-if="perk.state === PerkStateView.AVAILABLE">{{ t('company.perks.state.AVAILABLE') }}</InfoBadge>
          <SecondaryBadge v-else-if="perk.state === PerkStateView.LOCKED">{{ t('company.perks.state.LOCKED') }}</SecondaryBadge>
        </span>
        <MutedText tag="span">{{ perk.description }}</MutedText>
        <MutedText tag="span">{{ t('company.perks.terms', {price: money(perk.price, locale), level: perk.minLevel}) }}</MutedText>
        <ConfirmStep
          v-if="mayBuy && perk.state === PerkStateView.AVAILABLE"
          :trigger="t('company.perks.buy')"
          :question="t('company.perks.confirm', {name: perk.name, price: money(perk.price, locale)})"
          :confirm="t('company.perks.buyNow')"
          :busy="action.busy.value"
          @confirmed="buy(perk)"/>
      </li>
    </PlainList>
  </PanelBox>
</template>
