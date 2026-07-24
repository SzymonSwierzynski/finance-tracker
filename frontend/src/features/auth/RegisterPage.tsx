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
  password: z.string().min(8, 'min8'),
  displayName: z.string().max(100).optional(),
})
type FormValues = z.infer<typeof schema>

export function RegisterPage() {
  const { t } = useTranslation()
  const { register: registerUser } = useAuth()
  const navigate = useNavigate()
  const [formError, setFormError] = useState<string | null>(null)
  const { register, handleSubmit, formState } = useForm<FormValues>({ resolver: zodResolver(schema) })

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null)
    try {
      await registerUser(values.email, values.password, values.displayName || undefined)
      navigate('/', { replace: true })
    } catch (err) {
      setFormError(err instanceof ApiError ? err.detail || err.title : t('errors.generic'))
    }
  })

  return (
    <AuthLayout
      title={t('auth.startNow')}
      footer={
        <>
          {t('auth.haveAccount')}{' '}
          <Link to="/login" className="font-medium text-accent hover:text-accent">
            {t('auth.loginCta')}
          </Link>
        </>
      }
    >
      <form onSubmit={onSubmit} className="space-y-4" noValidate>
        <Field label={t('auth.email')} htmlFor="email" error={formState.errors.email && t('errors.required')}>
          <Input id="email" type="email" autoComplete="email" {...register('email')} />
        </Field>
        <Field
          label={t('auth.password')}
          htmlFor="password"
          error={formState.errors.password ? 'min. 8' : undefined}
        >
          <Input id="password" type="password" autoComplete="new-password" {...register('password')} />
        </Field>
        <Field label={`${t('auth.displayName')} (${t('common.optional')})`} htmlFor="displayName">
          <Input id="displayName" type="text" autoComplete="name" {...register('displayName')} />
        </Field>
        {formError && <p className="text-sm font-medium text-negative">{formError}</p>}
        <Button type="submit" className="w-full" loading={formState.isSubmitting}>
          {t('auth.registerCta')}
        </Button>
      </form>
    </AuthLayout>
  )
}
