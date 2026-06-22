import { Cell, Pie, PieChart, ResponsiveContainer } from 'recharts'
import { formatMinor } from '@/lib/money'

export interface DonutSlice {
  /** Category id, or null for the Uncategorized bucket. */
  id: string | null
  name: string
  color: string
  baseMinor: number
}

interface CategoryDonutProps {
  slices: DonutSlice[]
  currency: string
  totalBaseMinor: number
  centerTitle?: string
  onSelect?: (id: string | null) => void
}

/** A donut chart of category slices with the total shown in the centre. */
export function CategoryDonut({
  slices,
  currency,
  totalBaseMinor,
  centerTitle = 'Total',
  onSelect,
}: CategoryDonutProps) {
  return (
    <div className="relative h-72 w-full">
      <ResponsiveContainer width="100%" height="100%">
        <PieChart>
          <Pie
            data={slices}
            dataKey="baseMinor"
            nameKey="name"
            innerRadius="62%"
            outerRadius="92%"
            paddingAngle={slices.length > 1 ? 1 : 0}
            stroke="none"
            isAnimationActive={false}
            onClick={(_, index) => onSelect?.(slices[index]?.id ?? null)}
          >
            {slices.map((s, i) => (
              <Cell
                key={s.id ?? `null-${i}`}
                fill={s.color}
                cursor={onSelect ? 'pointer' : 'default'}
              />
            ))}
          </Pie>
        </PieChart>
      </ResponsiveContainer>
      <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
        <span className="text-xs uppercase tracking-wide text-slate-400">{centerTitle}</span>
        <span className="text-xl font-semibold text-slate-800">
          {formatMinor(totalBaseMinor, currency)}
        </span>
      </div>
    </div>
  )
}
