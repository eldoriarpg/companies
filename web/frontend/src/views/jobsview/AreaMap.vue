/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {AreaPreview} from '~/api/gateway'
import PictureButton from '@/components/button/PictureButton.vue'

/** A column of the map, counted from the area's north-west corner. */
export interface MapCorner {
  x: number
  z: number
}

/** Where a schematic lies on the map: its north-west corner and its size in columns. */
export interface Footprint extends MapCorner {
  width: number
  length: number
}

/**
 * The top layer of a stored area as a small map, north up, one square per column in the colour of its top block. With
 * a footprint it shows where a schematic lies; a click or the arrow keys move it, and it never leaves the area.
 */
const props = defineProps<{
  preview: AreaPreview
  footprint?: Footprint | null
}>()

const emit = defineEmits<{
  place: [x: number, z: number]
}>()

const {t} = useI18n()
const canvas = ref<HTMLCanvasElement | null>(null)
const scale = computed(() => Math.max(2, Math.floor(320 / Math.max(props.preview.width, props.preview.length))))

function draw(): void {
  const target = canvas.value
  const context = target?.getContext('2d')
  if (!target || !context) return
  const {width, length, palette, cells} = props.preview
  context.clearRect(0, 0, target.width, target.height)
  for (let z = 0; z < length; z++) {
    for (let x = 0; x < width; x++) {
      const color = palette[cells[z * width + x] ?? 0]
      if (!color) continue
      context.fillStyle = color
      context.fillRect(x * scale.value, z * scale.value, scale.value, scale.value)
    }
  }
}

onMounted(draw)
watch(() => props.preview, () => nextTick(draw))

/**
 * @returns the footprint's corner moved to the given column, kept inside the area
 */
function clamped(x: number, z: number, footprint: Footprint): [number, number] {
  return [
    Math.min(Math.max(0, x), Math.max(0, props.preview.width - footprint.width)),
    Math.min(Math.max(0, z), Math.max(0, props.preview.length - footprint.length)),
  ]
}

/**
 * Moves the footprint to where the map was clicked; a click from the keyboard carries no position and moves nothing.
 */
function click(event: MouseEvent): void {
  const footprint = props.footprint
  const target = canvas.value
  if (!footprint || !target || event.detail === 0) return
  const box = target.getBoundingClientRect()
  const x = Math.floor((event.clientX - box.left) / box.width * props.preview.width - footprint.width / 2)
  const z = Math.floor((event.clientY - box.top) / box.height * props.preview.length - footprint.length / 2)
  emit('place', ...clamped(x, z, footprint))
}

const steps: Record<string, [number, number]> = {ArrowLeft: [-1, 0], ArrowRight: [1, 0], ArrowUp: [0, -1], ArrowDown: [0, 1]}

function key(event: KeyboardEvent): void {
  const footprint = props.footprint
  const step = steps[event.key]
  if (!footprint || !step) return
  event.preventDefault()
  emit('place', ...clamped(footprint.x + step[0], footprint.z + step[1], footprint))
}
</script>

<template>
  <div class="flex flex-col gap-1">
    <PictureButton
        :disabled="!footprint"
        :label="footprint ? t('jobs.areas.mapAt', {width: preview.width, length: preview.length, x: footprint.x, z: footprint.z}) : t('jobs.areas.map', {width: preview.width, length: preview.length})"
        data-testid="area-map"
        @click="click"
        @keydown="key"
    >
      <canvas
          ref="canvas"
          :width="preview.width * scale"
          :height="preview.length * scale"
          class="block max-w-full [image-rendering:pixelated]"
      />
      <div
          v-if="footprint"
          class="pointer-events-none absolute border-2 border-accent bg-accent/20"
          :style="{
            left: `${footprint.x / preview.width * 100}%`,
            top: `${footprint.z / preview.length * 100}%`,
            width: `${footprint.width / preview.width * 100}%`,
            height: `${footprint.length / preview.length * 100}%`,
          }"
          data-testid="area-map-footprint"
      />
    </PictureButton>
    <span class="text-[0.6875rem] text-ink-muted">{{ t(footprint ? 'jobs.areas.mapPlace' : 'jobs.areas.mapNorth') }}</span>
  </div>
</template>
