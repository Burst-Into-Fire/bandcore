import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import type { z } from 'zod';

import { api } from '../../api/client';
import { applyProblem } from '../../api/forms';
import { ApiError, unwrap } from '../../api/problem';
import { adjustmentSchema } from '../../auth/schemas';
import { primaryButton, secondaryButton } from '../../components/buttons';
import { FormAlert } from '../../components/FormAlert';
import { FormDialog } from '../../components/FormDialog';
import { SelectField } from '../../components/SelectField';
import { TextField } from '../../components/TextField';
import { translateKey } from '../../i18n/messages';
import { MERCH_QUERIES } from './types';

type AdjustmentInput = z.input<typeof adjustmentSchema>;
type AdjustmentValues = z.output<typeof adjustmentSchema>;

const MODES = ['COUNT', 'DELTA'] as const;

const REASONS = ['COUNT', 'DAMAGED', 'LOST', 'GIFT', 'OTHER'] as const;

const MODE = 'mode';

/**
 * Corrects a variant's stock with a reason: the quantity counted, or units in or out. The
 * average cost does not change; the stock never goes below zero.
 */
export function AdjustStockForm({
  variantId,
  name,
  quantityOnHand,
  onDone,
}: {
  variantId: string;
  name: string;
  quantityOnHand: number;
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const firstField = useRef<HTMLInputElement | null>(null);
  const form = useForm<AdjustmentInput, unknown, AdjustmentValues>({
    resolver: zodResolver(adjustmentSchema),
    defaultValues: { mode: 'COUNT', quantity: quantityOnHand, reason: 'COUNT', note: '' },
  });
  const mode = useWatch({ control: form.control, name: MODE });
  const { errors } = form.formState;

  useEffect(() => {
    firstField.current?.focus();
  }, []);

  const save = useMutation({
    mutationFn: async (values: AdjustmentValues) =>
      unwrap(
        await api.POST('/variants/{variantId}/adjustments', {
          params: { path: { variantId } },
          body: {
            ...(values.mode === 'COUNT'
              ? { countedQuantity: values.quantity }
              : { quantityDelta: values.quantity }),
            reason: values.reason,
            note: values.note,
          },
        }),
      ),
    onSuccess: async () => {
      await Promise.all(
        MERCH_QUERIES.map((queryKey) => queryClient.invalidateQueries({ queryKey })),
      );
      onDone();
    },
    onError: (error) =>
      applyProblem(
        error instanceof ApiError
          ? new ApiError(
              error.status,
              error.code,
              error.fieldErrors.map((fieldError) => ({
                ...fieldError,
                field: ['countedQuantity', 'quantityDelta'].includes(fieldError.field)
                  ? 'quantity'
                  : fieldError.field,
              })),
            )
          : error,
        form.setError,
        ['quantity', 'note'],
      ),
  });

  const { ref: quantityRef, ...quantity } = form.register('quantity', { valueAsNumber: true });

  return (
    <FormDialog
      title={t('stock.adjustTitle', { name })}
      onSubmit={form.handleSubmit((values) => save.mutate(values))}
    >
      <p className="text-muted">{t('stock.current', { count: quantityOnHand })}</p>
      <FormAlert message={translateKey(t, errors.root?.server?.message)} />
      <fieldset className="flex flex-col gap-2">
        <legend className="text-card-title text-muted mb-1">{t('stock.mode')}</legend>
        <div className="flex flex-wrap gap-4">
          {MODES.map((option) => (
            <label key={option} className="flex items-center gap-2">
              <input
                type="radio"
                value={option}
                className="accent-accent size-5"
                {...form.register('mode')}
              />
              {option === 'COUNT' ? t('stock.modeCount') : t('stock.modeDelta')}
            </label>
          ))}
        </div>
      </fieldset>
      <TextField
        type="number"
        inputMode="numeric"
        label={mode === 'COUNT' ? t('stock.counted') : t('stock.delta')}
        {...(mode === 'DELTA' ? { hint: t('stock.deltaHint') } : { min: 0 })}
        error={translateKey(t, errors.quantity?.message)}
        ref={(element) => {
          quantityRef(element);
          firstField.current = element;
        }}
        {...quantity}
      />
      <SelectField
        label={t('stock.reason')}
        options={REASONS.map((reason) => ({ value: reason, label: t(`stock.reasons.${reason}`) }))}
        {...form.register('reason')}
      />
      <TextField
        label={t('stock.noteOptional')}
        maxLength={500}
        error={translateKey(t, errors.note?.message)}
        {...form.register('note')}
      />
      <div className="flex flex-wrap gap-2">
        <button type="submit" className={primaryButton} disabled={save.isPending}>
          {save.isPending ? t('stock.saving') : t('stock.save')}
        </button>
        <button type="button" className={secondaryButton} onClick={onDone}>
          {t('stock.cancel')}
        </button>
      </div>
    </FormDialog>
  );
}
