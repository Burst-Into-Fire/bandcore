import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import type { z } from 'zod';

import { api } from '../../api/client';
import { applyProblem } from '../../api/forms';
import { unwrap } from '../../api/problem';
import { variantSchema } from '../../auth/schemas';
import { primaryButton, secondaryButton } from '../../components/buttons';
import { FormAlert } from '../../components/FormAlert';
import { FormDialog } from '../../components/FormDialog';
import { MoneyInput } from '../../components/MoneyInput';
import { TextField } from '../../components/TextField';
import { translateKey } from '../../i18n/messages';
import type { Product, Variant } from './types';

type VariantInput = z.input<typeof variantSchema>;
type VariantValues = z.output<typeof variantSchema>;

const FIELDS = ['size', 'color', 'sku', 'priceCents', 'lowStockThreshold'] as const;

const PRICE = 'priceCents';

/** Adds a variant to a product, or changes one when {@code variant} is given (every value is sent). */
export function VariantForm({
  product,
  variant,
  onDone,
}: {
  product: Product;
  variant?: Variant;
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const firstField = useRef<HTMLInputElement | null>(null);
  const form = useForm<VariantInput, unknown, VariantValues>({
    resolver: zodResolver(variantSchema),
    defaultValues: {
      size: variant?.size ?? '',
      color: variant?.color ?? '',
      sku: variant?.sku ?? '',
      priceCents: variant?.priceCents ?? 0,
      lowStockThreshold: variant?.lowStockThreshold ?? 5,
    },
  });
  const { errors } = form.formState;

  useEffect(() => {
    firstField.current?.focus();
  }, []);

  const save = useMutation({
    mutationFn: async (values: VariantValues) => {
      const body = {
        size: values.size,
        color: values.color,
        sku: values.sku,
        lowStockThreshold: values.lowStockThreshold,
        ...(values.priceCents > 0 ? { priceCents: values.priceCents } : {}),
      };
      if (variant) {
        return unwrap(
          await api.PUT('/variants/{variantId}', {
            params: { path: { variantId: variant.id } },
            body: { ...body, version: variant.version },
          }),
        );
      }
      return unwrap(
        await api.POST('/products/{productId}/variants', {
          params: { path: { productId: product.id } },
          body,
        }),
      );
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['products'] });
      onDone();
    },
    onError: (error) => applyProblem(error, form.setError, FIELDS),
  });

  const { ref: sizeRef, ...size } = form.register('size');

  return (
    <FormDialog
      title={
        variant
          ? t('merch.editVariantTitle', { product: product.name })
          : t('merch.newVariantTitle', { product: product.name })
      }
      onSubmit={form.handleSubmit((values) => save.mutate(values))}
    >
      <FormAlert message={translateKey(t, errors.root?.server?.message)} />
      <div className="grid gap-3 sm:grid-cols-3">
        <TextField
          label={t('merch.size')}
          maxLength={20}
          error={translateKey(t, errors.size?.message)}
          ref={(element) => {
            sizeRef(element);
            firstField.current = element;
          }}
          {...size}
        />
        <TextField
          label={t('merch.color')}
          maxLength={40}
          error={translateKey(t, errors.color?.message)}
          {...form.register('color')}
        />
        <TextField
          label={t('merch.sku')}
          maxLength={40}
          error={translateKey(t, errors.sku?.message)}
          {...form.register('sku')}
        />
      </div>
      <div className="grid gap-3 sm:grid-cols-2">
        <Controller
          control={form.control}
          name={PRICE}
          render={({ field }) => (
            <MoneyInput
              label={t('merch.ownPrice')}
              value={field.value}
              onChange={field.onChange}
              onBlur={field.onBlur}
            />
          )}
        />
        <TextField
          type="number"
          inputMode="numeric"
          min={0}
          label={t('merch.lowStockThreshold')}
          error={translateKey(t, errors.lowStockThreshold?.message)}
          {...form.register('lowStockThreshold', { valueAsNumber: true })}
        />
      </div>
      <p className="text-caption text-muted">{t('merch.ownPriceHint')}</p>
      <div className="flex flex-wrap gap-2">
        <button type="submit" className={primaryButton} disabled={save.isPending}>
          {save.isPending ? t('merch.saving') : t('merch.saveVariant')}
        </button>
        <button type="button" className={secondaryButton} onClick={onDone}>
          {t('merch.cancel')}
        </button>
      </div>
    </FormDialog>
  );
}
