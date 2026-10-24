/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import ButtonRow from '@/components/button/ButtonRow.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ActionNotice from '@/components/feedback/ActionNotice.vue'
import Alert from '@/components/feedback/Alert.vue'
import Modal from '@/components/feedback/Modal.vue'
import TextInput from '@/components/input/TextInput.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'

/**
 * Dissolves the company, for its owner. A dialog says what happens and is answered only once the owner typed the
 * company's name; the plugin checks the name and the ownership again. The company leaves the dashboard through its
 * patch, so dissolving is told to the page.
 */
const props = defineProps<{
  company: string
}>()

const emit = defineEmits<{
  disbanded: []
}>()

const {t} = useI18n()
const action = useAction()
const open = ref(false)
const typed = ref('')
const matches = computed(() => typed.value.trim().toLowerCase() === props.company.toLowerCase())

watch(open, isOpen => {
  if (isOpen) typed.value = ''
})

/** Dissolves the company. */
async function disband(): Promise<void> {
  if (!matches.value) return
  if (!await action.run('company.disband', {name: typed.value.trim()})) return
  open.value = false
  emit('disbanded')
}
</script>

<template>
  <div class="flex flex-col gap-2 border-t border-line-soft px-3 py-2.5 md:px-4">
    <ButtonRow>
      <SecondaryButton @click="open = true">{{ t('company.disband') }}</SecondaryButton>
    </ButtonRow>
    <Modal v-model="open" size="sm">
      <form class="flex flex-col gap-4" @submit.prevent="disband">
        <SectionHeader>{{ t('company.disbandTitle', {company}) }}</SectionHeader>
        <Alert variant="problem">{{ t('company.disbandLead') }}</Alert>
        <TextInput v-model="typed" :label="t('company.disbandType', {company})" focus-first/>
        <ActionNotice :problem="action.problem.value"/>
        <ButtonRow>
          <SecondaryButton data-cancel @click="open = false">{{ t('common.cancel') }}</SecondaryButton>
          <SecondaryButton type="submit" :disabled="!matches || action.busy.value" data-confirm>{{ t('company.disbandConfirm') }}</SecondaryButton>
        </ButtonRow>
      </form>
    </Modal>
  </div>
</template>
