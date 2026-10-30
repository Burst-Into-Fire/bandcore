import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import type { z } from 'zod';

import { api } from '../api/client';
import { applyProblem } from '../api/forms';
import { unwrap } from '../api/problem';
import { createBandSchema } from '../auth/schemas';
import { AuthLayout } from '../components/AuthLayout';
import { primaryButton, textLink } from '../components/buttons';
import { FormAlert } from '../components/FormAlert';
import { TextField } from '../components/TextField';
import { errorKey, translateKey } from '../i18n/messages';
import { bandHostName, useHost } from '../lib/host';
import { browser } from '../lib/navigation';
import { isWellFormedSlug, slugify } from '../lib/slug';
import { useDebouncedValue } from '../lib/useDebouncedValue';

type CreateBandInput = z.input<typeof createBandSchema>;
type CreateBandValues = z.output<typeof createBandSchema>;

/** Band creation on the central host; on success the browser goes to the new band's login page. */
export function CreateBandPage() {
  const { t } = useTranslation();
  const host = useHost();
  const [slugEdited, setSlugEdited] = useState(false);
  const form = useForm<CreateBandInput, unknown, CreateBandValues>({
    resolver: zodResolver(createBandSchema),
    defaultValues: { name: '', slug: '' },
  });
  const slug = useWatch({ control: form.control, name: 'slug' }).trim().toLowerCase();
  const checkedSlug = useDebouncedValue(slug, 300);
  const availability = useQuery({
    queryKey: ['slug-availability', checkedSlug],
    queryFn: async () =>
      unwrap(
        await api.GET('/bands/slug-availability', { params: { query: { slug: checkedSlug } } }),
      ),
    enabled: isWellFormedSlug(checkedSlug),
    staleTime: 30_000,
  });
  const create = useMutation({
    mutationFn: async (values: CreateBandValues) =>
      unwrap(await api.POST('/bands', { body: values })),
    onSuccess: (band) => browser.assign(`${band.url}/login`),
    onError: (error) => applyProblem(error, form.setError, ['name', 'slug']),
  });
  const { errors } = form.formState;

  const slugStatus = (() => {
    if (!isWellFormedSlug(slug) || slug !== checkedSlug) {
      return undefined;
    }
    if (availability.isPending) {
      return t('createBand.checking');
    }
    if (availability.data?.available) {
      return t('createBand.available');
    }
    return undefined;
  })();
  const unavailable =
    availability.data && !availability.data.available && availability.data.reason
      ? errorKey(availability.data.reason)
      : undefined;

  return (
    <AuthLayout title={t('createBand.title')} subtitle={t('createBand.subtitle')}>
      <form
        noValidate
        className="flex flex-col gap-4"
        onSubmit={form.handleSubmit((values) => create.mutate(values))}
      >
        <FormAlert message={translateKey(t, errors.root?.server?.message)} />
        <TextField
          label={t('fields.bandName')}
          autoComplete="off"
          error={translateKey(t, errors.name?.message)}
          {...form.register('name', {
            onChange: (event: { target: { value: string } }) => {
              if (!slugEdited) {
                form.setValue('slug', slugify(event.target.value));
              }
            },
          })}
        />
        <TextField
          label={t('fields.slug')}
          autoComplete="off"
          autoCapitalize="none"
          spellCheck={false}
          hint={t('createBand.addressPreview', {
            host: bandHostName(slug || '…', host.baseDomain),
          })}
          error={translateKey(t, errors.slug?.message ?? unavailable)}
          {...form.register('slug', { onChange: () => setSlugEdited(true) })}
        />
        {slugStatus ? (
          <p role="status" className="text-caption text-success">
            {slugStatus}
          </p>
        ) : null}
        <button type="submit" className={primaryButton} disabled={create.isPending}>
          {create.isPending ? t('createBand.submitting') : t('createBand.submit')}
        </button>
      </form>
      <Link to="/bands" className={textLink}>
        {t('common.back')}
      </Link>
    </AuthLayout>
  );
}
