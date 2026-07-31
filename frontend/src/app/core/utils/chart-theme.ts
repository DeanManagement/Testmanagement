import type { Chart as ChartType } from 'chart.js';

/**
 * Chart.js colors derived from the active theme's CSS custom properties, so charts stay readable
 * when the theme flips (PRD-013). Read at render time rather than cached — the values change with
 * the `tm-dark` class on the document root.
 */
export interface ChartTheme {
    text: string;
    textSecondary: string;
    grid: string;
    surface: string;
}

function cssVar(name: string, fallback: string): string {
    const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
    return value || fallback;
}

export function chartTheme(): ChartTheme {
    return {
        text: cssVar('--tm-text', '#1e2328'),
        textSecondary: cssVar('--tm-text-secondary', '#5f6b7a'),
        grid: cssVar('--tm-chart-grid', 'rgba(0, 0, 0, 0.1)'),
        surface: cssVar('--tm-surface', '#ffffff'),
    };
}

/**
 * Points Chart.js's global defaults at the current theme. Call immediately before constructing a
 * chart. Going through the defaults rather than per-chart options means tick labels, legend text
 * and grid lines all follow the theme without every chart config having to opt in — the dataset
 * colors, which carry status meaning, are left alone.
 */
export function applyChartDefaults(chart: typeof ChartType): void {
    const theme = chartTheme();
    chart.defaults.color = theme.text;
    chart.defaults.borderColor = theme.grid;
    chart.defaults.plugins.tooltip.backgroundColor = theme.surface;
    chart.defaults.plugins.tooltip.titleColor = theme.text;
    chart.defaults.plugins.tooltip.bodyColor = theme.textSecondary;
    chart.defaults.plugins.tooltip.borderColor = theme.grid;
    chart.defaults.plugins.tooltip.borderWidth = 1;
}
