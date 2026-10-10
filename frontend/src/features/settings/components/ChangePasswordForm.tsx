import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { ApiError } from '@/services/http/ApiError'
import { useToast } from '@/app/providers/ToastProvider'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { changePasswordSchema, type ChangePasswordFormValues } from '../schemas'
import { useChangePassword } from '../hooks'

/** POST /auth/change-password (ARCHITECTURE.md D.2). */
export function ChangePasswordForm() {
  const change = useChangePassword()
  const toast = useToast()

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<ChangePasswordFormValues>({
    resolver: zodResolver(changePasswordSchema),
    defaultValues: { currentPassword: '', newPassword: '', confirmPassword: '' },
  })

  const onSubmit = handleSubmit((values) =>
    change.mutate(
      { currentPassword: values.currentPassword, newPassword: values.newPassword },
      {
        onSuccess: () => {
          toast.push({ tone: 'success', message: 'Password changed' })
          reset()
        },
        onError: (cause) =>
          toast.push({
            tone: 'error',
            message: cause instanceof ApiError ? cause.message : 'Could not change the password',
          }),
      },
    ),
  )

  return (
    <form onSubmit={onSubmit} noValidate className="flex max-w-sm flex-col gap-4">
      <Input
        label="Current password"
        type="password"
        autoComplete="current-password"
        error={errors.currentPassword?.message}
        {...register('currentPassword')}
      />
      <Input
        label="New password"
        type="password"
        autoComplete="new-password"
        hint="At least 8 characters"
        error={errors.newPassword?.message}
        {...register('newPassword')}
      />
      <Input
        label="Confirm new password"
        type="password"
        autoComplete="new-password"
        error={errors.confirmPassword?.message}
        {...register('confirmPassword')}
      />
      <div className="flex justify-end">
        <Button type="submit" loading={change.isPending}>
          Change password
        </Button>
      </div>
    </form>
  )
}
