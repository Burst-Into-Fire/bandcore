import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';
import { Controller, useFieldArray, useForm, useWatch, type Path } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import type { z } from 'zod';

import { api } from '../../api/client';
import { applyProblem, withFormPaths } from '../../api/forms';
import { unwrap } from '../../api/problem';
import { batchSchema } from '../../auth/schemas';
import { primaryButton, secondaryButton, textLink } from '../../components/buttons';
import { FormAlert } from '../../components/FormAlert';
import { FormDialog } from '../../components/FormDialog';
import { MoneyInput } from '../../components/MoneyInput';
import { SelectField } from '../../components/SelectField';
import { TextField } from '../../components/TextField';
import { translateKey } from '../../i18n/messages';
import { CURRENCIES, formatMoney, type Currency } from '../../lib/money';
import { previewSplit, splitBody } from '../../lib/splits';
import { SplitEditor } from '../expenses/SplitEditor';
import { initialSplit, memberName, type SplitState } from '../expenses/splitState';
import { categoryLabel, type Category, type Member } from '../expenses/types';
import { MONEY_QUERIES } from '../settlements/types';
import { lineCosts } from './batchCosts';
import { MERCH_QUERIES, variantName, type Product } from './types';

type BatchInput = z.input<typeof batchSchema>;
type BatchValues = z.output<typeof batchSchema>;

const COST_MODES = ['PER_ITEM', 'ALLOCATED'] as const;

const STATUSES = ['PAID', 'PLANNED'] as const;

const WATCHED = ['currency', 'costMode', 'items', 'totalCostCents', 'createExpense'] as const;

const EXPENSE_STATUS = 'expense.status';

const TOTAL = 'totalCostCents';

const EMPTY_ITEM = { variantId: '', quantity: 1, unitCostCents: 0 };

/**
 * Receives a production batch: items with quantities and costs (per item, or a total allocated
 * by quantity), and optionally the expense that pays for it, split like any expense. Full
 * screen on phones.
 */
