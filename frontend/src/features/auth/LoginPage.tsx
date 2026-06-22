import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Link, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { ApiError } from '@/api'
import { Button, Field, Input } from '@/components/primitives'
import { useAuth } from './AuthProvider'
import { AuthLayout } from './AuthLayout'

const schema = z.object({
  email: z.string().min(1).email(),
  password: z.string().min(1),
})
type FormValues = z.infer<typeof schema>

export function LoginPage() {
  const { t } = useTranslation()
  const { login } = useAuth()
  const navigate = useNavigate()
  const [formError, setFormError] = useState<string | null>(null)
  const { register, handleSubmit, formState } = useForm<FormValues>({ resolver: zodResolver(schema) })

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null)
    try {
      await login(values.email, values.password)
      navigate('/', { replace: true })
    } catch (err) {
      setFormError(err instanceof ApiError ? err.detail || err.title : t('errors.generic'))
    }
  })

  return (
    <AuthLayout
      title={t('auth.welcome')}
      footer={
        <>
          {t('auth.noAccount')}{' '}
          <Link to="/register" className="font-medium text-brand-600 hover:text-brand-700">
            {t('auth.registerCta')}
          </Link>
        </>
      }
    >
      <form onSubmit={onSubmit} className="space-y-4" noValidate>
        <Field label={t('auth.email')} htmlFor="email" error={formState.errors.email && t('errors.required')}>
          <Input id="email" type="email" autoComplete="email" {...register('email')} />
        </Field>
        <Field label={t('auth.password')} htmlFor="password" error={formState.errors.password && t('errors.required')}>
          <Input id="password" type="password" autoComplete="current-password" {...register('password')} />
        </Field>
        {formError && <p className="text-sm font-medium text-negative">{formError}</p>}
        <Button type="submit" className="w-full" loading={formState.isSubmitting}>
          {t('auth.loginCta')}
        </Button>
      </form>
    </AuthLayout>
  )
}
