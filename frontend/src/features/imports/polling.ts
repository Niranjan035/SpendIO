import type { ImportStatus } from '@/types/enums'

/**
 * Statuses the server moves through on its own: the detail view polls these and
 * stops as soon as the job settles into a status that waits for the user
 * (MAPPING_REQUIRED / READY) or a terminal one.
 */
const AUTO_MOVING: readonly ImportStatus[] = ['UPLOADED', 'PARSING', 'VALIDATING', 'PROCESSING']

/** 2s while work is running; `false` stops the interval when the job settles. */
export const IMPORT_POLL_INTERVAL_MS = 2000

export function isAutoMoving(status: ImportStatus | undefined): boolean {
  return status !== undefined && AUTO_MOVING.includes(status)
}

export function importPollInterval(status: ImportStatus | undefined): number | false {
  return isAutoMoving(status) ? IMPORT_POLL_INTERVAL_MS : false
}
