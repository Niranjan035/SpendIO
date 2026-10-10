import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { LoginForm } from './LoginForm'
import { useAuthStore } from '../auth.store'
import { clearAccessToken } from '@/services/http/tokenManager'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function renderLoginForm() {
  const client = new QueryClient()
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/login']}>
        <Routes>
          <Route path="/login" element={<LoginForm />} />
          <Route path="/dashboard" element={<p>Dashboard</p>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

async function submit(email: string, password: string) {
  const user = userEvent.setup()
  await user.type(screen.getByLabelText('Email'), email)
  await user.type(screen.getByLabelText('Password'), password)
  await user.click(screen.getByRole('button', { name: 'Sign in' }))
}

beforeEach(() => {
  clearAccessToken()
  useAuthStore.setState({ status: 'unknown', user: null, hydrated: false })
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('LoginForm', () => {
  it('shows a validation error without calling the API', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    renderLoginForm()

    await submit('not-an-email', 'x')

    expect(await screen.findByText('Enter a valid email address')).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('reports bad credentials on 401', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(
          jsonResponse(
            { status: 401, code: 'INVALID_CREDENTIALS', detail: 'Invalid credentials' },
            401,
          ),
        ),
    )
    renderLoginForm()

    await submit('ana@example.com', 'whatever1')

    expect(await screen.findByRole('alert')).toHaveTextContent('Email or password is incorrect.')
    expect(useAuthStore.getState().status).toBe('unknown')
  })

  it('reports the rate limit on 429', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse({ status: 429, code: 'RATE_LIMITED' }, 429)),
    )
    renderLoginForm()

    await submit('ana@example.com', 'whatever1')

    expect(await screen.findByRole('alert')).toHaveTextContent('Too many attempts.')
  })

  it('stores the session and navigates to the dashboard on success', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({
          accessToken: 'fresh-token',
          expiresIn: 900,
          tokenType: 'Bearer',
          user: {
            id: 9,
            email: 'ana@example.com',
            firstName: 'Ana',
            lastName: 'Lopes',
            displayName: 'Ana Lopes',
            timezone: 'Europe/Lisbon',
            locale: 'en-GB',
            baseCurrencyCode: 'EUR',
          },
        }),
      ),
    )
    renderLoginForm()

    await submit('ana@example.com', 'Str0ngPassphrase1')

    expect(await screen.findByText('Dashboard')).toBeInTheDocument()
    const state = useAuthStore.getState()
    expect(state.status).toBe('authenticated')
    expect(state.user).toMatchObject({ id: '9', baseCurrency: 'EUR', displayName: 'Ana Lopes' })
  })
})
