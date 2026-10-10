import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { resetPasswordSchema, type ResetPasswordFormValues } from '../schemas'
import { useResetPassword } from '../hooks'
import { policyMessage } from '../errors'
import { ApiError } from '@/services/http/ApiError'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { paths } from '@/routes/paths'

function failureMessage(error: unknown): string {
  const policy = policyMessage(error)
  if (policy) return policy
  if (error instanceof ApiError && (error.isValidation || error.isNotFound)) {
    return 'That reset link is invalid or has expired. Request a new one.'
  }
  return 'Something went wrong. Please try again.'
}

export function ResetPasswordPage() {
  const [params] = useSearchParams()
  const token = params.get('token') ?? ''
  const reset = useResetPassword()
  const navigate = useNavigate()

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ResetPasswordFormValues>({
    resolver: zodResolver(resetPasswordSchema),
    defaultValues: { token, password: '', confirmPassword: '' },
  })

  const onSubmit = handleSubmit((values) => {
    reset.mutate(
      { token: values.token, password: values.password },
      { onSuccess: () => navigate(paths.login, { replace: true }) },
    )
  })

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
      <div>
        <h1 className="text-xl font-semibold text-text">Choose a new password</h1>
        <p className="mt-1 text-sm text-muted">The link from your email is used once.</p>
      </div>

      {!token && (
        <p
          role="alert"
          className="rounded-md border border-warning/30 bg-warning-subtle px-3 py-2 text-sm text-warning"
        >
          This page needs a reset token. Request a new link from the forgot-password page.
        </p>
      )}

      {reset.isError && (
        <p
          role="alert"
          className="rounded-md border border-danger/30 bg-danger-subtle px-3 py-2 text-sm text-danger"
        >
          {failureMessage(reset.error)}
        </p>
      )}

      <Input type="hidden" {...register('token')} />
      <Input
        label="New password"
        type="password"
        autoComplete="new-password"
        error={errors.password?.message}
        {...register('password')}
      />
      <Input
        label="Confirm password"
        type="password"
        autoComplete="new-password"
        error={errors.confirmPassword?.message}
        {...register('confirmPassword')}
      />

      <Button type="submit" loading={reset.isPending} className="w-full" disabled={!token}>
        Update password
      </Button>

      <Link to={paths.login} className="text-center text-sm text-primary hover:underline">
        Back to sign in
      </Link>
    </form>
  )
}
