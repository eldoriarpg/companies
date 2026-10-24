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
 * A destructive action that asks once more: its button, then the question with the confirming button and a way back.
 * The question takes the focus when it opens, and going back returns the focus to the button that opened it.
 */
defineProps<{
  trigger: string
  question: string
  confirm: string
  busy: boolean
}>()

const emit = defineEmits<{
  confirmed: []
}>()

const {t} = useI18n()
const asking = ref(false)
const root = useTemplateRef<HTMLElement>('root')

/**
 * Opens or closes the question and moves the focus to its first button, or back to the trigger.
 *
 * @param open whether the question opens
 */
async function ask(open: boolean): Promise<void> {
  asking.value = open
  await nextTick()
  root.value?.querySelector<HTMLElement>('button')?.focus()
}
</script>

<template>
  <div ref="root" class="flex flex-col gap-2">
    <template v-if="asking">
      <span class="text-sm">{{ question }}</span>
      <ButtonRow>
        <PrimaryButton :disabled="busy" @click="emit('confirmed')">{{ confirm }}</PrimaryButton>
        <SecondaryButton @click="ask(false)">{{ t('common.back') }}</SecondaryButton>
      </ButtonRow>
    </template>
    <ButtonRow v-else>
      <SecondaryButton @click="ask(true)">{{ trigger }}</SecondaryButton>
    </ButtonRow>
  </div>
</template>
