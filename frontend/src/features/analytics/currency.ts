import { useAuthStore } from '@/features/auth/auth.store'

/** Fallback display currency when the user has not picked one (backend default). */
export const DEFAULT_DISPLAY_CURRENCY = 'USD'

/**
 * The user's preferred display currency, traced from Settings → the profile's
 * {@code defaultCurrencyCode} → the session user. Analytics amounts are frozen
 * base-currency minor units (ADR-0002/0005), so this only labels them; it never
 * converts them.
 */
export function useDisplayCurrency(): string {
  return useAuthStore((s) => s.user?.baseCurrency) ?? DEFAULT_DISPLAY_CURRENCY
}