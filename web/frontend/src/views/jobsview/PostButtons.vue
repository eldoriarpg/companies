/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'

/**
 * The buttons under a job form: post it, save it as a draft to post later, or go back.
 */
defineProps<{
  disabled: boolean
  testId: string
}>()

const emit = defineEmits<{
  post: [draft: boolean]
  back: []
}>()

const {t} = useI18n()
</script>

<template>
  <ButtonRow>
    <PrimaryButton :disabled="disabled" :data-testid="`${testId}-post`" @click="emit('post', false)">{{ t('jobs.contract.post') }}</PrimaryButton>
    <SecondaryButton :disabled="disabled" :data-testid="`${testId}-draft`" @click="emit('post', true)">{{ t('jobs.saveDraft') }}</SecondaryButton>
    <SecondaryButton @click="emit('back')">{{ t('jobs.contract.cancel') }}</SecondaryButton>
  </ButtonRow>
</template>
