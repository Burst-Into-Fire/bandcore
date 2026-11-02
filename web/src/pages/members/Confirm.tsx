import { useTranslation } from 'react-i18next';

import { primaryButton, secondaryButton } from '../../components/buttons';

/** In-page confirmation for destructive actions: the question with Confirm and Cancel. */
export function Confirm({
  question,
  pending,
  onConfirm,
  onCancel,
}: {
  question: string;
  pending?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}) {
  const { t } = useTranslation();
  return (
    <div
      role="group"
      aria-label={question}
      className="bg-surface-raised flex flex-col gap-2 rounded-lg p-3"
    >
      <p>{question}</p>
      <div className="flex flex-wrap gap-2">
        <button type="button" className={primaryButton} disabled={pending} onClick={onConfirm}>
          {t('members.confirm')}
        </button>
        <button type="button" className={secondaryButton} onClick={onCancel}>
          {t('members.cancel')}
        </button>
      </div>
    </div>
  );
}
