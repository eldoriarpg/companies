/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import ButtonRow from '@/components/button/ButtonRow.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'

/** Pages through a paged list: the page shown of how many, with the page before and after. Hidden for one page. */
const page = defineModel<number>('page', {required: true})

defineProps<{
  pages: number
}>()

const {t} = useI18n()
</script>

<template>
  <nav v-if="pages > 1" :aria-label="t('paging.label')" class="flex flex-wrap items-center justify-between gap-3">
    <span class="tabular text-xs text-ink-muted" aria-live="polite">{{ t('paging.position', {page, pages}) }}</span>
    <ButtonRow pair>
      <SecondaryButton :disabled="page <= 1" @click="page--">{{ t('paging.previous') }}</SecondaryButton>
      <SecondaryButton :disabled="page >= pages" @click="page++">{{ t('paging.next') }}</SecondaryButton>
    </ButtonRow>
  </nav>
</template>
