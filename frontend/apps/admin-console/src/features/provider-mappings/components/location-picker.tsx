'use client';

import * as React from 'react';
import { Check, MapPin, Search, X } from 'lucide-react';
import { Input } from '@/components/ui/input';
import { Spinner } from '@/components/ui/feedback';
import { useDebouncedValue } from '@/lib/hooks/use-debounced-value';
import { cn } from '@/lib/utils/cn';
import { useLocationSearch } from '../hooks/use-provider-mappings';
import type { LocationSummary } from '../types';

/**
 * Picks a canonical location that already exists.
 *
 * <p><strong>This control can only ever search.</strong> There is no "add location" affordance, no
 * free-text fallback, and the value it produces is an id returned by the catalogue — a term that
 * matches nothing yields an empty state that says so, not an offer to create one. The API module
 * it draws on exposes no write at all, so the guarantee is structural rather than a matter of this
 * component's markup. A mapping translates a place the catalogue already holds; letting a
 * provider's vocabulary mint RoadScanner places would invert the direction the catalogue is
 * authored in.
 *
 * <p>A hand-built combobox rather than a `Select`, because the options come from a debounced
 * server query keyed on what the operator types. Keyboard behaviour follows the ARIA combobox
 * pattern: ↑/↓ move the active option, Enter selects it, Escape closes the list without changing
 * the selection.
 */
export function LocationPicker({
  value,
  onChange,
  invalid,
  id = 'location-picker',
  disabled = false,
}: {
  /** The currently chosen location, or null. Owned by the caller — this control holds no selection state. */
  value: LocationSummary | null;
  onChange: (location: LocationSummary | null) => void;
  invalid?: boolean;
  id?: string;
  disabled?: boolean;
}) {
  const [term, setTerm] = React.useState('');
  const [open, setOpen] = React.useState(false);
  const [activeIndex, setActiveIndex] = React.useState(0);

  const debounced = useDebouncedValue(term, 300);
  const results = useLocationSearch(debounced);
  const options = React.useMemo(() => results.data ?? [], [results.data]);

  const listboxId = `${id}-listbox`;
  const containerRef = React.useRef<HTMLDivElement>(null);

  // Close when focus leaves the whole control — not on input blur, which fires before a click on
  // an option has been handled and would make the list unselectable with a mouse.
  React.useEffect(() => {
    if (!open) return;

    function onPointerDown(event: PointerEvent) {
      if (!containerRef.current?.contains(event.target as Node)) setOpen(false);
    }
    document.addEventListener('pointerdown', onPointerDown);
    return () => document.removeEventListener('pointerdown', onPointerDown);
  }, [open]);

  React.useEffect(() => {
    setActiveIndex(0);
  }, [debounced]);

  function select(location: LocationSummary) {
    onChange(location);
    setTerm('');
    setOpen(false);
  }

  function onKeyDown(event: React.KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      if (!open) {
        setOpen(true);
        return;
      }
      if (options.length === 0) return;
      const step = event.key === 'ArrowDown' ? 1 : -1;
      setActiveIndex((current) => (current + step + options.length) % options.length);
      return;
    }

    if (event.key === 'Enter') {
      // Only swallow Enter when it is choosing something — otherwise it must reach the form.
      const option = open ? options[activeIndex] : undefined;
      if (option) {
        event.preventDefault();
        select(option);
      }
      return;
    }

    if (event.key === 'Escape' && open) {
      event.preventDefault();
      setOpen(false);
    }
  }

  if (value) {
    return (
      <div
        className={cn(
          'flex items-center justify-between gap-3 rounded-md border bg-elevated px-3.5 py-2.5',
          invalid ? 'border-danger/60' : 'border-line',
        )}
      >
        <span className="flex min-w-0 items-center gap-2.5">
          <MapPin className="size-4 shrink-0 text-accent-text" aria-hidden />
          <span className="flex min-w-0 flex-col">
            <span className="truncate text-body text-content">{value.displayName}</span>
            <span className="truncate text-caption text-content-muted">
              {[value.city, value.state, value.country].filter(Boolean).join(' · ')}
            </span>
          </span>
        </span>

        {!disabled && (
          <button
            type="button"
            onClick={() => {
              onChange(null);
              setTerm('');
            }}
            className={cn(
              'grid size-8 shrink-0 place-items-center rounded-sm text-content-muted',
              'transition-colors hover:bg-white/[0.07] hover:text-content',
              'focus:outline-none focus-visible:ring-2 focus-visible:ring-accent',
            )}
            aria-label={`Clear ${value.displayName}`}
          >
            <X className="size-4" />
          </button>
        )}
      </div>
    );
  }

  const showEmpty = debounced.trim().length >= 2 && !results.isFetching && options.length === 0;

  return (
    <div ref={containerRef} className="relative">
      <Input
        id={id}
        role="combobox"
        aria-expanded={open}
        aria-controls={listboxId}
        aria-autocomplete="list"
        aria-activedescendant={open && options[activeIndex] ? `${id}-option-${activeIndex}` : undefined}
        autoComplete="off"
        icon={<Search />}
        placeholder="Search the location catalogue…"
        value={term}
        disabled={disabled}
        invalid={invalid}
        onChange={(event) => {
          setTerm(event.target.value);
          setOpen(true);
        }}
        onFocus={() => setOpen(true)}
        onKeyDown={onKeyDown}
      />

      {open && (
        <div
          className={cn(
            'absolute z-50 mt-1.5 max-h-64 w-full overflow-y-auto rounded-md panel shadow-lg',
            'border border-line-strong bg-overlay p-1.5',
          )}
        >
          <ul id={listboxId} role="listbox" aria-label="Canonical locations">
            {debounced.trim().length < 2 ? (
              <li className="px-3 py-2.5 text-caption text-content-muted">
                Type at least two characters to search.
              </li>
            ) : results.isFetching ? (
              <li className="px-3 py-2.5">
                <Spinner label="Searching the catalogue…" />
              </li>
            ) : showEmpty ? (
              <li className="flex flex-col gap-1 px-3 py-2.5">
                <span className="text-caption text-content">No catalogue entry matches that.</span>
                <span className="text-caption text-content-muted">
                  Locations are authored in the catalogue itself — mappings never create one.
                </span>
              </li>
            ) : (
              options.map((location, index) => (
                <li key={location.id}>
                  <button
                    type="button"
                    id={`${id}-option-${index}`}
                    role="option"
                    aria-selected={index === activeIndex}
                    onMouseEnter={() => setActiveIndex(index)}
                    onClick={() => select(location)}
                    className={cn(
                      'flex w-full items-center justify-between gap-2 rounded-sm px-3 py-2 text-left',
                      index === activeIndex
                        ? 'bg-white/[0.06] text-content'
                        : 'text-content-secondary',
                    )}
                  >
                    <span className="flex min-w-0 flex-col">
                      <span className="truncate text-body">{location.displayName}</span>
                      <span className="truncate text-caption text-content-muted">
                        {[location.city, location.state, location.country].filter(Boolean).join(' · ')}
                      </span>
                    </span>
                    {index === activeIndex && (
                      <Check className="size-4 shrink-0 text-accent" aria-hidden />
                    )}
                  </button>
                </li>
              ))
            )}
          </ul>
        </div>
      )}
    </div>
  );
}
