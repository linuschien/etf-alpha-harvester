import { describe, it, expect, vi } from 'vitest';
import { api } from './api-client';

describe('api-client', () => {
  it('performs GET request successfully', async () => {
    vi.spyOn(global, 'fetch').mockResolvedValueOnce({
      ok: true,
      json: async () => ({ status: 'UP' }),
    } as Response);

    const res = await api.get<{ status: string }>('/api/health', { env: 'prod' });
    expect(res.status).toBe('UP');
  });

  it('performs POST request successfully', async () => {
    vi.spyOn(global, 'fetch').mockResolvedValueOnce({
      ok: true,
      json: async () => ({ success: true }),
    } as Response);

    const res = await api.post<{ success: boolean }>('/api/submit', { name: 'test' });
    expect(res.success).toBe(true);
  });

  it('handles GET error', async () => {
    vi.spyOn(global, 'fetch').mockResolvedValueOnce({
      ok: false,
      status: 500,
    } as Response);

    await expect(api.get('/api/fail')).rejects.toThrow('GET /api/fail failed with status 500');
  });

  it('handles POST error', async () => {
    vi.spyOn(global, 'fetch').mockResolvedValueOnce({
      ok: false,
      status: 400,
    } as Response);

    await expect(api.post('/api/fail')).rejects.toThrow('POST /api/fail failed with status 400');
  });

  it('handles GraphQL errors array', async () => {
    vi.spyOn(global, 'fetch').mockResolvedValueOnce({
      ok: true,
      json: async () => ({ errors: [{ message: 'Field not found' }] }),
    } as Response);

    await expect(api.graphql('{ badQuery }')).rejects.toThrow('Field not found');
  });
});
