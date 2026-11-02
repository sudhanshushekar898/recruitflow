import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { z } from 'zod'
import { Alert, Button, TextField } from '../../components/ui'
import { api } from '../../lib/apiClient'
import { applyApiError, passwordSchema } from '../../lib/forms'
import { AuthLayout } from './AuthLayout'
import { useAuth } from './useAuth'

const HINT = 'At least 12 characters. A passphrase of several words works well.'

/** Shared by accept-invite and reset-password: both set a new password from an emailed one-time token. */
function SetPasswordForm({ endpoint, title, submitLabel }: { endpoint: string; title: string; submitLabel: string }) {
  const [params] = useSearchParams()
  const token = params.get('token') ?? ''
  const navigate = useNavigate()
  const [formError, setFormError] = useState<string | null>(null)
  const schema = z
    .object({ password: passwordSchema, confirm: z.string() })
    .refine((v) => v.password === v.confirm, { path: ['confirm'], message: 'Passwords do not match' })
  type Values = z.infer<typeof schema>
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<Values>({ resolver: zodResolver(schema) })

  if (!token) {
    return (
      <AuthLayout title={title}>
        <Alert>This link is incomplete. Open the link from your email again.</Alert>
      </AuthLayout>
    )
  }

  const onSubmit = handleSubmit(async ({ password }) => {
    setFormError(null)
    try {
      await api(endpoint, { method: 'POST', body: { token, password }, auth: false })
      navigate('/login', { replace: true, state: { notice: 'Password saved. You can sign in now.' } })
    } catch (error) {
      setFormError(applyApiError(error, setError))
    }
  })

  return (
    <AuthLayout title={title}>
      <form onSubmit={onSubmit} noValidate className="space-y-4">
        {formError && <Alert>{formError}</Alert>}
        <TextField
          label="New password"
          type="password"
          autoComplete="new-password"
          hint={HINT}
          error={errors.password?.message}
          {...register('password')}
        />
        <TextField
          label="Confirm password"
          type="password"
          autoComplete="new-password"
          error={errors.confirm?.message}
          {...register('confirm')}
        />
        <Button type="submit" loading={isSubmitting} className="w-full">
          {submitLabel}
        </Button>
      </form>
    </AuthLayout>
  )
}

export function AcceptInvitePage() {
  return <SetPasswordForm endpoint="/api/v1/auth/accept-invite" title="Welcome to RecruitFlow" submitLabel="Set password" />
}

export function ResetPasswordPage() {
  return <SetPasswordForm endpoint="/api/v1/auth/reset-password" title="Choose a new password" submitLabel="Reset password" />
}

const forgotSchema = z.object({ email: z.string().min(1, 'Enter your email').email('Enter a valid email') })

export function ForgotPasswordPage() {
  const [sent, setSent] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<z.infer<typeof forgotSchema>>({ resolver: zodResolver(forgotSchema) })

  const onSubmit = handleSubmit(async ({ email }) => {
    setFormError(null)
    try {
      await api('/api/v1/auth/forgot-password', { method: 'POST', body: { email }, auth: false })
      setSent(true)
    } catch (error) {
      setFormError(error instanceof Error ? error.message : 'Something went wrong.')
    }
  })

  return (
    <AuthLayout title="Reset your password">
      {sent ? (
        // Same message whether or not the address has an account, mirroring the API.
        <div className="space-y-4">
          <Alert tone="success">If an account exists for that email, a reset link is on its way.</Alert>
          <Link to="/login" className="block text-center text-sm text-indigo-600 hover:underline">
            Back to sign in
          </Link>
        </div>
      ) : (
        <form onSubmit={onSubmit} noValidate className="space-y-4">
          {formError && <Alert>{formError}</Alert>}
          <TextField label="Email" type="email" autoComplete="username" error={errors.email?.message} {...register('email')} />
          <Button type="submit" loading={isSubmitting} className="w-full">
            Send reset link
          </Button>
          <Link to="/login" className="block text-center text-sm text-indigo-600 hover:underline">
            Back to sign in
          </Link>
        </form>
      )}
    </AuthLayout>
  )
}

export function ChangePasswordPage() {
  const { user, changePassword, logout } = useAuth()
  const navigate = useNavigate()
  const [formError, setFormError] = useState<string | null>(null)
  const schema = z
    .object({ currentPassword: z.string().min(1, 'Enter your current password'), newPassword: passwordSchema, confirm: z.string() })
    .refine((v) => v.newPassword === v.confirm, { path: ['confirm'], message: 'Passwords do not match' })
  type Values = z.infer<typeof schema>
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<Values>({ resolver: zodResolver(schema) })

  const onSubmit = handleSubmit(async ({ currentPassword, newPassword }) => {
    setFormError(null)
    try {
      await changePassword(currentPassword, newPassword)
      navigate('/', { replace: true })
    } catch (error) {
      setFormError(applyApiError(error, setError))
    }
  })

  return (
    <AuthLayout title="Change your password">
      <form onSubmit={onSubmit} noValidate className="space-y-4">
        {user?.mustChangePassword && <Alert tone="info">You must choose a new password before continuing.</Alert>}
        {formError && <Alert>{formError}</Alert>}
        <TextField
          label="Current password"
          type="password"
          autoComplete="current-password"
          error={errors.currentPassword?.message}
          {...register('currentPassword')}
        />
        <TextField
          label="New password"
          type="password"
          autoComplete="new-password"
          hint={HINT}
          error={errors.newPassword?.message}
          {...register('newPassword')}
        />
        <TextField
          label="Confirm new password"
          type="password"
          autoComplete="new-password"
          error={errors.confirm?.message}
          {...register('confirm')}
        />
        <Button type="submit" loading={isSubmitting} className="w-full">
          Change password
        </Button>
        <Button type="button" variant="secondary" className="w-full" onClick={() => void logout()}>
          Sign out
        </Button>
      </form>
    </AuthLayout>
  )
}
