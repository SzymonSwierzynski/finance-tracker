import type { CategoryMover } from '@/api'

export type MoverState = 'new' | 'gone' | 'up' | 'down' | 'flat'

type MoverAmounts = Pick<CategoryMover, 'currentMinor' | 'previousMinor' | 'deltaMinor'>

/** Classifies a mover for arrow/colour/label at the display edge. */
export function moverState(m: MoverAmounts): MoverState {
  if (m.previousMinor === 0 && m.currentMinor > 0) return 'new'
  if (m.currentMinor === 0 && m.previousMinor > 0) return 'gone'
  if (m.deltaMinor > 0) return 'up'
  if (m.deltaMinor < 0) return 'down'
  return 'flat'
}

/** Signed % change vs the previous value, or null when there is no base to divide by. */
export function moverPercent(m: Pick<CategoryMover, 'deltaMinor' | 'previousMinor'>): number | null {
  return m.previousMinor !== 0 ? (m.deltaMinor / m.previousMinor) * 100 : null
}
