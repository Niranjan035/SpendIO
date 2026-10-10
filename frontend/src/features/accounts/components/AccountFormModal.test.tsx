import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ToastProvider } from '@/app/providers/ToastProvider'
import { ApiError } from '@/services/http/ApiError'
import { accountsApi } from '../api'
import type { Account } from '../types'
import { AccountFormModal } from './AccountFormModal'

vi.mock('../api', () => ({
  accountsApi: {
    list: vi.fn(),
    create: vi.fn(),
    summary: vi.fn(),
    detail: vi.fn(),
    update: vi.fn(),
    remove: vi.fn(),
    archive: vi.fn(),
    restore: vi.fn(),
    setPrimary: vi.fn(),
    balance: vi.fn(),
    balances: vi.fn(),
    adjust: vi.fn(),
  },
}))

const created: Account = {
  id: 11,
  name: 'Check-in',
  type: 'CASH',
  currencyCode: 'INR',
  openingBalanceMinor: 450000,
  openingBalanceDate: '2026-10-06',
  institution: null,
  last4: null,
  creditLimitMinor: null,
  billingDay: null,
  dueDay: null,
  icon: null,
  color: '#12AB34',
  notes: 'checkin daily',
  primary: true,
  status: 'ACTIVE',
  transactionCount: 0,
  createdAt: '2026-10-06T00:00:00Z',
  balanceMinor: 450000,
  balanceBaseMinor: 450000,
  baseCurrencyCode: 'USD',
  availableCreditMinor: null,
}

function renderModal() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const onClose = vi.fn()
  render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <AccountFormModal account={null} open onClose={onClose} />
      </ToastProvider>
    </QueryClientProvider>,
  )
  return onClose
}

const editable: Account = {
  id: 14,
  name: 'Everyday checking',
  type: 'BANK',
  currencyCode: 'EUR',
  openingBalanceMinor: 1000,
  openingBalanceDate: '2026-01-01',
  institution: 'Revolut',
  last4: null,
  creditLimitMinor: null,
  billingDay: null,
  dueDay: null,
  icon: null,
  color: '#12AB34',
  notes: 'Main spending card',
  primary: true,
  status: 'ACTIVE',
  transactionCount: 3,
  createdAt: '2026-01-01T09:00:00Z',
  balanceMinor: 125000,
  balanceBaseMinor: 125000,
  baseCurrencyCode: 'EUR',
  availableCreditMinor: null,
}

function renderEditModal() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const onClose = vi.fn()
  render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <AccountFormModal account={editable} open onClose={onClose} />
      </ToastProvider>
    </QueryClientProvider>,
  )
  return onClose
}

