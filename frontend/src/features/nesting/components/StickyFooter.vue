<template>
  <div class="sticky-foot">
    <div class="sticky-foot__inner">
      <div class="sticky-foot__info"><slot /></div>
      <div class="sticky-foot__actions"><slot name="actions" /></div>
    </div>
  </div>
</template>

<style scoped>
.sticky-foot {
  position: sticky;
  bottom: 0;
  z-index: 30;
  margin-top: var(--space-5);
  background: rgb(255 255 255 / 95%);
  backdrop-filter: blur(6px);
  border-top: 1px solid var(--color-border);
  box-shadow: var(--shadow-sticky);
  padding-bottom: env(safe-area-inset-bottom);
}

.sticky-foot__inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  max-width: var(--page-max);
  margin: 0 auto;
  padding: var(--space-3) var(--space-5);
}

.sticky-foot__info {
  min-width: 0;
  color: var(--color-muted);
  font-variant-numeric: tabular-nums;
}

.sticky-foot__actions {
  display: flex;
  gap: var(--space-2);
  flex: none;
}

@media (max-width: 640px) {
  .sticky-foot__inner {
    flex-direction: column;
    align-items: stretch;
    gap: var(--space-2);
    padding: var(--space-3) var(--space-4);
  }

  .sticky-foot__info {
    font-size: var(--text-sm);
  }

  .sticky-foot__actions > :deep(*) {
    flex: 1;
  }
}
</style>
