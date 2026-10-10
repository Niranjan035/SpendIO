import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { loginSchema, type LoginFormValues } from '../schemas'
import { useLogin } from '../hooks'
import { ApiError } from '@/services/http/ApiError'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { paths } from '@/routes/paths'

export function LoginForm() {
  const login = useLogin()
  const navigate = useNavigate()
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from ?? paths.dashboard

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: '', password: '' },
  })

  const onSubmit = handleSubmit((values) => {
    login.mutate(values, {
      onSuccess: () => navigate(from, { replace: true }),
      onError: (error) => {
        if (error instanceof ApiError && error.isValidation && error.fieldErrors.length > 0) {
          for (const field of error.fieldErrors) {
            setError(field.field as keyof LoginFormValues, { message: field.message })
          }
          return
        }
        setError('root', {
          message:
            error instanceof ApiError && error.status === 401
              ? 'Email or password is incorrect.'
              : error instanceof ApiError && error.isRateLimited
                ? 'Too many attempts. Please wait a moment and try again.'
                : 'Sign in failed. Please try again.',
        })
      },
    })
  })

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
      <div>
        <h1 className="text-xl font-semibold text-text">Sign in</h1>
        <p className="mt-1 text-sm text-muted">Welcome back to SpendIO.</p>
      </div>

      {errors.root && (
        <p
          role="alert"
          className="rounded-md border border-danger/30 bg-danger-subtle px-3 py-2 text-sm text-danger"
        >
          {errors.root.message}
        </p>
      )}

      <Input
        label="Email"
        type="email"
        autoComplete="email"
        placeholder="you@example.com"
        error={errors.email?.message}
        {...register('email')}
      />
      <Input
        label="Password"
        type="password"
        autoComplete="current-password"
        error={errors.password?.message}
        {...register('password')}
      />

      <Button type="submit" loading={login.isPending} className="w-full">
        Sign in
      </Button>

      <div className="flex items-center justify-between text-sm">
        <Link to={paths.forgotPassword} className="text-primary hover:underline">
          Forgot password?
        </Link>
        <Link to={paths.register} className="text-primary hover:underline">
          Create account
        </Link>
      </div>
    </form>
  )
}
