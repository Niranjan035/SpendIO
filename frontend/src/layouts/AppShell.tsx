import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useState } from 'react'
import {
  BarChart3,
  Bell,
  CreditCard,
  FileUp,
  LayoutDashboard,
  List,
  LogOut,
  PiggyBank,
  Repeat,
  Settings,
  Target,
  Wallet,
} from 'lucide-react'
import { cn } from '@/lib/cn'
import { paths } from '@/routes/paths'
import { useAuthStore } from '@/features/auth/auth.store'
import { useLogout } from '@/features/auth/hooks'
import { useTheme } from '@/app/providers/ThemeProvider'
import { NotificationBell } from '@/features/notifications/components/NotificationBell'
import { CommandPalette } from '@/components/layout/CommandPalette'

const NAV = [
  { to: paths.dashboard, label: 'Dashboard', icon: LayoutDashboard },
  { to: paths.transactions, label: 'Transactions', icon: List },
  { to: paths.accounts, label: 'Accounts', icon: Wallet },
  { to: paths.categories, label: 'Categories', icon: CreditCard },
  { to: paths.budgets, label: 'Budgets', icon: BarChart3 },
  { to: paths.goals, label: 'Goals', icon: Target },
  { to: paths.recurring, label: 'Recurring', icon: Repeat },
  { to: paths.import, label: 'Import', icon: FileUp },
  { to: paths.analytics, label: 'Analytics', icon: PiggyBank },
  { to: paths.notifications, label: 'Notifications', icon: Bell },
  { to: paths.settings, label: 'Settings', icon: Settings },
]

export function AppShell() {
  const user = useAuthStore((s) => s.user)
  const logout = useLogout()
  const navigate = useNavigate()
  const { resolved, setTheme } = useTheme()
  const [drawerOpen, setDrawerOpen] = useState(false)

  const navItems = (
    <nav className="flex flex-col gap-1" aria-label="Primary">
      {NAV.map(({ to, label, icon: Icon }) => (
        <NavLink
          key={to}
          to={to}
          onClick={() => setDrawerOpen(false)}
          className={({ isActive }) =>
            cn(
              'flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors',
              isActive
                ? 'bg-primary-subtle text-primary'
                : 'text-muted hover:bg-surface-sunken hover:text-text',
            )
          }
        >
          <Icon className="h-4 w-4" aria-hidden />
          {label}
        </NavLink>
      ))}
    </nav>
  )

  return (
    <div className="flex min-h-screen bg-bg">
      <a href="#main" className="skip-link">
        Skip to content
      </a>

      <aside className="hidden w-56 shrink-0 border-r border-border bg-surface p-3 lg:block">
        <div className="mb-5 flex items-center gap-2 px-2">
          <span className="flex h-7 w-7 items-center justify-center rounded-md bg-primary text-xs font-bold text-white">
            S
          </span>
          <span className="text-sm font-semibold text-text">SpendIO</span>
        </div>
        {navItems}
      </aside>

      {drawerOpen && (
        <div className="fixed inset-0 z-40 lg:hidden">
          <button
            type="button"
            aria-label="Close menu"
            className="absolute inset-0 bg-black/40"
            onClick={() => setDrawerOpen(false)}
          />
          <aside className="absolute inset-y-0 left-0 w-64 border-r border-border bg-surface p-3">
            <div className="mb-5 flex items-center justify-between px-2">
              <span className="text-sm font-semibold text-text">SpendIO</span>
              <button type="button" onClick={() => setDrawerOpen(false)} aria-label="Close menu">
                ×
              </button>
            </div>
            {navItems}
          </aside>
        </div>
      )}

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex h-14 items-center justify-between gap-3 border-b border-border bg-surface px-4">
          <button
            type="button"
            className="rounded-md border border-border px-2 py-1 text-sm lg:hidden"
            onClick={() => setDrawerOpen(true)}
            aria-label="Open menu"
          >
            ☰
          </button>
          <div className="flex-1" />
          <button
            type="button"
            className="hidden items-center gap-2 rounded-md border border-border px-2 py-1 text-xs text-muted hover:text-text sm:flex"
            onClick={() =>
              window.dispatchEvent(
                new KeyboardEvent('keydown', { key: 'k', ctrlKey: true, bubbles: true }),
              )
            }
          >
            Search
            <kbd className="rounded border border-border px-1 text-[10px]">Ctrl K</kbd>
          </button>
          <button
            type="button"
            className="rounded-md border border-border px-2 py-1 text-xs text-muted"
            onClick={() => setTheme(resolved === 'dark' ? 'light' : 'dark')}
          >
            {resolved === 'dark' ? 'Light' : 'Dark'} theme
          </button>
          <div className="flex items-center gap-2">
            <NotificationBell />
            <span className="hidden text-sm text-muted sm:block">{user?.displayName}</span>
            <button
              type="button"
              className="flex items-center gap-1 rounded-md border border-border px-2 py-1 text-xs text-muted hover:text-text"
              onClick={async () => {
                await logout()
                navigate(paths.login, { replace: true })
              }}
            >
              <LogOut className="h-3.5 w-3.5" aria-hidden />
              Sign out
            </button>
          </div>
        </header>

        <main id="main" className="min-w-0 flex-1 px-4 py-6 lg:px-8">
          <Outlet />
        </main>
      </div>

      <CommandPalette />
    </div>
  )
}
