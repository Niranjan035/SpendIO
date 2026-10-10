import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import type { ImportReportFailure } from '../types'

export function ErrorReportTable({ failures }: { failures: ImportReportFailure[] }) {
  return (
    <Card>
      <CardHeader title="Rejected rows" description={`${failures.length} rows could not be imported.`} />
      <CardBody>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-border text-left text-xs uppercase text-subtle">
                <th scope="col" className="py-2 pr-4 font-medium">
                  Row
                </th>
                <th scope="col" className="py-2 font-medium">
                  Reason
                </th>
              </tr>
            </thead>
            <tbody>
              {failures.map((failure) => (
                <tr key={`${failure.rowNumber}-${failure.reason}`} className="border-b border-border/60">
                  <td className="py-2 pr-4 text-muted">{failure.rowNumber}</td>
                  <td className="py-2 text-text">{failure.reason}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </CardBody>
    </Card>
  )
}
