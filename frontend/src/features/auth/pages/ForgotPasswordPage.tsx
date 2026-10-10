import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Link } from 'react-router-dom'
import { forgotPasswordSchema, type ForgotPasswordFormValues } from '../schemas'
import { useForgotPassword } from '../hooks'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { paths } from '@/routes/paths'

export function ForgotPasswordPage() {
  const forgot = useForgotPassword()

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ForgotPasswordFormValues>({
    resolver: zodResolver(forgotPasswordSchema),
    defaultValues: { email: '' },
  })

  const onSubmit = handleSubmit((values) => forgot.mutate(values.email))

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
      <div>
        <h1 className="text-xl font-semibold text-text">Reset your password</h1>
        <p className="mt-1 text-sm text-muted">
          Enter your email and we&apos;ll send a reset link if an account exists.
        </p>
      </div>

      {forgot.isSuccess && (
        <p
          role="status"
          className="rounded-md border border-success/30 bg-success-subtle px-3 py-2 text-sm text-success"
        >
          If an account exists for that address, a reset link is on its way.
        </p>
      )}

      <Input
        label="Email"
        type="email"
        autoComplete="email"
        error={errors.email?.message}
        {...register('email')}
      />

      <Button type="submit" loading={forgot.isPending} className="w-full">
        Send reset link
      </Button>

      <div className="flex items-center justify-between text-sm">
        <Link to={paths.login} className="text-primary hover:underline">
          Back to sign in
        </Link>
        <Link to={paths.register} className="text-primary hover:underline">
          Create account
        </Link>
      </div>
    </form>
  )
}
