// 8 fixed colours by categoryIndex, cycling (spec 5.1). Same design = same colour everywhere.
export const CATEGORY_COLORS = [
  '#7c3aed',
  '#ea580c',
  '#0891b2',
  '#ca8a04',
  '#db2777',
  '#16a34a',
  '#4f46e5',
  '#b91c1c',
]

export function categoryColor(categoryIndex: number): string {
  const n = CATEGORY_COLORS.length
  return CATEGORY_COLORS[((categoryIndex % n) + n) % n]
}
