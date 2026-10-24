/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {MemberPermission, MemberView} from '~/api/generated/protocol'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ActionNotice from '@/components/feedback/ActionNotice.vue'
import ConfirmStep from '@/components/feedback/ConfirmStep.vue'
import ToggleSwitch from '@/components/input/ToggleSwitch.vue'

/**
 * What a member may do to another: switch the permissions they hold themselves on or off for them, and remove them
 * from the company after asking once more. The plugin checks both again. Only the switches the player flipped are
 * sent, so a change another member made meanwhile is kept. A removed member leaves the list through its patch, so the
 * confirmation is told to the panel, which outlives the row.
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
const flipped = ref(new Map<MemberPermission, boolean>())

/**
 * Whether a switch is on: as the player flipped it, else as the member holds it now.
 *
 * @param permission the permission
 * @returns true when it is on
 */
function on(permission: MemberPermission): boolean {
  return flipped.value.get(permission) ?? props.member.permissions.includes(permission)
}

/**
 * Flips a switch; flipping it back to what the member holds forgets it.
 *
 * @param permission the permission
 * @param value whether it should be held
 */
function set(permission: MemberPermission, value: boolean): void {
  const next = new Map(flipped.value)
  if (value === props.member.permissions.includes(permission)) next.delete(permission)
  else next.set(permission, value)
  flipped.value = next
}

/** Saves the flipped permissions. */
async function save(): Promise<void> {
  const permissions = Object.fromEntries(flipped.value)
  if (await action.run('company.members.permissions', {player: props.member.uuid, permissions})) {
    flipped.value = new Map()
    emit('saved', props.member.name)
  }
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
            :model-value="on(permission)"
            :label="t(`permission.${permission}`)"
            @update:model-value="value => set(permission, value)"
        />
      </div>
      <ButtonRow>
        <PrimaryButton :disabled="action.busy.value || flipped.size === 0" @click="save">{{ t('company.savePermissions') }}</PrimaryButton>
      </ButtonRow>
    </fieldset>
    <ConfirmStep
        v-if="kickable"
        :trigger="t('company.kick')"
        :question="t('company.kickQuestion', {name: member.name})"
        :confirm="t('company.kickConfirm')"
        :busy="action.busy.value"
        @confirmed="kick"
    />
  </div>
</template>
