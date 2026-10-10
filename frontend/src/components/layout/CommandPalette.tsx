import { useEffect, useId, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  BarChart3,
  Bell,
  CreditCard,
  FileUp,
  LayoutDashboard,
  List,
  PiggyBank,
  Repeat,
  Settings,
  Target,
  Wallet,
} from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import { cn } from '@/lib/cn'
import { paths } from '@/routes/paths'

interface Command {
  label: string
  to: string
  icon: LucideIcon
  keywords: string
}

const COMMANDS: Command[] = [
  { label: 'Dashboard', to: paths.dashboard, icon: LayoutDashboard, keywords: 'home overview' },
  { label: 'Transactions', to: paths.transactions, icon: List, keywords: 'ledger entries' },
  { label: 'Accounts', to: paths.accounts, icon: Wallet, keywords: 'balance bank wallet' },
  { label: 'Categories', to: paths.categories, icon: CreditCard, keywords: 'tags groups' },
  { label: 'Budgets', to: paths.budgets, icon: BarChart3, keywords: 'limits spending' },
  { label: 'Goals', to: paths.goals, icon: Target, keywords: 'savings targets' },
  { label: 'Recurring', to: paths.recurring, icon: Repeat, keywords: 'subscriptions schedule' },
  { label: 'Import', to: paths.import, icon: FileUp, keywords: 'csv upload' },
  { label: 'Analytics', to: paths.analytics, icon: PiggyBank, keywords: 'reports charts' },
  { label: 'Notifications', to: paths.notifications, icon: Bell, keywords: 'alerts inbox' },
  { label: 'Settings', to: paths.settings, icon: Settings, keywords: 'profile preferences security' },
]

/**
 * Global command palette (ARCHITECTURE.md P1): Ctrl/Cmd+K opens a fuzzy-filterable
 * list of destinations; arrows move the active option and Enter navigates.
 */
export function CommandPalette() {
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const [activeIndex, setActiveIndex] = useState(0)
  const listboxId = useId()

  const matches = useMemo(() => {
    const needle = query.trim().toLowerCase()
    if (!needle) return COMMANDS
    return COMMANDS.filter(
      (command) =>
        command.label.toLowerCase().includes(needle) ||
        command.keywords.toLowerCase().includes(needle),
    )
  }, [query])

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault()
        setOpen((current) => {
          if (!current) {
            setQuery('')
            setActiveIndex(0)
          }
          return !current
        })
      }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [])

  if (!open) return null

  const close = () => setOpen(false)

  const select = (command: Command | undefined) => {
    if (!command) return
    close()
    navigate(command.to)
  }

  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center p-4 sm:pt-24">
      <div className="absolute inset-0 bg-black/40" onClick={close} aria-hidden="true" />
      <div
        role="dialog"
        aria-modal="true"
        aria-label="Command palette"
        className="relative z-10 w-full max-w-lg overflow-hidden rounded-lg border border-border-strong bg-surface shadow-lg"
      >
        <input
          autoFocus
          role="combobox"
          aria-expanded
          aria-controls={listboxId}
          aria-activedescendant={
            matches[activeIndex] ? `${listboxId}-option-${activeIndex}` : undefined
          }
          autoComplete="off"
          placeholder="Search pages…"
          value={query}
          onChange={(event) => {
            setQuery(event.target.value)
            setActiveIndex(0)
          }}
          onKeyDown={(event) => {
            if (event.key === 'Escape') close()
            if (event.key === 'ArrowDown') {
              event.preventDefault()
              setActiveIndex((current) => Math.min(current + 1, matches.length - 1))
            }
            if (event.key === 'ArrowUp') {
              event.preventDefault()
              setActiveIndex((current) => Math.max(current - 1, 0))
            }
            if (event.key === 'Enter') {
              event.preventDefault()
              select(matches[activeIndex])
            }
          }}
          className="h-12 w-full border-b border-border bg-surface px-4 text-sm text-text placeholder:text-subtle focus:outline-none"
        />

        <ul id={listboxId} role="listbox" className="max-h-80 overflow-y-auto py-1">
          {matches.length === 0 ? (
            <li className="px-4 py-6 text-center text-sm text-muted">No matches.</li>
          ) : (
            matches.map((command, index) => {
              const Icon = command.icon
              return (
                <li
                  key={command.to}
                  id={`${listboxId}-option-${index}`}
                  role="option"
                  aria-selected={index === activeIndex}
                  onMouseEnter={() => setActiveIndex(index)}
                  onClick={() => select(command)}
                  className={cn(
                    'flex cursor-pointer items-center gap-3 px-4 py-2 text-sm',
                    index === activeIndex ? 'bg-primary-subtle text-primary' : 'text-text',
                  )}
                >
                  <Icon className="h-4 w-4" aria-hidden />
                  {command.label}
                </li>
              )
            })
          )}
        </ul>
      </div>
    </div>
  )
}
