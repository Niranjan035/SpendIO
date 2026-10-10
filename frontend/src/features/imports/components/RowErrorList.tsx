export function RowErrorList({ errors, className }: { errors: string[]; className?: string }) {
  if (errors.length === 0) return null
  return (
    <ul className={className ?? 'list-disc space-y-0.5 pl-4 text-xs text-danger'}>
      {errors.map((error) => (
        <li key={error}>{error}</li>
      ))}
    </ul>
  )
}
