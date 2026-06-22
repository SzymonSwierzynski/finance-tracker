import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import '@/lib/i18n'
import { Money } from './Money'

describe('<Money />', () => {
  it('renders integer minor units as formatted currency', () => {
    render(<Money minor={1999} currency="PLN" />)
    // Formatted value contains the major-unit amount (locale separator may vary).
    expect(screen.getByText(/19[.,]99/)).toBeInTheDocument()
  })

  it('keeps the raw minor units in the title for traceability', () => {
    render(<Money minor={43000} currency="PLN" />)
    expect(screen.getByTitle('43000 minor units')).toBeInTheDocument()
  })
})
