/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import {ref, useId} from 'vue'
import {FocusScope} from 'reka-ui'
import {useFloatingPanel, type PanelAlignment} from '~/composables/useFloatingPanel'
import {useDismiss, type DismissedBy} from '~/composables/useDismiss'

/**
 * A panel that hangs off a trigger and is rendered at the end of the page, whoever decides when it
 * is open.
 *
 * <p>Rendering it at the body is what keeps it whole: a panel positioned beside its trigger is cut
 * off by every ancestor with `overflow`, a scrolling list or the body of a dialog alike. It is placed
 * against the element this component renders around the trigger.
 *
 * <p>A press outside the trigger and the panel, and the escape key, close it and are reported as
 * `dismissed`; a press inside leaves it alone. Focus leaving both is reported as `focusLeft`.
 *
 * <p>It works inside a dialog or a bottom sheet as well. The panel paints above every dialog layer and takes the
 * pointer although a dialog has switched it off for the rest of the page. While it is open it
 * holds a focus scope of its own, which pauses the dialog's trap, so its fields can take the focus.
 * `opened` is emitted once that scope stands and the panel is placed, which is the moment a caller
 * can move the focus into it; the panel moves none itself, so an editor keeps its selection.
 */
const open = defineModel<boolean>('open', {required: true})

const props = withDefaults(defineProps<{
  label: string
  role: 'menu' | 'dialog'
  /** Tells two panels on one page apart. The panel carries it, the trigger is expected to carry it with `-trigger`. */
  testId?: string
  panelClass?: string
  align?: PanelAlignment
}>(), {
  testId: undefined,
  panelClass: 'min-w-44 py-1',
  align: 'end',
})

const emit = defineEmits<{
  opened: [panel: HTMLElement]
  dismissed: [by: DismissedBy]
  focusLeft: []
}>()

const anchor = ref<HTMLElement | null>(null)
const {panel, style, place} = useFloatingPanel(anchor, open, props.align)
const panelId = useId()

useDismiss(open, () => [anchor.value, panel.value], (by) => {
  open.value = false
  emit('dismissed', by)
})

function announceOpened(event: Event) {
  event.preventDefault()
  place()
  if (panel.value) emit('opened', panel.value)
}

function onFocusOut(event: FocusEvent) {
  const next = event.relatedTarget as Node | null
  if (!next || anchor.value?.contains(next) || panel.value?.contains(next)) return
  emit('focusLeft')
}

defineExpose({anchor})
</script>

<template>
  <div ref="anchor">
    <slot
        name="trigger"
        :trigger-attrs="{'aria-controls': panelId, 'aria-expanded': open, 'aria-haspopup': role}"
    />
    <Teleport to="body">
      <FocusScope v-if="open" as-child @mount-auto-focus="announceOpened" @unmount-auto-focus.prevent>
        <div
            :id="panelId"
            ref="panel"
            :aria-label="label"
            :class="panelClass"
            :data-testid="testId"
            :role="role"
            :style="style"
            class="pointer-events-auto z-[90] max-h-[60vh] overflow-y-auto border border-line bg-surface text-left"
            tabindex="-1"
            @focusout="onFocusOut"
        >
          <slot/>
        </div>
      </FocusScope>
    </Teleport>
  </div>
</template>
