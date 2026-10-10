import { Outlet } from 'react-router-dom'

/** Centred card layout for login / register / password flows. */
export function AuthLayout() {
  return (
    <div className="flex min-h-screen bg-bg">
      <a href="#main" className="skip-link">
        Skip to content
      </a>

      <div className="hidden w-1/2 flex-col justify-between bg-surface-sunken p-10 lg:flex">
        <div className="flex items-center gap-2">
          <span className="flex h-8 w-8 items-center justify-center rounded-md bg-primary text-sm font-bold text-white">
            S
          </span>
          <span className="text-base font-semibold text-text">SpendIO</span>
        </div>
        <div>
          <p className="max-w-md text-2xl font-semibold text-text">
            Every currency, account and rupee in one place.
          </p>
          <p className="mt-3 max-w-md text-sm text-muted">
            Track spending against budgets, watch goals compound, and import your statements — with
            a ledger that never rounds a number it should not.
          </p>
        </div>
        <p className="text-xs text-subtle">Personal finance, self-hosted.</p>
      </div>

      <main id="main" className="flex w-full items-center justify-center px-6 py-10 lg:w-1/2">
        <div className="w-full max-w-sm">
          <div className="mb-6 flex items-center gap-2 lg:hidden">
            <span className="flex h-8 w-8 items-center justify-center rounded-md bg-primary text-sm font-bold text-white">
              S
            </span>
            <span className="text-base font-semibold text-text">SpendIO</span>
          </div>
          <Outlet />
        </div>
      </main>
    </div>
  )
}