async function fillReportedValues(user: ReturnType<typeof userEvent.setup>) {
  await user.selectOptions(screen.getByLabelText('Type'), 'CASH')
  await user.type(screen.getByLabelText('Name'), 'Check-in')
  await user.type(screen.getByLabelText('Currency'), 'INR')
  await user.type(screen.getByLabelText('Opening amount'), '4500')
  await user.type(screen.getByLabelText('Opening date'), '2026-10-06')
  await user.type(screen.getByLabelText('Colour'), '#12AB34')
  await user.type(screen.getByLabelText('Notes'), 'checkin daily')
  await user.click(screen.getByLabelText('Make this the primary account'))
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('AccountFormModal', () => {
  it('creates the reported Cash/INR account with null optionals', async () => {
    const user = userEvent.setup()
    vi.mocked(accountsApi.create).mockResolvedValue(created)
    const onClose = renderModal()

    await fillReportedValues(user)
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    expect(await screen.findByText('Account created')).toBeInTheDocument()
    expect(accountsApi.create).toHaveBeenCalledWith({
      name: 'Check-in',
      type: 'CASH',
      currencyCode: 'INR',
      openingBalanceMinor: 450000,
      openingBalanceDate: '2026-10-06',
      institution: null,
      last4: null,
      creditLimitMinor: null,
      billingDay: null,
      dueDay: null,
      icon: null,
      color: '#12AB34',
      notes: 'checkin daily',
      primary: true,
      openingTransactionDate: null,
      createOpeningBalanceTransaction: false,
    })
    expect(onClose).toHaveBeenCalled()
  })

  it('surfaces backend field errors on the form without a generic toast', async () => {
    const user = userEvent.setup()
    vi.mocked(accountsApi.create).mockRejectedValue(
      new ApiError(400, {
        status: 400,
        code: 'VALIDATION_ERROR',
        detail: 'Request validation failed',
        fieldErrors: [{ field: 'name', message: 'name is already taken' }],
      }),
    )
    renderModal()

    await user.type(screen.getByLabelText('Name'), 'Check-in')
    await user.type(screen.getByLabelText('Currency'), 'INR')
    await user.type(screen.getByLabelText('Opening amount'), '0')
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    expect(await screen.findByText('name is already taken')).toBeInTheDocument()
    expect(screen.queryByText('Request validation failed')).not.toBeInTheDocument()
  })

  it('falls back to a toast with the backend message for unmapped errors', async () => {
    const user = userEvent.setup()
    vi.mocked(accountsApi.create).mockRejectedValue(
      new ApiError(400, {
        status: 400,
        code: 'VALIDATION_ERROR',
        detail: 'Request validation failed',
        fieldErrors: [
          {
            field: 'createAccountRequest',
            message: 'openingBalanceDate is required when openingBalanceMinor is non-zero',
          },
        ],
      }),
    )
    renderModal()

    await user.type(screen.getByLabelText('Name'), 'Check-in')
    await user.type(screen.getByLabelText('Currency'), 'INR')
    await user.type(screen.getByLabelText('Opening amount'), '0')
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    expect(
      await screen.findByText('openingBalanceDate is required when openingBalanceMinor is non-zero'),
    ).toBeInTheDocument()
    expect(screen.queryByText('Request validation failed')).not.toBeInTheDocument()
  })

  it('blocks submit when the client schema rejects the input', async () => {
    const user = userEvent.setup()
    renderModal()

    await user.type(screen.getByLabelText('Name'), 'Check-in')
    await user.type(screen.getByLabelText('Currency'), 'INR')
    await user.type(screen.getByLabelText('Opening amount'), '4500')
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    expect(await screen.findByText('Pick an opening date')).toBeInTheDocument()
    expect(accountsApi.create).not.toHaveBeenCalled()
  })
})

describe('AccountFormModal edit mode', () => {
  it('saves an edited name despite the create-only fields being empty', async () => {
    const user = userEvent.setup()
    vi.mocked(accountsApi.update).mockResolvedValue({ ...editable, name: 'Everyday checking v2' })
    const onClose = renderEditModal()

    expect(screen.queryByLabelText('Currency')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Type')).toBeDisabled()

    const name = screen.getByLabelText('Name')
    await user.clear(name)
    await user.type(name, 'Everyday checking v2')
    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    expect(await screen.findByText('Account updated')).toBeInTheDocument()
    expect(accountsApi.update).toHaveBeenCalledWith(14, {
      name: 'Everyday checking v2',
      institution: 'Revolut',
      last4: null,
      color: '#12AB34',
      notes: 'Main spending card',
    })
    expect(onClose).toHaveBeenCalled()
  })

  it('shows a field-level validation error without calling the API', async () => {
    const user = userEvent.setup()
    renderEditModal()

    await user.clear(screen.getByLabelText('Name'))
    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    expect(await screen.findByText('Name is required')).toBeInTheDocument()
    expect(accountsApi.update).not.toHaveBeenCalled()
  })

  it('surfaces API failures as a visible toast without closing the modal', async () => {
    const user = userEvent.setup()
    vi.mocked(accountsApi.update).mockRejectedValue(
      new ApiError(503, {
        status: 503,
        code: 'SERVICE_UNAVAILABLE',
        detail: 'Account service unavailable',
        fieldErrors: [],
      }),
    )
    const onClose = renderEditModal()

    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    expect(await screen.findByText('Account service unavailable')).toBeInTheDocument()
    expect(screen.queryByText('Account updated')).not.toBeInTheDocument()
    expect(onClose).not.toHaveBeenCalled()
  })

  it('maps a backend field error onto the Name input', async () => {
    const user = userEvent.setup()
    vi.mocked(accountsApi.update).mockRejectedValue(
      new ApiError(409, {
        status: 409,
        code: 'DUPLICATE_ACCOUNT_NAME',
        detail: 'Request failed',
        fieldErrors: [{ field: 'name', message: 'An account with that name already exists' }],
      }),
    )
    renderEditModal()

    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    expect(await screen.findByText('An account with that name already exists')).toBeInTheDocument()
    expect(screen.queryByText('Request failed')).not.toBeInTheDocument()
  })
})
