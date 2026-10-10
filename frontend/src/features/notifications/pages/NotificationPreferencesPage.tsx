import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { Select } from '@/components/ui/Select'
import { LoadingSpinner } from '@/components/feedback/LoadingSpinner'
import { useToast } from '@/app/providers/ToastProvider'
import { cn } from '@/lib/cn'
import { useNotificationPreferences, useUpdateNotificationPreference } from '../hooks'
import {
  NOTIFICATION_SEVERITIES,
  NOTIFICATION_SEVERITY_LABELS,
  NOTIFICATION_TYPES,
  NOTIFICATION_TYPE_LABELS,
} from '../labels'
import type { NotificationChannel, NotificationPreference, NotificationSeverity } from '../types'

const CHANNEL_LABELS: Record<NotificationChannel, string> = {
  IN_APP: 'In-app',
  EMAIL: 'Email',
}

function Toggle({
  enabled,
  onChange,
  label,
}: {
  enabled: boolean
  onChange: (enabled: boolean) => void
  label: string
}) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={enabled}
      aria-label={label}
      className={cn(
        'relative h-6 w-11 rounded-full transition-colors',
        enabled ? 'bg-primary' : 'bg-border-strong',
      )}
      onClick={() => onChange(!enabled)}
    >
      <span
        className={cn(
          'absolute top-0.5 h-5 w-5 rounded-full bg-white shadow transition-transform',
          enabled ? 'translate-x-[22px]' : 'translate-x-0.5',
        )}
      />
    </button>
  )
}

function ChannelRow({
  preference,
  updating,
  onUpdate,
}: {
  preference: NotificationPreference
  updating: boolean
  onUpdate: (enabled: boolean, minSeverity: NotificationSeverity | null) => void
}) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-3">
      <div className="flex items-center gap-3">
        <Toggle
          enabled={preference.enabled}
          onChange={(enabled) => onUpdate(enabled, preference.minSeverity)}
          label={`${CHANNEL_LABELS[preference.channel]} notifications`}
        />
        <span className="text-sm text-text">{CHANNEL_LABELS[preference.channel]}</span>
        {updating && <LoadingSpinner label="Saving" />}
      </div>
      <div className="w-36">
        <Select
          aria-label={`Minimum severity for ${CHANNEL_LABELS[preference.channel]}`}
          value={preference.minSeverity ?? 'MEDIUM'}
          disabled={updating}
          onChange={(event) =>
            onUpdate(preference.enabled, event.target.value as NotificationSeverity | null)
          }
        >
          {NOTIFICATION_SEVERITIES.map((option) => (
            <option key={option} value={option}>
              {NOTIFICATION_SEVERITY_LABELS[option]}+
            </option>
          ))}
        </Select>
      </div>
    </div>
  )
}

export function NotificationPreferencesPage() {
  const toast = useToast()
  const preferences = useNotificationPreferences()
  const updatePreference = useUpdateNotificationPreference()
  const rows = preferences.data?.preferences ?? []

  const rowFor = (
    type: (typeof NOTIFICATION_TYPES)[number],
    channel: NotificationChannel,
  ): NotificationPreference | undefined =>
    rows.find((row) => row.type === type && row.channel === channel)

  const onUpdate = (type: (typeof NOTIFICATION_TYPES)[number], channel: NotificationChannel) => (
    enabled: boolean,
    minSeverity: NotificationSeverity | null,
  ) => {
    const floor = minSeverity ?? 'MEDIUM'
    updatePreference.mutate(
      { type, channel, enabled, minSeverity: floor },
      {
        onSuccess: () => toast.push({ tone: 'success', message: 'Notification preference saved' }),
        onError: (cause) =>
          toast.push({
            tone: 'error',
            message: cause instanceof Error ? cause.message : 'Could not save the preference.',
          }),
      },
    )
  }

  return (
    <div className="flex flex-col gap-6">
      <header>
        <h1 className="text-2xl font-semibold text-text">Notification preferences</h1>
        <p className="mt-1 text-sm text-muted">
          Choose which channels each notification type uses and the minimum severity.
        </p>
      </header>

      {preferences.isLoading && (
        <div className="flex justify-center py-8">
          <LoadingSpinner label="Loading preferences" />
        </div>
      )}
      {preferences.isError && (
        <p role="alert" className="text-sm text-danger">
          Could not load notification preferences.
        </p>
      )}
      {!preferences.isLoading && !preferences.isError && (
        <section className="grid gap-4 xl:grid-cols-2" aria-label="Channels by type">
          {NOTIFICATION_TYPES.map((type) => {
            const inApp = rowFor(type, 'IN_APP')
            const email = rowFor(type, 'EMAIL')
            return (
              <Card key={type}>
                <CardHeader title={NOTIFICATION_TYPE_LABELS[type]} />
                <CardBody className="flex flex-col gap-4">
                  {inApp ? (
                    <ChannelRow
                      preference={inApp}
                      updating={updatePreference.isPending}
                      onUpdate={onUpdate(type, 'IN_APP')}
                    />
                  ) : (
                    <p className="text-sm text-subtle">No in-app preference.</p>
                  )}
                  {email ? (
                    <ChannelRow
                      preference={email}
                      updating={updatePreference.isPending}
                      onUpdate={onUpdate(type, 'EMAIL')}
                    />
                  ) : (
                    <p className="text-sm text-subtle">No email preference.</p>
                  )}
                </CardBody>
              </Card>
            )
          })}
        </section>
      )}
    </div>
  )
}