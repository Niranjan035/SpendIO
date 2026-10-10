import { afterEach, describe, expect, it } from 'vitest'
import { renderHook } from '@testing-library/react'
import { useAuthStore } from '@/features/auth/auth.store'
import { DEFAULT_DISPLAY_CURRENCY, useDisplayCurrency } from './currency'

afterEach(() => {
  useAuthStore.setState({ status: 'anonymous', user: null, hydrated: true })
})

describe('useDisplayCurrency', () => {
  it('falls back to the backend default when no user is loaded', () => {
    const { result } = renderHook(() => useDisplayCurrency())
    expect(result.current).toBe(DEFAULT_DISPLAY_CURRENCY)
    expect(result.current).toBe('USD')
  })

  it('reads the session user’s base currency', () => {
    useAuthStore.setState({
      status: 'authenticated',
      user: {
        id: '42',
        email: 'ana@example.com',
        displayName: 'Ana',
        baseCurrency: 'INR',
      },
      hydrated: true,
    })
    const { result } = renderHook(() => useDisplayCurrency())
    expect(result.current).toBe('INR')
  })
})