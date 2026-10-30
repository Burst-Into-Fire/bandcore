/** A message for the whole form (server errors), announced by screen readers. */
export function FormAlert({ message }: { message: string | undefined }) {
  if (!message) {
    return null;
  }
  return (
    <p role="alert" className="border-warning text-warning rounded-lg border px-3 py-2">
      {message}
    </p>
  );
}
