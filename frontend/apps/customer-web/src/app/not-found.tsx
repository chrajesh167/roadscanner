import Link from 'next/link';
import { Compass } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { EmptyState } from '@/components/ui/feedback';

export default function NotFound() {
  return (
    <div className="mx-auto max-w-2xl px-5 py-20 sm:px-8">
      <EmptyState
        icon={<Compass />}
        title="This page doesn't exist"
        description="The link may be out of date, or the trip may no longer be listed."
        action={
          <div className="flex flex-wrap justify-center gap-3">
            <Button asChild>
              <Link href="/search">Search trips</Link>
            </Button>
            <Button variant="secondary" asChild>
              <Link href="/">Go home</Link>
            </Button>
          </div>
        }
      />
    </div>
  );
}
