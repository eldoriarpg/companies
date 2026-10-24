/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
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
