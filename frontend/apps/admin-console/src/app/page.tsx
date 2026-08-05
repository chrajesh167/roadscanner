import { redirect } from 'next/navigation';

/** The console has no marketing surface — the dashboard is its front door. */
export default function RootPage() {
  redirect('/dashboard');
}
