import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it } from 'vitest';

import { MoneyInput } from './MoneyInput';

function Harness({ onValue }: { onValue: (cents: number) => void }) {
  const [cents, setCents] = useState(0);
  return (
    <MoneyInput
      label="Valor"
      value={cents}
      onChange={(next) => {
        setCents(next);
        onValue(next);
      }}
    />
  );
}

describe('MoneyInput', () => {
  it('reads typed digits as integer cents', async () => {
    const values: number[] = [];
    render(<Harness onValue={(cents) => values.push(cents)} />);
    const input = screen.getByLabelText('Valor');

    await userEvent.type(input, '350000');

    expect(input).toHaveValue('3.500,00');
    expect(values.at(-1)).toBe(350000);
    expect(values.every(Number.isInteger)).toBe(true);
  });

  it('ignores anything that is not a digit', async () => {
    render(<Harness onValue={() => {}} />);
    const input = screen.getByLabelText('Valor');

    await userEvent.type(input, '1a,-2.5');

    expect(input).toHaveValue('1,25');
  });

  it('goes back to zero when cleared', async () => {
    render(<Harness onValue={() => {}} />);
    const input = screen.getByLabelText('Valor');

    await userEvent.type(input, '99');
    await userEvent.clear(input);

    expect(input).toHaveValue('0,00');
  });
});
