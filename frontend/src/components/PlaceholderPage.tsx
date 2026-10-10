import { Card, CardBody, CardHeader } from '@/components/ui/Card'

/** Temporary shell for routes whose backend module is not implemented yet. */
export function PlaceholderPage({ title, description }: { title: string; description: string }) {
  return (
    <div className="flex flex-col gap-6">
      <header>
        <h1 className="text-2xl font-semibold text-text">{title}</h1>
        <p className="mt-1 text-sm text-muted">{description}</p>
      </header>
      <Card>
        <CardHeader title="Coming soon" />
        <CardBody>
          <p className="text-sm text-muted">
            This screen is wired into the router, navigation and layout already — the feature itself
            lands with its backend module.
          </p>
        </CardBody>
      </Card>
    </div>
  )
}
