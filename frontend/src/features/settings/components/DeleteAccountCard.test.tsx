import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ToastProvider } from '@/app/providers/ToastProvider'
import { settingsApi } from '../api'
import { DeleteAccountCard } from './DeleteAccountCard'

vi.mock('../api', () => ({
  settingsApi: {
    getProfile: vi.fn(),
    updateProfile: vi.fn(),
    changeBaseCurrency: vi.fn(),
    getPreferences: vi.fn(),
    savePreferences: vi.fn(),
    listCurrencies: vi.fn(),
    changePassword: vi.fn(),
    listSessions: vi.fn(),
    revokeSession: vi.fn(),
    logoutAll: vi.fn(),
    exportData: vi.fn(),
    deleteAccount: vi.fn(),
    listAuditLogs: vi.fn(),
  },
}))

function renderCard() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <DeleteAccountCard />
      </ToastProvider>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(settingsApi.deleteAccount).mockResolvedValue(undefined)
})

describe('DeleteAccountCard', () => {
  it('does nothing until the dialog is confirmed', async () => {
    renderCard()
    const user = userEvent.setup()

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Delete account' }))
    expect(screen.getByRole('dialog', { name: 'Delete your account' })).toBeInTheDocument()
    expect(settingsApi.deleteAccount).not.toHaveBeenCalled()
  })

  it('refuses a wrong confirmation phrase', async () => {
    renderCard()
    const user = userEvent.setup()

    await user.click(screen.getByRole('button', { name: 'Delete account' }))
    const dialog = within(screen.getByRole('dialog', { name: 'Delete your account' }))
    await user.type(dialog.getByLabelText('Password'), 'secret')
    await user.type(dialog.getByLabelText('Type DELETE to confirm'), 'delete')
    await user.click(dialog.getByRole('button', { name: 'Delete account' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Type DELETE to confirm')
    expect(settingsApi.deleteAccount).not.toHaveBeenCalled()
  })

  it('submits the password and phrase to the API', async () => {
    renderCard()
    const user = userEvent.setup()

    await user.click(screen.getByRole('button', { name: 'Delete account' }))
    const dialog = within(screen.getByRole('dialog', { name: 'Delete your account' }))
    await user.type(dialog.getByLabelText('Password'), 'secret')
    await user.type(dialog.getByLabelText('Type DELETE to confirm'), 'DELETE')
    await user.click(dialog.getByRole('button', { name: 'Delete account' }))

    await waitFor(() =>
      expect(settingsApi.deleteAccount).toHaveBeenCalledWith({
        password: 'secret',
        confirmationText: 'DELETE',
      }),
    )
  })
})
