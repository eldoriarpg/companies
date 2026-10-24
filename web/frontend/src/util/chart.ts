/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */

/**
 * The points of a line chart for an SVG polyline: the values spread over the width, the lowest at the bottom and the
 * highest at the top, with a margin of two units so the stroke is not cut off. A single value is drawn flat.
 *
 * @param values the values in order
 * @param width the chart's width
 * @param height the chart's height
 * @returns the points as `x,y x,y …`
 */
export function linePoints(values: readonly number[], width: number, height: number): string {
    if (values.length === 0) return ''
    const series = values.length === 1 ? [values[0] as number, values[0] as number] : values
    const low = Math.min(...series)
    const span = Math.max(...series) - low || 1
    return series
        .map((value, index) => {
            const x = index * width / (series.length - 1)
            const y = height - 2 - (value - low) / span * (height - 4)
            return `${x.toFixed(1)},${y.toFixed(1)}`
        })
        .join(' ')
}

/**
 * The lines of a chart with gaps, one per run of values: every slot keeps its place across the width, so missing
 * values leave a gap instead of pulling their neighbours together. The scale comes from the values present. A value
 * standing alone between gaps is drawn as a short flat mark, so it stays visible.
 *
 * @param values the values in order, null where there is none
 * @param width the chart's width
 * @param height the chart's height
 * @returns one `x,y x,y …` list per run
 */
export function lineSegments(values: readonly (number | null)[], width: number, height: number): string[] {
    const present = values.filter((value): value is number => value !== null)
    if (present.length === 0) return []
    if (present.length === values.length) return [linePoints(present, width, height)]
    const low = Math.min(...present)
    const span = Math.max(...present) - low || 1
    const step = values.length > 1 ? width / (values.length - 1) : 0
    const point = (value: number, x: number) => `${x.toFixed(1)},${(height - 2 - (value - low) / span * (height - 4)).toFixed(1)}`
    const segments: string[] = []
    let run: string[] = []
    const close = (index: number, value: number | null) => {
        if (run.length === 1 && value !== null) {
            const x = index * step
            run = [point(value, Math.max(0, x - 1.5)), point(value, Math.min(width, x + 1.5))]
        }
        if (run.length > 0) segments.push(run.join(' '))
        run = []
    }
    values.forEach((value, index) => {
        if (value === null) {
            close(index - 1, values[index - 1] ?? null)
            return
        }
        run.push(point(value, index * step))
    })
    close(values.length - 1, values.at(-1) ?? null)
    return segments
}
