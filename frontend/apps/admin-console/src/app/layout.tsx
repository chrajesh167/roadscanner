import type { Metadata, Viewport } from 'next';
import { Inter } from 'next/font/google';
import { Providers } from './providers';
import './globals.css';

const inter = Inter({
  subsets: ['latin'],
  variable: '--font-inter',
  display: 'swap',
});

export const metadata: Metadata = {
  title: {
    default: 'RoadScanner Admin Console',
    template: '%s · RoadScanner Admin',
  },
  description: 'Administer the RoadScanner provider registry: configuration, credentials and health.',
  applicationName: 'RoadScanner Admin Console',
  // An internal tool has no business being indexed, and it is reachable on a public port during
  // development.
  robots: { index: false, follow: false },
};

export const viewport: Viewport = {
  themeColor: '#0a0c11',
  width: 'device-width',
  initialScale: 1,
  viewportFit: 'cover',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className={inter.variable} suppressHydrationWarning>
      <body className="min-h-dvh antialiased">
        <Providers>
          <a
            href="#main"
            className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-[100] focus:rounded-md focus:bg-accent focus:px-4 focus:py-2 focus:text-white"
          >
            Skip to content
          </a>
          {children}
        </Providers>
      </body>
    </html>
  );
}
