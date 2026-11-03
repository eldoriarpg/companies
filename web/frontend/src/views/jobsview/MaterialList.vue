/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import MutedText from '@/components/typography/MutedText.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import {count} from '~/util/format'

/**
 * Materials of a job with their amounts, such as what a supplied draft still lacks; a note when there are none.
 */
defineProps<{
  title: string
  items: [string, number][]
  empty: string
  testId: string
}>()

const {locale} = useI18n()
</script>

<template>
  <section class="flex flex-col gap-2" :data-testid="testId">
    <SectionHeader>{{ title }}</SectionHeader>
    <MutedText v-if="items.length === 0" tag="p">{{ empty }}</MutedText>
    <ul v-else class="m-0 list-none border border-line bg-ground px-3 py-0.5">
      <li v-for="[material, amount] in items" :key="material" class="flex justify-between gap-3 border-b border-line-soft py-2 text-sm last:border-b-0">
        <span>{{ material.toLowerCase().replaceAll('_', ' ') }}</span>
        <span class="tabular">{{ count(amount, locale) }}</span>
      </li>
    </ul>
  </section>
</template>
