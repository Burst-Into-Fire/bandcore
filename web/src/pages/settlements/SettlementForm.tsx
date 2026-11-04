import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Controller, useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import type { z } from 'zod';

import { api } from '../../api/client';
import { applyProblem } from '../../api/forms';
import { unwrap } from '../../api/problem';
import { settlementSchema } from '../../auth/schemas';
import { primaryButton, secondaryButton } from '../../components/buttons';
import { FormAlert } from '../../components/FormAlert';
import { MoneyInput } from '../../components/MoneyInput';
import { SelectField } from '../../components/SelectField';
import { TextField } from '../../components/TextField';
import { translateKey } from '../../i18n/messages';
import { CURRENCIES, type Currency } from '../../lib/money';
import { memberName } from '../expenses/splitState';
import type { Member } from '../expenses/types';
import { MONEY_QUERIES, type Settlement } from './types';

type SettlementInput = z.input<typeof settlementSchema>;
type SettlementValues = z.output<typeof settlementSchema>;

const AMOUNT = 'amountCents';

const FIELDS = ['fromMemberId', 'toMemberId', 'currency', 'amountCents', 'paidOn', 'note'] as const;

/** Records that one member paid another, or edits a settlement when {@code settlement} is given. */
export function SettlementForm({
  settlement,
  members,
  defaultCurrency,
  ownMemberId,
  today,
  onDone,
}: {
  settlement?: Settlement;
  members: readonly Member[];
  defaultCurrency: Currency;
  ownMemberId: string | undefined;
  today: string;
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const form = useForm<SettlementInput, unknown, SettlementValues>({
    resolver: zodResolver(settlementSchema),
    defaultValues: {
      fromMemberId: settlement?.fromMemberId ?? ownMemberId ?? '',
      toMemberId: settlement?.toMemberId ?? '',
      currency: settlement?.currency ?? defaultCurrency,
      amountCents: settlement?.amountCents ?? 0,
      paidOn: settlement?.paidOn ?? today,
      note: settlement?.note ?? '',
    },
  });
  const save = useMutation({
    mutationFn: async (values: SettlementValues) =>
      settlement
        ? unwrap(
            await api.PATCH('/settlements/{settlementId}', {
              params: { path: { settlementId: settlement.id } },
              body: { ...values, version: settlement.version },
            }),
          )
        : unwrap(await api.POST('/settlements', { body: values })),
    onSuccess: async () => {
      await Promise.all(
        MONEY_QUERIES.map((queryKey) => queryClient.invalidateQueries({ queryKey: [...queryKey] })),
      );
      onDone();
    },
    onError: (error) => applyProblem(error, form.setError, FIELDS),
  });
  const { errors } = form.formState;
  // Members who left can still settle what they owe.
  const options = [
    { value: '', label: t('settlements.choose') },
    ...members.map((member) => ({ value: member.id, label: memberName(t, member) })),
  ];

  return (
    <form
      noValidate
      aria-label={settlement ? t('settlements.editTitle') : t('settlements.newTitle')}
      className="bg-surface-raised flex flex-col gap-3 rounded-lg p-3"
      onSubmit={form.handleSubmit((values) => save.mutate(values))}
    >
      <FormAlert message={translateKey(t, errors.root?.server?.message)} />
      <div className="grid gap-3 sm:grid-cols-2">
        <SelectField
          label={t('settlements.from')}
          options={options}
          error={translateKey(t, errors.fromMemberId?.message)}
          {...form.register('fromMemberId')}
        />
        <SelectField
          label={t('settlements.to')}
          options={options}
          error={translateKey(t, errors.toMemberId?.message)}
          {...form.register('toMemberId')}
        />
      </div>
      <div className="grid grid-cols-[1fr_auto] gap-3">
        <div className="flex flex-col gap-1">
          <Controller
            control={form.control}
            name={AMOUNT}
            render={({ field }) => (
              <MoneyInput
                label={t('settlements.amount')}
                value={field.value}
                onChange={field.onChange}
                onBlur={field.onBlur}
                aria-invalid={errors.amountCents ? true : undefined}
              />
            )}
          />
          {errors.amountCents ? (
            <p className="text-caption text-warning">
              {translateKey(t, errors.amountCents.message)}
            </p>
          ) : null}
        </div>
        <SelectField
          label={t('settlements.currency')}
          options={CURRENCIES.map((code) => ({ value: code, label: code }))}
          {...form.register('currency')}
        />
      </div>
      <div className="grid gap-3 sm:grid-cols-2">
        <TextField
          type="date"
          label={t('settlements.date')}
          error={translateKey(t, errors.paidOn?.message)}
          {...form.register('paidOn')}
        />
        <TextField
          label={t('settlements.note')}
          maxLength={500}
          error={translateKey(t, errors.note?.message)}
          {...form.register('note')}
        />
      </div>
      <div className="flex flex-wrap gap-2">
        <button type="submit" className={primaryButton} disabled={save.isPending}>
          {save.isPending ? t('settlements.saving') : t('settlements.save')}
        </button>
        <button type="button" className={secondaryButton} onClick={onDone}>
          {t('settlements.cancel')}
        </button>
      </div>
    </form>
  );
}
