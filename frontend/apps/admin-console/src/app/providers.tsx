'use client';

import * as React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Toaster } from 'sonner';
import { TooltipProvider } from '@/components/ui/misc';
import { ApiError } from '@/lib/api/client';

function makeQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        refetchOnWindowFocus: false,
        // 4xx means the request was wrong, not unlucky — only retry transport/5xx failures.
        retry: (failureCount, error) => {
          if (error instanceof ApiError && error.status >= 400 && error.status < 500) return false;
          return failureCount < 2;
        },
      },
      // Nothing here is retried automatically. Every mutation in this console changes platform
      // state — enabling supply, rotating a secret, probing a partner's API — and a silent second
      // attempt after an ambiguous failure is exactly what an operator must not have to reason
      // about.
      mutations: { retry: false },
    },
  });
}

export function Providers({ children }: { children: React.ReactNode }) {
  // Created in state so a re-render never swaps the client and drops the cache.
  const [queryClient] = React.useState(makeQueryClient);

  return (
    <QueryClientProvider client={queryClient}>
      <TooltipProvider delayDuration={200}>
        {children}
        <Toaster
          position="top-right"
          closeButton
          toastOptions={{
            classNames: {
              toast: 'panel !rounded-lg !text-content !border-line',
              description: '!text-content-secondary',
            },
          }}
        />
      </TooltipProvider>
    </QueryClientProvider>
  );
}
