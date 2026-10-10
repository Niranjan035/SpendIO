import { useState } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { fxApi } from '@/features/fx/api'
import { SEARCH_DEBOUNCE_MS } from '@/lib/constants'
import { MoneyInput, type MoneyInputProps } from './MoneyInput'

vi.mock('@/features/fx/api', () => ({
  fxApi: {
    convert: vi.fn(),
    currentRate: vi.fn(),
    history: vi.fn(),
    overrideRate: vi.fn(),
    refresh: vi.fn(),
  },
  todayIsoDate: () => '2026-10-07',
}))

const converted = {
  fromAmountMinor: 1234,
  toAmountMinor: 1333,
  rate: 1.08,
  rateDate: '2026-10-07',
  source: 'provider',
  stale: false,
}

function Harness({
  currency = 'EUR',
  baseCurrency = 'USD',
  hint,
}: {
  currency?: string
  baseCurrency?: string
  hint?: string
}) {
  const [value, setValue] = useState('')
  const props: MoneyInputProps = {
    label: 'Amount',
    currency,
    baseCurrency,
    hint,
    value,
    onChange: setValue,
  }
  return <MoneyInput {...props} />
}

beforeEach(() => {
  vi.mocked(fxApi.convert).mockResolvedValue(converted)
})

describe('MoneyInput', () => {
  it('surfaces keystrokes to the parent', async () => {
    const user = userEvent.setup()
    render(<Harness currency="EUR" baseCurrency="USD" />)

    await user.type(screen.getByLabelText('Amount'), '12.34')

    expect(screen.getByLabelText('Amount')).toHaveValue('12.34')
  })

  it('debounces one convert call and shows the base-currency value', async () => {
    const user = userEvent.setup()
    render(<Harness currency="EUR" baseCurrency="USD" />)

    await user.type(screen.getByLabelText('Amount'), '12.34')

    expect(await screen.findByText(/≈ \$13\.33/)).toBeInTheDocument()
    expect(screen.getByText(/1 EUR = 1\.08 USD/)).toBeInTheDocument()
    expect(vi.mocked(fxApi.convert)).toHaveBeenCalledTimes(1)
    expect(vi.mocked(fxApi.convert)).toHaveBeenCalledWith({
      fromCurrency: 'EUR',
      toCurrency: 'USD',
      amountMinor: 1234,
      date: '2026-10-07',
    })
  })

  it('keeps the field hint alongside the converted value', async () => {
    const user = userEvent.setup()
    render(<Harness currency="EUR" baseCurrency="USD" hint="Gross amount" />)

    await user.type(screen.getByLabelText('Amount'), '12.34')

    await screen.findByText(/≈ \$13\.33/)
    const hint = screen.getByText(/Gross amount/)
    expect(hint).toHaveTextContent('≈ $13.33')
  })

  it('marks a stale fallback rate', async () => {
    vi.mocked(fxApi.convert).mockResolvedValue({
      ...converted,
      stale: true,
      rateDate: '2026-09-01',
    })
    const user = userEvent.setup()
    render(<Harness currency="EUR" baseCurrency="USD" />)

    await user.type(screen.getByLabelText('Amount'), '12.34')

    expect(await screen.findByText(/stale rate from 2026-09-01/)).toBeInTheDocument()
  })

  it('never calls the API when the amount is already in the base currency', async () => {
    const user = userEvent.setup()
    render(<Harness currency="USD" baseCurrency="USD" />)

    await user.type(screen.getByLabelText('Amount'), '99.00')
    await new Promise((resolve) => setTimeout(resolve, SEARCH_DEBOUNCE_MS + 150))

    expect(vi.mocked(fxApi.convert)).not.toHaveBeenCalled()
    expect(screen.queryByText(/≈/)).toBeNull()
  })

  it('drops the hint while the amount is not a number', async () => {
    const user = userEvent.setup()
    render(<Harness currency="EUR" baseCurrency="USD" />)

    await user.type(screen.getByLabelText('Amount'), '12.34')
    expect(await screen.findByText(/≈ \$13\.33/)).toBeInTheDocument()

    await user.clear(screen.getByLabelText('Amount'))
    await user.type(screen.getByLabelText('Amount'), 'abc')

    expect(screen.queryByText(/≈/)).toBeNull()
  })
})
