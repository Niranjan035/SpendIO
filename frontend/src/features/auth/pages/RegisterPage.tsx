import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Link, useNavigate } from 'react-router-dom'
import { browserDefaults, registerSchema, type RegisterFormValues } from '../schemas'
import { useRegister } from '../hooks'
import { policyMessage } from '../errors'
import { ApiError } from '@/services/http/ApiError'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { paths } from '@/routes/paths'

const defaults = browserDefaults()

export function RegisterPage() {
  const registerMutation = useRegister()
  const navigate = useNavigate()

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<RegisterFormValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: {
      email: '',
      password: '',
      firstName: '',
      lastName: '',
      timezone: defaults.timezone,
      locale: defaults.locale,
      baseCurrencyCode: 'USD',
    },
  })

  const onSubmit = handleSubmit((values) => {
    registerMutation.mutate(values, {
      onSuccess: () => navigate(paths.dashboard, { replace: true }),
      onError: (error) => {
        if (error instanceof ApiError && error.isValidation && error.fieldErrors.length > 0) {
          for (const field of error.fieldErrors) {
            setError(field.field as keyof RegisterFormValues, { message: field.message })
          }
          return
        }
        const policy = policyMessage(error)
        setError('root', {
          message:
            policy ??
            (error instanceof ApiError && error.isConflict
              ? 'An account with that email already exists.'
              : error instanceof ApiError && error.isValidation
                ? error.message
                : 'Could not create your account. Please try again.'),
        })
      },
    })
  })

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
      <div>
        <h1 className="text-xl font-semibold text-text">Create your account</h1>
        <p className="mt-1 text-sm text-muted">It takes less than a minute.</p>
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
        label="Email"
        type="email"
        autoComplete="email"
        error={errors.email?.message}
        {...register('email')}
      />
      <Input
        label="Password"
        type="password"
        autoComplete="new-password"
        hint="At least 10 characters, with upper case, lower case and a digit."
        error={errors.password?.message}
        {...register('password')}
      />

      <Button type="submit" loading={registerMutation.isPending} className="w-full">
        Create account
      </Button>

      <p className="text-sm text-muted">
        Already have an account?{' '}
        <Link to={paths.login} className="text-primary hover:underline">
          Sign in
        </Link>
      </p>
    </form>
  )
}
