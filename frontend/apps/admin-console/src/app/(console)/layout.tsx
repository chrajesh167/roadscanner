import { AdminGuard } from '@/components/layout/admin-guard';
import { AppShell } from '@/components/layout/app-shell';

/**
 * Every route in this group is behind the guard and inside the shell. `/login` sits outside the
 * group precisely so it renders neither — a sign-in screen wrapped in an authenticated shell is
 * how redirect loops start.
 */
export default function ConsoleLayout({ children }: { children: React.ReactNode }) {
  return (
    <AdminGuard>
      <AppShell>{children}</AppShell>
    </AdminGuard>
  );
}
