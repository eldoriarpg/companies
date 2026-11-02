/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {EventRow} from '~/api/gateway'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import RowButton from '@/components/button/RowButton.vue'
import PlainList from '@/components/container/PlainList.vue'
import ItemMark from '@/components/display/ItemMark.vue'
import RowTitle from '@/components/typography/RowTitle.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import {offerMaterial} from '~/util/events'

/**
 * The contests that closed lately, under the running offers, each with how it ended and the place of the player's
 * company. Opening one shows its ranking and prizes. Shows nothing without results.
 */
defineProps<{
  results: EventRow[]
  /** Whether the player is part of a company, whose place the results show. */
  member?: boolean
  /** The offer open beside the list. */
  selected?: number | null
}>()

defineEmits<{
  open: [id: number]
}>()

const {t} = useI18n()
</script>

<template>
  <template v-if="results.length > 0">
    <div class="border-t border-line px-3 pt-3 md:px-4">
      <SectionHeader :note="member ? t('events.resultsNote') : t('events.resultsNoteAlone')">{{ t('events.results') }}</SectionHeader>
    </div>
    <PlainList data-testid="event-results">
      <li v-for="offer in results" :key="offer.id">
        <RowButton :selected="selected === offer.id" @click="$emit('open', offer.id)">
          <span class="flex min-w-0 items-center gap-2.5">
            <ItemMark :item="{material: offerMaterial(offer.item.icon), name: offer.item.name}"/>
            <span class="flex min-w-0 grow items-center gap-1.5">
              <RowTitle>{{ offer.name }}</RowTitle>
              <SecondaryBadge>{{ t(`events.state.${offer.state}`) }}</SecondaryBadge>
            </span>
            <span v-if="offer.place" class="tabular shrink-0 text-xs text-accent">{{ t('events.place', {place: offer.place}) }}</span>
          </span>
        </RowButton>
      </li>
    </PlainList>
  </template>
</template>
