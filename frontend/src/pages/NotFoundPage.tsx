import { Link } from 'react-router-dom'
import { paths } from '@/routes/paths'

export function NotFoundPage() {
  return (
    <div className="flex min-h-[60vh] flex-col items-center justify-center text-center">
      <p className="text-sm font-medium text-primary">404</p>
      <h1 className="mt-2 text-2xl font-semibold text-text">Page not found</h1>
      <p className="mt-2 max-w-sm text-sm text-muted">
        The page you were looking for does not exist or may have moved.
      </p>
      <Link
        to={paths.dashboard}
        className="mt-6 inline-flex h-10 items-center rounded-md bg-primary px-4 text-sm font-medium text-white hover:bg-primary-hover"
      >
        Back to dashboard
      </Link>
    </div>
  )
}
