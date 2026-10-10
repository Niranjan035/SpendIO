import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { ApiError } from '@/services/http/ApiError'
import { useToast } from '@/app/providers/ToastProvider'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { LoadingSpinner } from '@/components/feedback/LoadingSpinner'
import { EMPTY_PROFILE_FORM, profileSchema, type ProfileFormValues } from '../schemas'
import { useProfile, useUpdateProfile } from '../hooks'
import type { UpdateProfileInput } from '../types'

function toPatch(values: ProfileFormValues): UpdateProfileInput {
  return {
    firstName: values.firstName.trim() || null,
    lastName: values.lastName.trim() || null,
    timezone: values.timezone.trim() || null,
    locale: values.locale.trim() || null,
  }
}

export function ProfileForm() {
  const { data: profile, isLoading, error } = useProfile()
  const update = useUpdateProfile()
  const toast = useToast()

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isDirty },
  } = useForm<ProfileFormValues>({
    resolver: zodResolver(profileSchema),
    defaultValues: EMPTY_PROFILE_FORM,
  })

  useEffect(() => {
    if (profile) {
      reset({
        firstName: profile.firstName ?? '',
        lastName: profile.lastName ?? '',
        timezone: profile.timezone ?? '',
        locale: profile.locale ?? '',
      })
    }
  }, [profile, reset])

  const onSubmit = handleSubmit((values) =>
    update.mutate(toPatch(values), {
      onSuccess: () => toast.push({ tone: 'success', message: 'Profile updated' }),
      onError: (cause) =>
        toast.push({
          tone: 'error',
          message: cause instanceof ApiError ? cause.message : 'Could not update the profile',
        }),
    }),
  )

  if (isLoading) return <LoadingSpinner label="Loading profile" />

  if (error || !profile) {
    return (
      <p role="alert" className="text-sm text-danger">
        {error instanceof ApiError ? error.message : 'Could not load your profile.'}
      </p>
    )
  }

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
      <div className="grid gap-4 sm:grid-cols-2">
        <Input
          label="First name"
          autoComplete="given-name"
          error={errors.firstName?.message}
          {...register('firstName')}
        />
        <Input
          label="Last name"
          autoComplete="family-name"
          error={errors.lastName?.message}
          {...register('lastName')}
        />
        <Input
          label="Timezone"
          hint="IANA id, e.g. Europe/Lisbon"
          error={errors.timezone?.message}
          {...register('timezone')}
        />
        <Input
          label="Locale"
          hint="Language-region, e.g. en-GB"
          error={errors.locale?.message}
          {...register('locale')}
        />
      </div>

      <dl className="grid gap-3 border-t border-border pt-4 text-sm sm:grid-cols-3">
        <div>
          <dt className="text-xs text-muted">Email</dt>
          <dd className="text-text">{profile.email}</dd>
        </div>
        <div>
          <dt className="text-xs text-muted">Verified</dt>
          <dd className="text-text">{profile.emailVerified ? 'Yes' : 'Not yet'}</dd>
        </div>
        <div>
          <dt className="text-xs text-muted">Member since</dt>
          <dd className="text-text">{profile.createdAt.slice(0, 10)}</dd>
        </div>
      </dl>

      <div className="flex justify-end">
        <Button type="submit" loading={update.isPending} disabled={!isDirty}>
          Save profile
        </Button>
      </div>
    </form>
  )
}
