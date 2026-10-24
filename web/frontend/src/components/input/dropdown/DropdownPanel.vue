/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import {PopoverContent, PopoverPortal, PopoverRoot, PopoverTrigger} from 'reka-ui'

/**
 * The panel every dropdown opens: hung off its trigger, and rendered at the end of the page.
 *
 * <p>Rendering it there is what keeps a dropdown in a table whole. A panel placed beside its
 * trigger is cut off by every ancestor that scrolls, and a table with `overflow-x-auto` is one. It
 * is placed under the trigger, or above it where there is no room below, and follows it as the page
 * scrolls.
 *
 * <p>Opening moves the focus into the panel and closing gives it back to the trigger. Escape, a
 * press outside and tabbing away all close it. Nothing listens to the page while it is closed, so
 * a screen full of closed dropdowns costs nothing and renders on the server as it is.
 *
 * <p>The trigger comes in through the `trigger` slot and must be a single element or component,
 * which receives the attributes that tie it to the panel.
 */
const open = defineModel<boolean>('open', {default: false})

withDefaults(defineProps<{
  /**
   * Sizes the panel from its trigger, for a trigger that fills its field. See
   * {@link PANEL_WIDTH_FROM_TRIGGER}.
   */
  matchWidth?: boolean
  /** Size and spacing of the panel, such as its width and its greatest height. */
  panelClass?: string
  testId?: string
}>(), {
  matchWidth: false,
  panelClass: 'w-64 max-h-72',
  testId: undefined,
})

const emit = defineEmits<{
  openAutoFocus: [event: Event]
}>()

/**
 * The width of a panel sized from its trigger: at least the trigger and at least 16rem, growing with
 * its content up to 24rem or the trigger, whichever is wider, and never wider than the window less
 * the collision padding on both sides. A field wider than 24rem keeps a panel exactly its width; a
 * narrow chip in a filter bar no longer squeezes its panel down to a few letters.
 */
const PANEL_WIDTH_FROM_TRIGGER = 'w-max min-w-[min(max(16rem,var(--reka-popover-trigger-width)),100vw_-_1rem)] max-w-[min(max(24rem,var(--reka-popover-trigger-width)),100vw_-_1rem)]'

/** Where the focus lands as the panel opens: its search, or else the entry the list highlights. */
const LANDING = 'input[type="text"], input[type="search"], [role="option"][tabindex="0"], [role="listbox"][tabindex="0"]'

/**
 * Puts the focus where the reader starts: in the search where there is one, otherwise on the list,
 * past whatever buttons sit above it. A caller that prevents the event keeps the focus where it is.
 */
function landInTheList(event: Event) {
  emit('openAutoFocus', event)
  if (event.defaultPrevented) return
  const landing = (event.target as HTMLElement | null)?.querySelector<HTMLElement>(LANDING)
  if (!landing) return
  event.preventDefault()
  landing.focus()
}
</script>

<template>
  <PopoverRoot v-model:open="open">
    <PopoverTrigger as-child>
      <slot name="trigger" :open="open"/>
    </PopoverTrigger>
    <PopoverPortal>
      <PopoverContent
          side="bottom"
          align="start"
          :side-offset="4"
          :collision-padding="8"
          :data-testid="testId"
          :class="[matchWidth ? PANEL_WIDTH_FROM_TRIGGER : '', panelClass]"
          class="z-[90] flex flex-col overflow-hidden border border-line bg-surface text-ink outline-none data-[state=open]:animate-fade-in data-[state=closed]:animate-fade-out"
          @open-auto-focus="landInTheList"
      >
        <slot/>
      </PopoverContent>
    </PopoverPortal>
  </PopoverRoot>
</template>
