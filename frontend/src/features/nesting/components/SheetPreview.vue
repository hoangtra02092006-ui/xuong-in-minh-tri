<script setup lang="ts">
// Layout preview (spec 5.6 d): true-scale SVG, rulers every 10 cm, colour per design,
// real rotation angle for tilted pieces, tooltip on hover/tap.
import { computed, ref, watch } from 'vue'
import type { Placement, Sheet } from '@/api/types'
import { categoryColor } from '@/shared/category-colors'
import { pieceShape, rotationText, ticks } from '@/shared/layout-geometry'
import { formatCm, formatSizeCm } from '@/shared/units'

const props = defineProps<{ sheets: Sheet[]; jobId: string }>()
const active = ref(0)
// New result: back to sheet 1.
watch(
  () => props.jobId,
  () => (active.value = 0),
)

const sheet = computed(() => props.sheets[Math.min(active.value, props.sheets.length - 1)])
const W = computed(() => sheet.value.widthMm)
const L = computed(() => sheet.value.lengthMm)
/** Ruler sizes scale with the sheet so text stays readable for 30-160 cm rolls. */
const font = computed(() => Math.max(8, W.value / 48))
const pad = computed(() => font.value * 3.2)

const pieces = computed(() =>
  sheet.value.placements.map((p, i) => ({
    key: i,
    p,
    color: categoryColor(p.categoryIndex),
    shape: pieceShape(p, L.value, `${formatCm(p.sourceWMm)}x${formatCm(p.sourceHMm)}`),
  })),
)
const xTicks = computed(() => ticks(W.value))
const yTicks = computed(() => ticks(L.value, true))

const tip = ref<{ p: Placement; x: number; y: number } | null>(null)
const frame = ref<HTMLElement | null>(null)

function showTip(e: PointerEvent, p: Placement) {
  const box = frame.value?.getBoundingClientRect()
  if (!box || !frame.value) return
  tip.value = {
    p,
    x: e.clientX - box.left + frame.value.scrollLeft,
    y: e.clientY - box.top + frame.value.scrollTop,
  }
}
</script>

<template>
  <div class="preview">
    <div v-if="sheets.length > 1" class="tabs" role="tablist" aria-label="Chọn tấm">
      <button
        v-for="(s, i) in sheets"
        :key="s.index"
        type="button"
        role="tab"
        class="tab"
        :class="{ 'is-on': i === active }"
        :aria-selected="i === active"
        @click="active = i"
      >
        Tấm {{ i + 1 }}
      </button>
    </div>
    <p class="meta num">
      Khổ {{ formatCm(W) }} cm · dài {{ formatCm(L) }} cm · {{ sheet.placements.length }} hình
    </p>
    <div ref="frame" class="frame" @pointerleave="tip = null">
      <svg
        class="svg"
        :viewBox="`${-pad} ${-pad} ${W + pad + font} ${L + pad + font}`"
        role="img"
        :aria-label="`Bố trí tấm ${active + 1}: ${formatCm(W)} x ${formatCm(L)} cm, ${sheet.placements.length} hình`"
      >
        <rect x="0" y="0" :width="W" :height="L" class="sheet-bg" />
        <!-- rulers -->
        <g class="ruler" :font-size="font">
          <line x1="0" :y1="-font * 0.6" :x2="W" :y2="-font * 0.6" />
          <g v-for="t in xTicks" :key="`x${t.pos}`">
            <line :x1="t.pos" :x2="t.pos" :y1="-font * 1.1" :y2="-font * 0.6" />
            <text :x="t.pos" :y="-font * 1.5" text-anchor="middle">{{ t.label }}</text>
          </g>
          <line :x1="-font * 0.6" y1="0" :x2="-font * 0.6" :y2="L" />
          <g v-for="t in yTicks" :key="`y${t.pos}`">
            <line :x1="-font * 1.1" :x2="-font * 0.6" :y1="t.pos" :y2="t.pos" />
            <text :x="-font * 1.4" :y="t.pos" text-anchor="end" dominant-baseline="middle">
              {{ t.label }}
            </text>
          </g>
        </g>
        <!-- pieces -->
        <g
          v-for="it in pieces"
          :key="it.key"
          class="piece"
          @pointerenter="showTip($event, it.p)"
          @pointermove="showTip($event, it.p)"
        >
          <rect
            :x="it.shape.x"
            :y="it.shape.y"
            :width="it.shape.w"
            :height="it.shape.h"
            :transform="it.shape.transform || undefined"
            :rx="Math.min(2, it.shape.w / 8)"
            :fill="it.color"
            fill-opacity="0.18"
            :stroke="it.color"
            stroke-width="0.8"
          />
          <text
            v-if="it.shape.label"
            :x="it.shape.cx"
            :y="it.shape.cy"
            :font-size="it.shape.fontSize"
            :fill="it.color"
            text-anchor="middle"
            dominant-baseline="central"
            :transform="it.shape.transform || undefined"
            class="piece__label"
          >
            {{ it.shape.label }}
          </text>
        </g>
        <!-- sheet border last so it stays on top -->
        <rect x="0" y="0" :width="W" :height="L" class="sheet-border" />
      </svg>
      <div v-if="tip" class="tip" :style="{ left: `${tip.x + 14}px`, top: `${tip.y + 14}px` }" role="tooltip">
        <strong>{{ tip.p.label }}</strong>
        <span>{{ formatSizeCm(tip.p.sourceWMm, tip.p.sourceHMm) }}</span>
        <span>vị trí {{ formatCm(tip.p.xMm) }} ; {{ formatCm(tip.p.yMm) }} cm</span>
        <span>{{ rotationText(tip.p) }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.tabs {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-1);
  margin-bottom: var(--space-3);
}

.tab {
  height: 30px;
  padding: 0 var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-surface);
  color: var(--color-muted);
  font-size: var(--text-sm);
  font-weight: 500;
  cursor: pointer;
  font-variant-numeric: tabular-nums;
}

.tab:hover {
  border-color: var(--color-border-strong);
  color: var(--color-text);
}

.tab.is-on {
  background: var(--color-text);
  border-color: var(--color-text);
  color: #fff;
}

.meta {
  text-align: left;
  color: var(--color-muted);
  font-size: var(--text-sm);
  margin-bottom: var(--space-2);
}

.frame {
  position: relative;
  max-height: 70vh;
  overflow: auto;
  padding: var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  background: #efeee9;
}

.svg {
  display: block;
  width: 100%;
  max-width: 600px;
  height: auto;
  margin: 0 auto;
}

.sheet-bg {
  fill: #fff;
}

.sheet-border {
  fill: none;
  stroke: #8a8a82;
  stroke-width: 0.8;
}

.ruler {
  fill: var(--color-muted);
  font-family: var(--font-sans);
  font-variant-numeric: tabular-nums;
}

.ruler line {
  stroke: #a7a69e;
  stroke-width: 0.6;
}

.piece {
  cursor: default;
}

.piece:hover rect {
  fill-opacity: 0.32;
}

.piece__label {
  font-family: var(--font-sans);
  font-weight: 600;
  pointer-events: none;
  font-variant-numeric: tabular-nums;
}

.tip {
  position: absolute;
  z-index: 2;
  display: flex;
  flex-direction: column;
  gap: 1px;
  max-width: 260px;
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-sm);
  background: var(--color-text);
  color: #fff;
  font-size: var(--text-sm);
  pointer-events: none;
  box-shadow: var(--shadow-overlay);
  font-variant-numeric: tabular-nums;
}

.tip strong {
  font-weight: 600;
  overflow-wrap: anywhere;
}

.tip span {
  color: #d6d5cf;
}
</style>
