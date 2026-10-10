import { z } from 'zod'

/** The backend rejects uploads over 5 MB with 413 (ARCHITECTURE.md E.16). */
export const MAX_IMPORT_FILE_BYTES = 5 * 1024 * 1024

export const csvFileSchema = z
  .custom<File>((file) => file instanceof File, 'Choose a CSV file')
  .refine((file) => /\.csv$/i.test(file.name), 'Only .csv files are supported')
  .refine((file) => file.size > 0, 'The chosen file is empty')
  .refine(
    (file) => file.size <= MAX_IMPORT_FILE_BYTES,
    'CSV files must be 5 MB or smaller',
  )

export const uploadFormSchema = z.object({
  defaultCurrencyCode: z.string(),
  defaultDateFormat: z.string(),
  targetAccountId: z.string(),
})

export type UploadFormValues = z.infer<typeof uploadFormSchema>

export const EMPTY_UPLOAD_FORM: UploadFormValues = {
  defaultCurrencyCode: '',
  defaultDateFormat: '',
  targetAccountId: '',
}

/** Canonical mapping field -> the CSV header that feeds it (empty = not mapped). */
export const mappingAssignmentsSchema = z
  .record(z.string(), z.string())
  .superRefine((assignments, ctx) => {
    if (!assignments.date) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: 'Map a date column',
      })
    }
    if (!assignments.amount) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: 'Map an amount column',
      })
    }
  })

export type MappingAssignments = z.infer<typeof mappingAssignmentsSchema>

export const mappingOptionsSchema = z.object({
  defaultCurrencyCode: z.string(),
  defaultDateFormat: z.string(),
  targetAccountId: z.string(),
  dateFormat: z.string(),
  skipDuplicates: z.boolean(),
  allowPartialImport: z.boolean(),
  createMissingCategories: z.boolean(),
  defaultType: z.string(),
})

export type MappingOptionsValues = z.infer<typeof mappingOptionsSchema>

export const EMPTY_MAPPING_OPTIONS: MappingOptionsValues = {
  defaultCurrencyCode: '',
  defaultDateFormat: '',
  targetAccountId: '',
  dateFormat: '',
  skipDuplicates: true,
  allowPartialImport: false,
  createMissingCategories: false,
  defaultType: '',
}

/** Row-level fixes from the preview table (ARCHITECTURE.md D.12 step 7). */
export const editRowSchema = z.object({
  occurredOn: z.string().min(1, 'Date is required'),
  amount: z.string().min(1, 'Amount is required'),
  description: z.string(),
  type: z.string().min(1, 'Type is required'),
  accountId: z.string(),
  categoryId: z.string(),
})

export type EditRowValues = z.infer<typeof editRowSchema>
