import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useEffect, useId, useRef, useState } from 'react';
import { Controller, useForm, useWatch } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import type { z } from 'zod';

import { api } from '../../api/client';
import { applyProblem } from '../../api/forms';
import { unwrap } from '../../api/problem';
import { expenseSchema } from '../../auth/schemas';
import { primaryButton, secondaryButton } from '../../components/buttons';
import { FormAlert } from '../../components/FormAlert';
import { MoneyInput } from '../../components/MoneyInput';
import { SelectField } from '../../components/SelectField';
import { TextField } from '../../components/TextField';
import { translateKey } from '../../i18n/messages';
import { CURRENCIES, type Currency } from '../../lib/money';
import { previewSplit, splitBody } from '../../lib/splits';
import { DESKTOP, useMediaQuery } from '../../lib/useMediaQuery';
import { SplitEditor } from './SplitEditor';
import { initialSplit, memberName, type SplitState } from './splitState';
import { categoryLabel, type Category, type Expense, type Member } from './types';

type ExpenseInput = z.input<typeof expenseSchema>;
type ExpenseValues = z.output<typeof expenseSchema>;

const FIELDS = [
  'description',
  'categoryId',
  'amountCents',
  'currency',
  'expenseDate',
  'paidByMemberId',
  'notes',
] as const;

const WATCHED = ['status', 'amountCents', 'currency'] as const;

const AMOUNT = 'amountCents';

const STATUSES = ['PAID', 'PLANNED'] as const;

/**
 * Records an expense, or edits one when {@code expense} is given: what, category, amount and
 * currency, date, whether it is already paid and by whom, and how it is split. Full screen on
 * phones; saving waits until the split adds up.
 */