export function BatchForm({
  products,
  members,
  categories,
  defaultCurrency,
  today,
  ownMemberId,
  onDone,
}: {
  products: readonly Product[];
  members: readonly Member[];
  categories: readonly Category[];
  defaultCurrency: Currency;
  /** Today in the band's time zone, "YYYY-MM-DD". */
  today: string;
  ownMemberId: string | undefined;
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const firstField = useRef<HTMLSelectElement | null>(null);
  const [split, setSplit] = useState<SplitState>(() => initialSplit(members));
  const [splitError, setSplitError] = useState(false);
  const merchCategory = categories.find(
    (category) => category.defaultKey === 'MERCH' && !category.archived,
  );
  const form = useForm<BatchInput, unknown, BatchValues>({
    resolver: zodResolver(batchSchema),
    defaultValues: {
      supplier: '',
      orderedOn: '',
      receivedOn: today,
      currency: defaultCurrency,
      costMode: 'PER_ITEM',
      totalCostCents: 0,
      notes: '',
      items: [{ ...EMPTY_ITEM }],
      createExpense: false,
      expense: {
        description: t('batches.defaultDescription'),
        categoryId: merchCategory?.id ?? '',
        status: 'PAID',
        paidByMemberId: ownMemberId ?? '',
      },
    },
  });
  const items = useFieldArray({ control: form.control, name: 'items' });
  const [currency, costMode, watchedItems, totalCostCents, createExpense] = useWatch({
    control: form.control,
    name: WATCHED,
  });
  const expenseStatus = useWatch({ control: form.control, name: EXPENSE_STATUS });
  const { errors } = form.formState;
  const lines = lineCosts({ costMode, totalCostCents, items: watchedItems });
  const total =
    costMode === 'ALLOCATED' ? totalCostCents : lines.reduce((sum, cost) => sum + cost, 0);
  const splitComplete = previewSplit(split.mode, total, split.entries).complete;
  const variants = products
    .filter((product) => !product.archived && product.currency === currency)
    .flatMap((product) =>
      product.variants
        .filter((variant) => !variant.archived)
        .map((variant) => ({ value: variant.id, label: variantName(product.name, variant) })),
    );
  const payers = members.filter((member) => member.status === 'ACTIVE');

  useEffect(() => {
    firstField.current?.focus();
  }, []);

  const save = useMutation({
    mutationFn: async (values: BatchValues) =>
      unwrap(
        await api.POST('/batches', {
          body: {
            supplier: values.supplier,
            ...(values.orderedOn ? { orderedOn: values.orderedOn } : {}),
            receivedOn: values.receivedOn,
            currency: values.currency,
            costMode: values.costMode,
            ...(values.costMode === 'ALLOCATED' ? { totalCostCents: values.totalCostCents } : {}),
            notes: values.notes,
            items: values.items.map((item) => ({
              variantId: item.variantId,
              quantity: item.quantity,
              ...(values.costMode === 'PER_ITEM' ? { unitCostCents: item.unitCostCents } : {}),
            })),
            ...(values.createExpense
              ? {
                  expense: {
                    description: values.expense.description,
                    categoryId: values.expense.categoryId,
                    paid: values.expense.status === 'PAID',
                    ...(values.expense.status === 'PAID'
                      ? { paidByMemberId: values.expense.paidByMemberId }
                      : {}),
                    split: splitBody(split.mode, split.entries),
                  },
                }
              : {}),
          },
        }),
      ),
    onSuccess: async (_, values) => {
      const queries = values.createExpense ? [...MERCH_QUERIES, ...MONEY_QUERIES] : MERCH_QUERIES;
      await Promise.all(queries.map((queryKey) => queryClient.invalidateQueries({ queryKey })));
      onDone();
    },
    onError: (error) => {
      const fields: Path<BatchInput>[] = [
        'supplier',
        'orderedOn',
        'receivedOn',
        'currency',
        TOTAL,
        'notes',
        'expense.description',
        'expense.categoryId',
        'expense.paidByMemberId',
      ];
      items.fields.forEach((_, index) => {
        fields.push(
          `items.${index}.variantId`,
          `items.${index}.quantity`,
          `items.${index}.unitCostCents`,
        );
      });
      applyProblem(withFormPaths(error), form.setError, fields);
    },
  });

  const submit = form.handleSubmit((values) => {
    if (values.createExpense && !splitComplete) {
      setSplitError(true);
      return;
    }
    setSplitError(false);
    save.mutate(values);
  });

  const { ref: currencyRef, ...currencyField } = form.register('currency');

  return (
    <FormDialog title={t('batches.newTitle')} onSubmit={submit}>
      <FormAlert message={translateKey(t, errors.root?.server?.message)} />
      <div className="grid gap-4 sm:grid-cols-3">
        <SelectField
          label={t('batches.currency')}
          options={CURRENCIES.map((code) => ({ value: code, label: code }))}
          error={translateKey(t, errors.currency?.message)}
          ref={(element) => {
            currencyRef(element);
            firstField.current = element;
          }}
          {...currencyField}
        />
        <TextField
          type="date"
          label={t('batches.receivedOn')}
          error={translateKey(t, errors.receivedOn?.message)}
          {...form.register('receivedOn')}
        />
        <TextField
          type="date"
          label={t('batches.orderedOn')}
          error={translateKey(t, errors.orderedOn?.message)}
          {...form.register('orderedOn')}
        />
      </div>
      <TextField
        label={t('batches.supplier')}
        maxLength={120}
        error={translateKey(t, errors.supplier?.message)}
        {...form.register('supplier')}
      />
      <fieldset className="flex flex-col gap-2">
        <legend className="text-card-title text-muted mb-1">{t('batches.costMode')}</legend>
        <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap sm:gap-4">
          {COST_MODES.map((option) => (
            <label key={option} className="flex items-center gap-2">
              <input
                type="radio"
                value={option}
                className="accent-accent size-5"
                {...form.register('costMode')}
              />
              {t(`batches.${option}`)}
            </label>
          ))}
        </div>
      </fieldset>
      {costMode === 'ALLOCATED' ? (
        <Controller
          control={form.control}
          name={TOTAL}
          render={({ field }) => (
            <MoneyInput
              label={t('batches.totalCost')}
              value={field.value}
              onChange={field.onChange}
              onBlur={field.onBlur}
            />
          )}
        />
      ) : null}
      <fieldset className="flex flex-col gap-3">
        <legend className="text-card-title text-muted mb-1">{t('batches.itemsHeading')}</legend>
        {variants.length === 0 ? (
          <p className="text-warning">{t('batches.noVariants', { currency })}</p>
        ) : null}
        {items.fields.map((field, index) => (
          <div
            key={field.id}
            role="group"
            aria-label={t('batches.itemNumber', { number: index + 1 })}
            className="border-border flex flex-col gap-3 rounded-lg border p-3"
          >
            <SelectField
              label={t('batches.variant')}
              options={[{ value: '', label: t('expenses.choose') }, ...variants]}
              error={translateKey(t, errors.items?.[index]?.variantId?.message)}
              {...form.register(`items.${index}.variantId`)}
            />
            <div className="grid gap-3 sm:grid-cols-2">
              <TextField
                type="number"
                inputMode="numeric"
                min={1}
                label={t('batches.quantity')}
                error={translateKey(t, errors.items?.[index]?.quantity?.message)}
                {...form.register(`items.${index}.quantity`, { valueAsNumber: true })}
              />
              {costMode === 'PER_ITEM' ? (
                <Controller
                  control={form.control}
                  name={`items.${index}.unitCostCents`}
                  render={({ field: cost }) => (
                    <MoneyInput
                      label={t('batches.unitCost')}
                      value={cost.value}
                      onChange={cost.onChange}
                      onBlur={cost.onBlur}
                    />
                  )}
                />
              ) : null}
            </div>
            <div className="flex items-center justify-between gap-3">
              <span className="text-muted tabular-nums" aria-live="polite">
                {t('batches.lineCost', { amount: formatMoney(lines[index] ?? 0, currency) })}
              </span>
              {items.fields.length > 1 ? (
                <button type="button" className={textLink} onClick={() => items.remove(index)}>
                  {t('batches.removeItem', { number: index + 1 })}
                </button>
              ) : null}
            </div>
          </div>
        ))}
        <button
          type="button"
          className={`${secondaryButton} self-start`}
          onClick={() => items.append({ ...EMPTY_ITEM })}
        >
          {t('batches.addItem')}
        </button>
      </fieldset>
      <p className="text-card-title font-semibold tabular-nums" role="status">
        {t('batches.total', { amount: formatMoney(total, currency) })}
      </p>
      <TextField
        label={t('batches.notes')}
        maxLength={500}
        error={translateKey(t, errors.notes?.message)}
        {...form.register('notes')}
      />
      <label className="flex items-center gap-2">
        <input
          type="checkbox"
          className="accent-accent size-5"
          {...form.register('createExpense')}
        />
        {t('batches.createExpense')}
      </label>
      {createExpense ? (
        <section className="border-border flex flex-col gap-4 rounded-lg border p-3">
          <p className="text-caption text-muted">{t('batches.expenseHint')}</p>
          <div className="grid gap-4 sm:grid-cols-2">
            <TextField
              label={t('batches.expenseDescription')}
              maxLength={120}
              error={translateKey(t, errors.expense?.description?.message)}
              {...form.register('expense.description')}
            />
            <SelectField
              label={t('expenses.category')}
              options={[
                { value: '', label: t('expenses.choose') },
                ...categories
                  .filter((category) => !category.archived)
                  .map((category) => ({ value: category.id, label: categoryLabel(t, category) })),
              ]}
              error={translateKey(t, errors.expense?.categoryId?.message)}
              {...form.register('expense.categoryId')}
            />
          </div>
          <fieldset className="flex flex-col gap-2">
            <legend className="text-card-title text-muted mb-1">{t('expenses.status')}</legend>
            <div className="flex flex-wrap gap-4">
              {STATUSES.map((option) => (
                <label key={option} className="flex items-center gap-2">
                  <input
                    type="radio"
                    value={option}
                    className="accent-accent size-5"
                    {...form.register('expense.status')}
                  />
                  {option === 'PAID' ? t('expenses.statusPaid') : t('expenses.statusPlanned')}
                </label>
              ))}
            </div>
          </fieldset>
          {expenseStatus === 'PAID' ? (
            <SelectField
              label={t('expenses.payer')}
              options={[
                { value: '', label: t('expenses.choose') },
                ...payers.map((member) => ({ value: member.id, label: memberName(t, member) })),
              ]}
              error={translateKey(t, errors.expense?.paidByMemberId?.message)}
              {...form.register('expense.paidByMemberId')}
            />
          ) : null}
          <SplitEditor
            value={split}
            onChange={(next) => {
              setSplit(next);
              setSplitError(false);
            }}
            members={members}
            totalCents={total}
            currency={currency}
          />
          {splitError ? <FormAlert message={t('validation.splitIncomplete')} /> : null}
        </section>
      ) : null}
      <div className="flex flex-wrap gap-2">
        <button
          type="submit"
          className={primaryButton}
          disabled={save.isPending || (createExpense && total > 0 && !splitComplete)}
        >
          {save.isPending ? t('batches.saving') : t('batches.save')}
        </button>
        <button type="button" className={secondaryButton} onClick={onDone}>
          {t('batches.cancel')}
        </button>
      </div>
    </FormDialog>
  );
}
