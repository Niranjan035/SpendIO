import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { ProtectedRoute } from './ProtectedRoute'
import { GuestOnlyRoute } from './GuestOnlyRoute'
import { useAuthStore } from '@/features/auth/auth.store'
import { paths } from './paths'

type Status = 'unknown' | 'authenticated' | 'anonymous'
type Kind = 'protected' | 'guest'

function renderGuard(status: Status, kind: Kind) {
  useAuthStore.setState({ status, user: null, hydrated: true })
  const guarded =
    kind === 'protected' ? (
      <ProtectedRoute>
        <div>secret</div>
      </ProtectedRoute>
    ) : (
      <GuestOnlyRoute>
        <div>login-form</div>
      </GuestOnlyRoute>
    )

  return render(
    <MemoryRouter initialEntries={[kind === 'protected' ? '/app' : '/signin']}>
      <Routes>
        <Route path="/app" element={guarded} />
        <Route path="/signin" element={guarded} />
        <Route path="/login" element={<p>login-page</p>} />
        <Route path={paths.dashboard} element={<p>Dashboard</p>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('ProtectedRoute', () => {
  it('renders children for an authenticated user', () => {
    renderGuard('authenticated', 'protected')
    expect(screen.getByText('secret')).toBeInTheDocument()
  })

  it('redirects an anonymous user to the login page', () => {
    renderGuard('anonymous', 'protected')
    expect(screen.queryByText('secret')).not.toBeInTheDocument()
    expect(screen.getByText('login-page')).toBeInTheDocument()
  })

  it('shows a loader until the session has been restored', () => {
    renderGuard('unknown', 'protected')
    expect(screen.getByText('Restoring session…')).toBeInTheDocument()
  })
})

describe('GuestOnlyRoute', () => {
  it('renders children for an anonymous visitor', () => {
    renderGuard('anonymous', 'guest')
    expect(screen.getByText('login-form')).toBeInTheDocument()
  })

  it('sends an authenticated user to the dashboard', () => {
    renderGuard('authenticated', 'guest')
    expect(screen.queryByText('login-form')).not.toBeInTheDocument()
    expect(screen.getByText('Dashboard')).toBeInTheDocument()
  })
})

describe('paths', () => {
  it('exposes every required route', () => {
    expect(paths.login).toBe('/login')
    expect(paths.dashboard).toBe('/dashboard')
    expect(paths.transaction('7')).toBe('/transactions/7')
  })
})
