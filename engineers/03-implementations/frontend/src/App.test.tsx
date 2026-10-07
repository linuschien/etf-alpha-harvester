import React from 'react';
import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import App from './App';

describe('App component', () => {
  it('renders root application without crashing', async () => {
    render(<App />);
    expect(
      await screen.findByRole('heading', {
        name: /台股 ETF 量化收割戰情室/i,
      })
    ).toBeInTheDocument();
  });
});
