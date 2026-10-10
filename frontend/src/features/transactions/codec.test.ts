import { describe, expect, it } from 'vitest'
import { parseFilter, serializeFilter } from './codec'
import type { TransactionFilter } from './types'

describe('parseFilter', () => {
  it('returns an empty filter for an empty query', () => {
    expect(parseFilter(new URLSearchParams())).toEqual({})
  })

  it('reads typed, multi-value and flag params', () => {
    const params = new URLSearchParams(
      'q=coffee&accountId=1&accountId=3&type=EXPENSE&type=INCOME&categoryType=EXPENSE&' +
        'direction=OUTFLOW&status=POSTED&from=2026-01-01&to=2026-01-31&minAmountMinor=500&' +
        'maxAmountMinor=9000&currencyCode=USD&baseCurrencyOnly=true&recurringOnly=false&' +
        'hasNotes=true&sort=amount,desc&page=2&size=25',
    )

    expect(parseFilter(params)).toEqual({
      q: 'coffee',
      accountIds: [1, 3],
      types: ['EXPENSE', 'INCOME'],
      categoryType: 'EXPENSE',
      directions: ['OUTFLOW'],
      statuses: ['POSTED'],
      from: '2026-01-01',
      to: '2026-01-31',
      minAmountMinor: 500,
      maxAmountMinor: 9000,
      currencyCode: 'USD',
      baseCurrencyOnly: true,
      recurringOnly: false,
      hasNotes: true,
      sort: 'amount,desc',
      page: 2,
      size: 25,
    })
  })

  it('ignores unknown and malformed values', () => {
    const params = new URLSearchParams('page=abc&size=1.5&accountId=x&type=NOPE&unknown=1')
    expect(parseFilter(params)).toEqual({})
  })
})

describe('serializeFilter', () => {
  it('omits unset values and keeps explicit false flags', () => {
    expect(serializeFilter({ types: ['EXPENSE'], recurringOnly: false }).toString()).toBe(
      'type=EXPENSE&recurringOnly=false',
    )
  })

  it('round-trips a full filter through the URL', () => {
    const filter: TransactionFilter = {
      q: 'rent march',
      accountIds: [1, 3],
      categoryIds: [9],
      categoryType: 'EXPENSE',
      types: ['EXPENSE'],
      directions: ['OUTFLOW'],
      statuses: ['POSTED', 'PENDING'],
      from: '2026-01-01',
      to: '2026-01-31',
      minAmountMinor: 100,
      maxAmountMinor: 50000,
      currencyCode: 'EUR',
      baseCurrencyOnly: true,
      recurringOnly: false,
      importedOnly: false,
      hasNotes: true,
      hasReversal: false,
      sort: 'amount,desc',
      page: 3,
      size: 25,
    }

    expect(parseFilter(serializeFilter(filter))).toEqual(filter)
  })
})
