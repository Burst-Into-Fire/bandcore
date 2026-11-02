import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import type { z } from 'zod';

import { api } from '../../api/client';
import { applyProblem } from '../../api/forms';
import { unwrap } from '../../api/problem';
import { memberSchema } from '../../auth/schemas';
import { primaryButton, secondaryButton } from '../../components/buttons';
import { FormAlert } from '../../components/FormAlert';
import { TextField } from '../../components/TextField';
import { translateKey } from '../../i18n/messages';
import type { Member } from './types';

type MemberInput = z.input<typeof memberSchema>;
type MemberValues = z.output<typeof memberSchema>;

/** Adds a member, or edits one when {@code member} is given. */
export function MemberForm({ member, onDone }: { member?: Member; onDone: () => void }) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const form = useForm<MemberInput, unknown, MemberValues>({
    resolver: zodResolver(memberSchema),
    defaultValues: { displayName: member?.displayName ?? '', instrument: member?.instrument ?? '' },
  });
  const save = useMutation({
    mutationFn: async (values: MemberValues) =>
      member
        ? unwrap(
            await api.PATCH('/members/{memberId}', {
              params: { path: { memberId: member.id } },
              body: values,
            }),
          )
        : unwrap(await api.POST('/members', { body: values })),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['members'] });
      onDone();
    },
    onError: (error) => applyProblem(error, form.setError, ['displayName', 'instrument']),
  });
  const { errors } = form.formState;

  return (
    <form
      noValidate
      aria-label={
        member ? t('members.editTitle', { name: member.displayName }) : t('members.addTitle')
      }
      className="bg-surface-raised flex flex-col gap-3 rounded-lg p-3"
      onSubmit={form.handleSubmit((values) => save.mutate(values))}
    >
      <FormAlert message={translateKey(t, errors.root?.server?.message)} />
      <div className="grid gap-3 sm:grid-cols-2">
        <TextField
          label={t('fields.memberName')}
          error={translateKey(t, errors.displayName?.message)}
          {...form.register('displayName')}
        />
        <TextField
          label={t('fields.instrument')}
          placeholder={t('members.instrumentPlaceholder')}
          error={translateKey(t, errors.instrument?.message)}
          {...form.register('instrument')}
        />
      </div>
      <div className="flex flex-wrap gap-2">
        <button type="submit" className={primaryButton} disabled={save.isPending}>
          {save.isPending ? t('members.saving') : t('members.save')}
        </button>
        <button type="button" className={secondaryButton} onClick={onDone}>
          {t('members.cancel')}
        </button>
      </div>
    </form>
  );
}
