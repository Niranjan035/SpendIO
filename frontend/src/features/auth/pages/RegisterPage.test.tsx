import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RegisterPage } from './RegisterPage'
import { useAuthStore } from '../auth.store'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function renderRegisterPage() {
  const client = new QueryClient()
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/register']}>
        <Routes>
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/dashboard" element={<p>Dashboard</p>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  useAuthStore.setState({ status: 'unknown', user: null, hydrated: false })
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('RegisterPage', () => {
  it('requires the profile fields before posting', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    renderRegisterPage()
    const user = userEvent.setup()

    await user.click(screen.getByRole('button', { name: 'Create account' }))

    expect(await screen.findByText('First name is required')).toBeInTheDocument()
    expect(await screen.findByText('Last name is required')).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('submits the backend RegisterRequest shape and signs the user in', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({
        accessToken: 'brand-new-token',
        expiresIn: 900,
        tokenType: 'Bearer',
        user: {
          id: 12,
          email: 'rita@example.com',
          firstName: 'Rita',
          lastName: 'Silva',
          displayName: 'Rita Silva',
          timezone: 'UTC',
          locale: 'en-GB',
          baseCurrencyCode: 'EUR',
        },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)
    renderRegisterPage()
    const user = userEvent.setup()

    await user.type(screen.getByLabelText('First name'), 'Rita')
    await user.type(screen.getByLabelText('Last name'), 'Silva')
    await user.type(screen.getByLabelText('Email'), 'rita@example.com')
    await user.type(screen.getByLabelText('Password'), 'Str0ngPassphrase1')
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    expect(await screen.findByText('Dashboard')).toBeInTheDocument()

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/v1/auth/register')
    const body = JSON.parse(String(init.body)) as Record<string, unknown>
    expect(body).toMatchObject({
      email: 'rita@example.com',
      password: 'Str0ngPassphrase1',
      firstName: 'Rita',
      lastName: 'Silva',
      baseCurrencyCode: 'USD',
    })
    expect(typeof body.timezone).toBe('string')
    expect(String(body.timezone).length).toBeGreaterThan(0)
    expect(typeof body.locale).toBe('string')
    expect(String(body.locale).length).toBeGreaterThan(0)
    expect(useAuthStore.getState().status).toBe('authenticated')
  })

  it('surfaces a duplicate email as a conflict message', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(
          jsonResponse({ status: 409, code: 'USER_EMAIL_TAKEN', detail: 'Email taken' }, 409),
        ),
    )
    renderRegisterPage()
    const user = userEvent.setup()

    await user.type(screen.getByLabelText('First name'), 'Rita')
    await user.type(screen.getByLabelText('Last name'), 'Silva')
    await user.type(screen.getByLabelText('Email'), 'rita@example.com')
    await user.type(screen.getByLabelText('Password'), 'Str0ngPassphrase1')
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'An account with that email already exists.',
    )
  })
})
