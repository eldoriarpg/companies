/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import type {DismissedBy} from '~/composables/useDismiss'
import type {PanelAlignment} from '~/composables/useFloatingPanel'
import FloatingPanel from './FloatingPanel.vue'

/**
 * A panel that hangs off a trigger and opens and closes itself: a row's menu, a table's column list.
 *
 * <p>The panel is a {@link FloatingPanel}, rendered at the body rather than beside its trigger,
 * because a trigger usually sits in something that scrolls, and a panel positioned inside a table
 * with `overflow-x-auto` is cut off at the edge of the table.
 *
 * <p>Standing at the end of the body is also why focus is moved by hand. Tabbing on from the
 * trigger would otherwise walk the whole rest of the page before arriving at the panel that just
 * opened, so the first control in it is focused on open and the trigger gets its focus back on
 * close. The panel is placed before that focus lands, so it is no longer transparent by then.
 *
 * <p>It closes on a press outside, on escape, and when focus is tabbed out of it. A menu also
 * closes once something in it was chosen; a panel of settings, such as a column list, stays open
 * while the reader ticks through it.
 *
 * <p>The trigger comes in through the `trigger` slot, which is handed `toggle` and the attributes that
 * tie the trigger to its panel. The content is handed `close`, for a dialog that is done once something
 * deeper in it was chosen. Whether it is open can be bound with `v-model:open`, for a screen that opens
 * it from elsewhere or shows the trigger while it is open.
 */
const open = defineModel<boolean>('open', {default: false})

const props = withDefaults(defineProps<{
  label: string
  role?: 'menu' | 'dialog'
  /** Tells two panels on one page apart. The panel carries it, the trigger is expected to carry it with `-trigger`. */
  testId?: string
  panelClass?: string
  /** Which edge of the trigger the panel lines up with. */
  align?: PanelAlignment
}>(), {
  role: 'menu',
  testId: undefined,
  panelClass: 'min-w-44 py-1',
  align: 'end',
})

const FOCUSABLE = 'button:not([disabled]), a[href], input:not([disabled])'

const floating = ref<InstanceType<typeof FloatingPanel> | null>(null)

function triggerElement(): HTMLElement | null {
  return floating.value?.anchor?.querySelector<HTMLElement>('button, a[href]') ?? null
}

function toggle() {
  if (open.value) close()
  else open.value = true
}

function focusFirstControl(panel: HTMLElement) {
  const first = panel.querySelector<HTMLElement>(FOCUSABLE)
  const landing = first ?? panel
  landing.focus()
}

/**
 * Closes the panel.
 *
 * @param restoreFocus whether focus goes back to the trigger, which it should whenever the reader
 *                     left the panel by choosing something or by pressing escape, and should not
 *                     when they have already put their focus somewhere else
 */
function close(restoreFocus = true) {
  if (!open.value) return
  open.value = false
  if (restoreFocus) triggerElement()?.focus()
}

function onDismissed(by: DismissedBy) {
  if (by === 'escape') triggerElement()?.focus()
}

function onPanelClick() {
  if (props.role === 'menu') close()
}
</script>

<template>
  <FloatingPanel
      ref="floating"
      v-model:open="open"
      :label="label"
      :role="role"
      :test-id="testId"
      :panel-class="panelClass"
      :align="align"
      @opened="focusFirstControl"
      @dismissed="onDismissed"
      @focus-left="close(false)"
  >
    <template #trigger="{triggerAttrs}">
      <slot :toggle="toggle" :trigger-attrs="triggerAttrs" name="trigger"/>
    </template>
    <div role="presentation" @click="onPanelClick">
      <slot :close="close"/>
    </div>
  </FloatingPanel>
</template>
