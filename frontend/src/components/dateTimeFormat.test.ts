import { describe, expect, it } from 'vitest'
import {
  formatAmountValue,
  formatDateTime,
  fromDatetimeLocalValue,
  toDatetimeLocalValue,
} from './dateTimeFormat'

describe('dateTimeFormat', () => {
  it('formats amounts without currency', () => {
    expect(formatAmountValue(99.9)).toBe('99.90')
  })

  it('formats instants as UTC date and minute', () => {
    expect(formatDateTime('2026-09-15T11:22:22.546024Z')).toBe(
      '2026-09-15 11:22',
    )
  })

  it('round-trips datetime-local values in UTC', () => {
    expect(toDatetimeLocalValue('2026-09-15T11:22:22.546024Z')).toBe(
      '2026-09-15T11:22',
    )
    expect(fromDatetimeLocalValue('2026-09-15T11:22')).toBe(
      '2026-09-15T11:22:00.000Z',
    )
  })
})
