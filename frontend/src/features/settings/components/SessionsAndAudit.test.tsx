import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ToastProvider } from '@/app/providers/ToastProvider'
import { settingsApi } from '../api'
import { AuditLogTable } from './AuditLogTable'
import { SessionsList } from './SessionsList'
import type { AuditLogEntry, UserSession } from '../types'

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

function renderWithProviders(ui: React.ReactElement) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  render(
    <QueryClientProvider client={client}>
      <ToastProvider>{ui}</ToastProvider>
    </QueryClientProvider>,
  )
}

const currentSession: UserSession = {
  id: 's-now',
  deviceLabel: 'This laptop',
  ipAddress: '127.0.0.1',
  current: true,
  createdAt: '2026-02-01T09:00:00Z',
  lastUsedAt: '2026-02-01T09:00:00Z',
  expiresAt: '2026-03-01T09:00:00Z',
}

const otherSession: UserSession = {
  ...currentSession,
  id: 's-other',
  deviceLabel: 'Phone',
  current: false,
}

const auditEntry: AuditLogEntry = {
  id: 1,
  actorType: 'USER',
  action: 'PASSWORD_CHANGED',
  entityType: null,
  entityId: null,
  ipAddress: '127.0.0.1',
  traceId: null,
  before: null,
  after: null,
  createdAt: '2026-02-01T09:00:00Z',
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(settingsApi.revokeSession).mockResolvedValue(undefined)
  vi.mocked(settingsApi.listSessions).mockResolvedValue([currentSession, otherSession])
  vi.mocked(settingsApi.listAuditLogs).mockResolvedValue({
    content: [auditEntry],
    page: 0,
    size: 20,
    totalElements: 1,
    totalPages: 1,
    first: true,
    last: true,
  })
})

describe('SessionsList', () => {
  it('never offers to revoke the current session', async () => {
    renderWithProviders(<SessionsList />)

    expect(await screen.findByText('This laptop')).toBeInTheDocument()
    expect(screen.getByText('This device')).toBeInTheDocument()
    expect(screen.getByText('Phone')).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: 'Revoke' })).toHaveLength(1)
  })

  it('revokes another device', async () => {
    renderWithProviders(<SessionsList />)
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Revoke' }))

    await waitFor(() => expect(settingsApi.revokeSession).toHaveBeenCalledWith('s-other'))
  })
})

describe('AuditLogTable', () => {
  it('renders the caller audit trail', async () => {
    renderWithProviders(<AuditLogTable />)

    expect(await screen.findByText('Password changed')).toBeInTheDocument()
    expect(settingsApi.listAuditLogs).toHaveBeenCalledWith(0, 20)
  })
})
