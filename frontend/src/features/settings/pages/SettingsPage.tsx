import { Link, useLocation } from 'react-router-dom'
import type { ReactNode } from 'react'
import { cn } from '@/lib/cn'
import { paths } from '@/routes/paths'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { ProfileForm } from '../components/ProfileForm'
import { PreferencesForm } from '../components/PreferencesForm'
import { BaseCurrencyForm } from '../components/BaseCurrencyForm'
import { ChangePasswordForm } from '../components/ChangePasswordForm'
import { SessionsList } from '../components/SessionsList'
import { DataExportCard } from '../components/DataExportCard'
import { DeleteAccountCard } from '../components/DeleteAccountCard'
import { AuditLogTable } from '../components/AuditLogTable'

const TABS = [
  { to: paths.profileSettings, label: 'Profile' },
  { to: paths.preferencesSettings, label: 'Preferences' },
  { to: paths.securitySettings, label: 'Security' },
  { to: paths.dataSettings, label: 'Data & privacy' },
  { to: paths.auditLog, label: 'Audit log' },
] as const

/** /settings and /settings/profile are the same panel. */
function activeTab(pathname: string): string {
  if (pathname === paths.settings || pathname === paths.profileSettings) {
    return paths.profileSettings
  }
  return pathname
}

function UnknownSettingsPage({ pathname }: { pathname: string }) {
  return (
    <Card>
      <CardHeader title="Unknown settings page" />
      <CardBody className="flex flex-col gap-3">
        <p className="text-sm text-muted">There is nothing at {pathname}.</p>
        <Link to={paths.profileSettings} className="text-sm text-primary hover:underline">
          Back to profile settings
        </Link>
      </CardBody>
    </Card>
  )
}

export function SettingsPage() {
  const { pathname } = useLocation()
  const current = activeTab(pathname)

  let panel: ReactNode
  if (current === paths.profileSettings) {
    panel = (
      <Card>
        <CardHeader
          title="Profile"
          description="Your name, timezone and locale. The email address changes elsewhere."
        />
        <CardBody>
          <ProfileForm />
        </CardBody>
        <div className="border-t border-border px-4 py-4">
          <h3 className="text-sm font-semibold text-text">Base currency</h3>
          <div className="mt-3 max-w-sm">
            <BaseCurrencyForm />
          </div>
        </div>
      </Card>
    )
  } else if (current === paths.preferencesSettings) {
    panel = (
      <Card>
        <CardHeader
          title="Preferences"
          description="Formatting, reminders and accessibility - applied across the app."
        />
        <CardBody>
          <PreferencesForm />
        </CardBody>
      </Card>
    )
  } else if (current === paths.securitySettings) {
    panel = (
      <div className="flex flex-col gap-5">
        <Card>
          <CardHeader
            title="Password"
            description="Changing your password signs out every other session."
          />
          <CardBody>
            <ChangePasswordForm />
          </CardBody>
        </Card>
        <Card>
          <CardHeader
            title="Active sessions"
            description="Devices currently signed in to your account."
          />
          <CardBody>
            <SessionsList />
          </CardBody>
        </Card>
      </div>
    )
  } else if (current === paths.dataSettings) {
    panel = (
      <div className="flex flex-col gap-5">
        <Card>
          <CardHeader
            title="Export your data"
            description="Download everything SpendIO stores about you as JSON."
          />
          <CardBody>
            <DataExportCard />
          </CardBody>
        </Card>
        <Card>
          <CardHeader
            title="Delete account"
            description="Permanently remove your account and personal data."
          />
          <CardBody>
            <DeleteAccountCard />
          </CardBody>
        </Card>
      </div>
    )
  } else if (current === paths.auditLog) {
    panel = (
      <Card>
        <CardHeader
          title="Audit log"
          description="A tamper-evident record of the actions taken on your account."
        />
        <CardBody>
          <AuditLogTable />
        </CardBody>
      </Card>
    )
  } else {
    panel = <UnknownSettingsPage pathname={pathname} />
  }

  return (
    <div className="mx-auto flex max-w-4xl flex-col gap-5">
      <div>
        <h1 className="text-xl font-semibold text-text">Settings</h1>
        <p className="mt-1 text-sm text-muted">Profile, preferences, security and data.</p>
      </div>

      <nav className="flex flex-wrap gap-1 border-b border-border" aria-label="Settings sections">
        {TABS.map((tab) => (
          <Link
            key={tab.to}
            to={tab.to}
            aria-current={current === tab.to ? 'page' : undefined}
            className={cn(
              '-mb-px border-b-2 px-3 py-2 text-sm font-medium transition-colors',
              current === tab.to
                ? 'border-primary text-primary'
                : 'border-transparent text-muted hover:text-text',
            )}
          >
            {tab.label}
          </Link>
        ))}
      </nav>

      {panel}
    </div>
  )
}
