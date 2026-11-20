import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api-client';
import type { GlobalAssetOption } from '@/lib/performance-comparison';

export const listGlobalAssetsKeys = {
  all: ['listGlobalAssets'] as const,
};

export function useListGlobalAssets(options?: { enabled?: boolean }) {
  return useQuery({
    queryKey: listGlobalAssetsKeys.all,
    enabled: options?.enabled,
    staleTime: 1000 * 60 * 30, // Metadata updates monthly
    queryFn: () =>
      api
        .graphql<{ listGlobalAssets: GlobalAssetOption[] }>(
          `query ListGlobalAssets {
            listGlobalAssets {
              ticker
              name
              listingDate
              underlyingIndex
              netAssetValue
              fundSizeTwd
            }
          }`
        )
        .then((data) => data.listGlobalAssets ?? []),
  });
}