export function ExpenseForm({
  expense,
  members,
  categories,
  defaultCurrency,
  today,
  ownMemberId,
  onDone,
}: {
  expense?: Expense;
  members: readonly Member[];
  categories: readonly Category[];
  defaultCurrency: Currency;
  /** Today in the band's time zone, "YYYY-MM-DD". */
  today: string;
  ownMemberId: string | undefined;
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const titleId = useId();
  const desktop = useMediaQuery(DESKTOP);
  const firstField = useRef<HTMLInputElement | null>(null);
  const queryClient = useQueryClient();
  const [split, setSplit] = useState<SplitState>(() => initialSplit(members, expense));
  const [splitError, setSplitError] = useState(false);

  const form = useForm<ExpenseInput, unknown, ExpenseValues>({
    resolver: zodResolver(expenseSchema),
    defaultValues: {
      description: expense?.description ?? '',
      categoryId: expense?.categoryId ?? '',
      amountCents: expense?.amountCents ?? 0,
      currency: expense?.currency ?? defaultCurrency,
      expenseDate: expense?.expenseDate ?? today,
      status: expense?.status ?? 'PAID',
      paidByMemberId: expense?.paidByMemberId ?? ownMemberId ?? '',
      notes: expense?.notes ?? '',
    },
  });
  const { errors } = form.formState;
  const [status, amountCents, currency] = useWatch({
    control: form.control,
    name: WATCHED,
  });
  const splitComplete = previewSplit(split.mode, amountCents, split.entries).complete;

  useEffect(() => {
    firstField.current?.focus();
  }, []);

  const save = useMutation({
    mutationFn: async (values: ExpenseValues) => {
      const body = {
        description: values.description,
        categoryId: values.categoryId,
        currency: values.currency,
        amountCents: values.amountCents,
        expenseDate: values.expenseDate,
        notes: values.notes,
        split: splitBody(split.mode, split.entries),
      };
      if (expense) {
        return unwrap(
          await api.PATCH('/expenses/{expenseId}', {
            params: { path: { expenseId: expense.id } },
            body: {
              ...body,
              version: expense.version,
              ...(expense.status === 'PAID' ? { paidByMemberId: values.paidByMemberId } : {}),
            },
          }),
        );
      }
      return unwrap(
        await api.POST('/expenses', {
          body: {
            ...body,
            status: values.status,
            ...(values.status === 'PAID' ? { paidByMemberId: values.paidByMemberId } : {}),
          },
        }),
      );
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['expenses'] });
      onDone();
    },
    onError: (error) => applyProblem(error, form.setError, FIELDS),
  });

  const submit = form.handleSubmit((values) => {
    if (!splitComplete) {
      setSplitError(true);
      return;
    }
    setSplitError(false);
    save.mutate(values);
  });

  const activeCategories = categories.filter(
    (category) => !category.archived || category.id === expense?.categoryId,
  );
  const payers = members.filter(
    (member) => member.status === 'ACTIVE' || member.id === expense?.paidByMemberId,
  );
  const { ref: descriptionRef, ...description } = form.register('description');

  return (
    <div
      role="dialog"
      aria-modal={desktop ? undefined : true}
      aria-labelledby={titleId}
      className={
        desktop
          ? 'rounded-card border-border bg-surface border p-4'
          : 'bg-background fixed inset-0 z-40 overflow-y-auto px-4 pt-4 pb-8'
      }
    >
      <form noValidate className="mx-auto flex max-w-2xl flex-col gap-4" onSubmit={submit}>
        <h2 id={titleId} className="text-title font-bold">
          {expense ? t('expenses.editTitle') : t('expenses.newTitle')}
        </h2>
        <FormAlert message={translateKey(t, errors.root?.server?.message)} />
        <TextField
          label={t('expenses.description')}
          placeholder={t('expenses.descriptionPlaceholder')}
          maxLength={120}
          error={translateKey(t, errors.description?.message)}
          ref={(element) => {
            descriptionRef(element);
            firstField.current = element;
          }}
          {...description}
        />
        <div className="grid gap-4 sm:grid-cols-2">
          <SelectField
            label={t('expenses.category')}
            options={[
              { value: '', label: t('expenses.choose') },
              ...activeCategories.map((category) => ({
                value: category.id,
                label: category.archived
                  ? t('expenses.archivedCategory', { name: categoryLabel(t, category) })
                  : categoryLabel(t, category),
              })),
            ]}
            error={translateKey(t, errors.categoryId?.message)}
            {...form.register('categoryId')}
          />
          <TextField
            type="date"
            label={t('expenses.date')}
            error={translateKey(t, errors.expenseDate?.message)}
            {...form.register('expenseDate')}
          />
        </div>
        <div className="grid grid-cols-[1fr_auto] gap-4">
          <div className="flex flex-col gap-1">
            <Controller
              control={form.control}
              name={AMOUNT}
              render={({ field }) => (
                <MoneyInput
                  label={t('expenses.amount')}
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
            label={t('expenses.currency')}
            options={CURRENCIES.map((code) => ({ value: code, label: code }))}
            {...form.register('currency')}
          />
        </div>
        {expense ? null : (
          <fieldset className="flex flex-col gap-2">
            <legend className="text-card-title text-muted mb-1">{t('expenses.status')}</legend>
            <div className="flex flex-wrap gap-4">
              {STATUSES.map((option) => (
                <label key={option} className="flex items-center gap-2">
                  <input
                    type="radio"
                    value={option}
                    className="accent-accent size-5"
                    {...form.register('status')}
                  />
                  {option === 'PAID' ? t('expenses.statusPaid') : t('expenses.statusPlanned')}
                </label>
              ))}
            </div>
          </fieldset>
        )}
        {status === 'PAID' ? (
          <SelectField
            label={t('expenses.payer')}
            options={[
              { value: '', label: t('expenses.choose') },
              ...payers.map((member) => ({ value: member.id, label: memberName(t, member) })),
            ]}
            error={translateKey(t, errors.paidByMemberId?.message)}
            {...form.register('paidByMemberId')}
          />
        ) : null}
        <SplitEditor
          value={split}
          onChange={(next) => {
            setSplit(next);
            setSplitError(false);
          }}
          members={members}
          totalCents={amountCents}
          currency={currency}
        />
        {splitError ? <FormAlert message={t('validation.splitIncomplete')} /> : null}
        <TextField
          label={t('expenses.notesField')}
          maxLength={500}
          error={translateKey(t, errors.notes?.message)}
          {...form.register('notes')}
        />
        <div className="flex flex-wrap gap-2">
          <button
            type="submit"
            className={primaryButton}
            disabled={save.isPending || (amountCents > 0 && !splitComplete)}
          >
            {save.isPending ? t('expenses.saving') : t('expenses.save')}
          </button>
          <button type="button" className={secondaryButton} onClick={onDone}>
            {t('expenses.cancel')}
          </button>
        </div>
      </form>
    </div>
  );
}
