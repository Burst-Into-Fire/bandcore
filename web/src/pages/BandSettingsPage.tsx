import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo } from 'react';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import type { z } from 'zod';

import { api } from '../api/client';
import { applyProblem } from '../api/forms';
import { unwrap } from '../api/problem';
import { bandSettingsSchema } from '../auth/schemas';
import { useBandAccess } from '../auth/useBandAccess';
import { primaryButton } from '../components/buttons';
import { FormAlert } from '../components/FormAlert';
import { PageHeader } from '../components/PageHeader';
import { SelectField } from '../components/SelectField';
import { TextField } from '../components/TextField';
import { translateKey } from '../i18n/messages';
import { bandHostName, useHost } from '../lib/host';
import { CURRENCIES, type Currency } from '../lib/money';
import { timeZoneOptions } from '../lib/timezones';
import { errorMessage } from './errorMessage';
import { CategoriesCard } from './settings/CategoriesCard';

type SettingsInput = z.input<typeof bandSettingsSchema>;
type SettingsValues = z.output<typeof bandSettingsSchema>;

/** Band settings (admins): name, time zone and default currency. The address is fixed in v1. */
export function BandSettingsPage() {
  const { t } = useTranslation();
  return (
    <div className="flex flex-col gap-6">
      <PageHeader title={t('settings.title')} subtitle={t('settings.subtitle')} />
      <SettingsContent />
    </div>
  );
}

function SettingsContent() {
  const { t } = useTranslation();
  const { can, me } = useBandAccess();
  const band = useQuery({
    queryKey: ['band'],
    queryFn: async () => unwrap(await api.GET('/band')),
  });

  if (me.isPending || band.isPending) {
    return (
      <p role="status" className="text-muted">
        {t('common.loading')}
      </p>
    );
  }
  if (band.isError || me.isError) {
    return <FormAlert message={errorMessage(t, band.error ?? me.error)} />;
  }
  if (!can('band:manage')) {
    return <p className="text-muted">{t('settings.adminsOnly')}</p>;
  }
  return (
    <>
      <SettingsForm band={band.data} />
      <CategoriesCard />
    </>
  );
}

function SettingsForm({
  band,
}: {
  band: { slug: string; name: string; timezone: string; currency: Currency };
}) {
  const { t } = useTranslation();
  const host = useHost();
  const queryClient = useQueryClient();
  const zones = useMemo(() => timeZoneOptions(band.timezone), [band.timezone]);
  const form = useForm<SettingsInput, unknown, SettingsValues>({
    resolver: zodResolver(bandSettingsSchema),
    defaultValues: { name: band.name, timezone: band.timezone, currency: band.currency },
  });
  const save = useMutation({
    mutationFn: async (values: SettingsValues) =>
      unwrap(await api.PATCH('/band', { body: values })),
    onSuccess: (saved) => {
      queryClient.setQueryData(['band'], saved);
      form.reset({ name: saved.name, timezone: saved.timezone, currency: saved.currency });
    },
    onError: (error) => applyProblem(error, form.setError, ['name', 'timezone', 'currency']),
  });
  const { errors, isDirty } = form.formState;

  return (
    <form
      noValidate
      className="flex max-w-xl flex-col gap-4"
      onSubmit={form.handleSubmit((values) => save.mutate(values))}
    >
      <FormAlert message={translateKey(t, errors.root?.server?.message)} />
      <TextField
        label={t('fields.bandName')}
        error={translateKey(t, errors.name?.message)}
        {...form.register('name')}
      />
      <SelectField
        label={t('fields.timezone')}
        hint={t('settings.timezoneHint')}
        options={zones}
        error={translateKey(t, errors.timezone?.message)}
        {...form.register('timezone')}
      />
      <SelectField
        label={t('settings.currency')}
        hint={t('settings.currencyHint')}
        options={CURRENCIES.map((currency) => ({
          value: currency,
          label: t(`currencies.${currency}`),
        }))}
        error={translateKey(t, errors.currency?.message)}
        {...form.register('currency')}
      />
      <dl className="grid gap-3">
        <div className="flex flex-col gap-1">
          <dt className="text-card-title text-muted">{t('fields.slug')}</dt>
          <dd>{bandHostName(band.slug, host.baseDomain)}</dd>
          <dd className="text-caption text-muted">{t('settings.addressFixed')}</dd>
        </div>
      </dl>
      <div className="flex items-center gap-3">
        <button type="submit" className={primaryButton} disabled={save.isPending || !isDirty}>
          {save.isPending ? t('settings.saving') : t('settings.save')}
        </button>
        {save.isSuccess && !isDirty ? (
          <p role="status" className="text-muted">
            {t('settings.saved')}
          </p>
        ) : null}
      </div>
    </form>
  );
}
