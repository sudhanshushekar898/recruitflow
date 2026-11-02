import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { z } from 'zod'
import { Alert, Button, TextField } from '../../components/ui'
import { ApiError } from '../../lib/errors'
import { AuthLayout } from './AuthLayout'
import { useAuth } from './useAuth'

const schema = z.object({
  email: z.string().min(1, 'Enter your email').email('Enter a valid email'),
  password: z.string().min(1, 'Enter your password'),
})
type Values = z.infer<typeof schema>

export function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [formError, setFormError] = useState<string | null>(null)
  const notice = (location.state as { notice?: string } | null)?.notice
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<Values>({ resolver: zodResolver(schema) })

  const onSubmit = handleSubmit(async ({ email, password }) => {
    setFormError(null)
    try {
      await login(email, password)
      const from = (location.state as { from?: string } | null)?.from
      navigate(from && from !== '/login' ? from : '/', { replace: true })
    } catch (error) {
      if (error instanceof ApiError && error.code === 'ACCOUNT_LOCKED') {
        setFormError('Too many failed attempts. Please wait a few minutes and try again.')
      } else if (error instanceof ApiError && error.status === 401) {
        setFormError('Invalid email or password.')
      } else {
        setFormError(error instanceof ApiError ? error.message : 'Something went wrong. Please try again.')
      }
    }
  })

  return (
    <AuthLayout title="Sign in to RecruitFlow">
      <form onSubmit={onSubmit} noValidate className="space-y-4">
        {notice && <Alert tone="success">{notice}</Alert>}
        {formError && <Alert>{formError}</Alert>}
        <TextField label="Email" type="email" autoComplete="username" error={errors.email?.message} {...register('email')} />
        <TextField
          label="Password"
          type="password"
          autoComplete="current-password"
          error={errors.password?.message}
          {...register('password')}
        />
        <Button type="submit" loading={isSubmitting} className="w-full">
          Sign in
        </Button>
        <p className="text-center text-sm">
          <Link to="/forgot-password" className="text-indigo-600 hover:underline">
            Forgot your password?
          </Link>
        </p>
      </form>
    </AuthLayout>
  )
}
