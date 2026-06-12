import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import App from './App';

describe('App', () => {
  it('renders the application documentation', () => {
    render(<App />);
    expect(screen.getByText('API Documentation')).toBeTruthy();
  });
});
