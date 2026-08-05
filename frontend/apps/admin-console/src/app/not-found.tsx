import Link from 'next/link';
import { Button } from '@/components/ui/button';

export default function NotFound() {
  return (
    <div className="mx-auto flex max-w-md flex-col items-center gap-5 px-6 py-24 text-center">
      <p className="text-micro uppercase tracking-[0.14em] text-content-muted">404</p>
      <h1 className="text-h2">No such screen</h1>
      <p className="text-body text-content-secondary">
        That route isn&apos;t part of the admin console.
      </p>
      <Button asChild variant="secondary">
        <Link href="/dashboard">Back to dashboard</Link>
      </Button>
    </div>
  );
}
