/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {MemberPermission, MemberView} from '~/api/generated/protocol'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ActionNotice from '@/components/feedback/ActionNotice.vue'
import ToggleSwitch from '@/components/input/ToggleSwitch.vue'

/**
 * What a member may do to another: switch the permissions they hold themselves on or off for them, and remove them
 * from the company after asking once more. The plugin checks both again. A removed member leaves the list through its
 * patch, so the confirmation is told to the panel, which outlives the row; the question takes the focus.
 */
const props = defineProps<{
  member: MemberView
  editable: readonly MemberPermission[]
  kickable: boolean
}>()

const emit = defineEmits<{
  saved: [name: string]
  kicked: [name: string]
}>()

const {t} = useI18n()
const action = useAction()
const wanted = ref(new Map(props.editable.map(permission => [permission, props.member.permissions.includes(permission)])))
const changes = computed(() => props.editable.filter(permission => wanted.value.get(permission) !== props.member.permissions.includes(permission)))
const asking = ref(false)
const question = useTemplateRef<HTMLElement>('question')

/**
 * Switches a permission in the form.
 *
 * @param permission the permission
 * @param on whether it should be held
 */
function set(permission: MemberPermission, on: boolean): void {
  wanted.value = new Map(wanted.value).set(permission, on)
}

/** Saves the switched permissions. */
async function save(): Promise<void> {
  const permissions = Object.fromEntries(changes.value.map(permission => [permission, wanted.value.get(permission) === true]))
  if (await action.run('company.members.permissions', {player: props.member.uuid, permissions})) emit('saved', props.member.name)
}

/** Asks once more and moves the focus to the question. */
async function askKick(): Promise<void> {
  asking.value = true
  await nextTick()
  question.value?.querySelector<HTMLElement>('button')?.focus()
}

/** Removes the member. */
async function kick(): Promise<void> {
  if (await action.run('company.members.kick', {player: props.member.uuid})) emit('kicked', props.member.name)
}
</script>

<template>
  <div class="flex basis-full flex-col gap-3 border-t border-line-soft pt-3 md:col-span-5">
    <ActionNotice :problem="action.problem.value"/>
    <fieldset v-if="editable.length > 0" class="m-0 flex flex-col gap-2 border-0 p-0">
      <legend class="mb-1 text-xs font-semibold">{{ t('company.permissionsOf', {name: member.name}) }}</legend>
      <div class="grid gap-2 sm:grid-cols-2">
        <ToggleSwitch
            v-for="permission in editable"
            :key="permission"
            :model-value="wanted.get(permission) === true"
            :label="t(`permission.${permission}`)"
            @update:model-value="on => set(permission, on)"
        />
      </div>
      <ButtonRow>
        <PrimaryButton :disabled="action.busy.value || changes.length === 0" @click="save">{{ t('company.savePermissions') }}</PrimaryButton>
      </ButtonRow>
    </fieldset>
    <template v-if="kickable">
      <div v-if="asking" ref="question" class="flex flex-col gap-2">
        <span class="text-sm">{{ t('company.kickQuestion', {name: member.name}) }}</span>
        <ButtonRow>
          <PrimaryButton :disabled="action.busy.value" @click="kick">{{ t('company.kickConfirm') }}</PrimaryButton>
          <SecondaryButton @click="asking = false">{{ t('common.back') }}</SecondaryButton>
        </ButtonRow>
      </div>
      <ButtonRow v-else>
        <SecondaryButton @click="askKick">{{ t('company.kick') }}</SecondaryButton>
      </ButtonRow>
    </template>
  </div>
</template>
