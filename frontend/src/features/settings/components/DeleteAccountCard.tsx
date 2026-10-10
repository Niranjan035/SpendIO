import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { ApiError } from '@/services/http/ApiError'
import { useToast } from '@/app/providers/ToastProvider'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { Modal } from '@/components/ui/Modal'
import { deleteAccountSchema, type DeleteAccountFormValues } from '../schemas'
import { useDeleteAccount } from '../hooks'

/**
 * DELETE /users/me (ARCHITECTURE.md D.3, E.3, Phase 14). A destructive action gated
 * behind a modal, the account password and a typed confirmation phrase.
 */
export function DeleteAccountCard() {
  const [open, setOpen] = useState(false)
  const remove = useDeleteAccount()
  const toast = useToast()

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<DeleteAccountFormValues>({
    resolver: zodResolver(deleteAccountSchema),
    defaultValues: { password: '', confirmationText: '' },
  })

  const close = () => {
    reset()
    setOpen(false)
  }

  const onSubmit = handleSubmit((values) =>
    remove.mutate(values, {
      onError: (cause) =>
        toast.push({
          tone: 'error',
          message: cause instanceof ApiError ? cause.message : 'Could not delete the account',
        }),
    }),
  )

  return (
    <div className="flex flex-col gap-3">
      <p className="text-sm text-muted">
        Permanently deletes your account and personal data. Financial records are anonymised and
        kept for a short grace period before being purged. This cannot be undone.
      </p>
      <div className="flex justify-end">
        <Button variant="danger" onClick={() => setOpen(true)}>
          Delete account
        </Button>
      </div>

      <Modal open={open} title="Delete your account" onClose={close}>
        <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
          <p className="text-sm text-muted">
            This anonymises your profile and revokes every session. Type <strong>DELETE</strong> and
            confirm your password to continue.
          </p>
          <Input
            label="Password"
            type="password"
            autoComplete="current-password"
            error={errors.password?.message}
            {...register('password')}
          />
          <Input
            label="Type DELETE to confirm"
            autoComplete="off"
            error={errors.confirmationText?.message}
            {...register('confirmationText')}
          />
          <div className="flex justify-end gap-2">
            <Button type="button" variant="ghost" onClick={close}>
              Cancel
            </Button>
            <Button type="submit" variant="danger" loading={remove.isPending}>
              Delete account
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  )
}
