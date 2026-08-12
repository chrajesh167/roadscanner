'use client';

import * as React from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { MapPin } from 'lucide-react';
import { Input } from '@/components/ui/input';
import { Spinner } from '@/components/ui/feedback';
import { useLocationSuggestions } from '@/lib/hooks/use-search';
import { cn } from '@/lib/utils/cn';
import type { SelectedPlace } from '@/lib/api/types';

/**
 * Origin/destination field backed by `GET /api/v1/locations`.
 *
 * It carries a {@link SelectedPlace} rather than a bare string, because a name alone cannot reach a
 * provider: provider search is keyed by canonical location id, and the translation into each
 * provider's own city vocabulary hangs off that id. A field that forgot the id of the row the
 * traveller actually clicked would leave the search unable to ask any provider — which is precisely
 * what it used to do, by consuming a suggestions endpoint that returned nothing but strings.
 *
 * Typing freely is still allowed and still searches; it simply yields `id: null`, and the caller
 * decides what that means. Guessing the id from the typed text is exactly the mistranslation the
 * platform refuses elsewhere by name.
 *
 * The suggestion list remains a combobox: fully keyboard-operable (arrows, enter, escape) and
 * announced via `aria-activedescendant`, because a mouse-only autocomplete is a dead end for
 * anyone using the keyboard or a screen reader.
 */
export function PlaceInput({
  id,
  value,
  onChange,
  placeholder,
  invalid,
  icon,
}: {
  id: string;
  value: SelectedPlace;
  onChange: (value: SelectedPlace) => void;
  placeholder?: string;
  invalid?: boolean;
  icon?: React.ReactNode;
}) {
  const [open, setOpen] = React.useState(false);
  const [query, setQuery] = React.useState(value.name);
  const [highlight, setHighlight] = React.useState(-1);
  const containerRef = React.useRef<HTMLDivElement>(null);

  // Debounce so a fast typist issues one request, not one per keystroke.
  const [debounced, setDebounced] = React.useState(value.name);
  React.useEffect(() => {
    const timer = setTimeout(() => setDebounced(query), 220);
    return () => clearTimeout(timer);
  }, [query]);

  React.useEffect(() => setQuery(value.name), [value.name]);

  const { data: suggestions = [], isFetching } = useLocationSuggestions(debounced);
  const visible = open && suggestions.length > 0;

  React.useEffect(() => {
    function onPointerDown(event: MouseEvent) {
      if (!containerRef.current?.contains(event.target as Node)) setOpen(false);
    }
    document.addEventListener('mousedown', onPointerDown);
    return () => document.removeEventListener('mousedown', onPointerDown);
  }, []);

  /** Picking a row is the only way an id is ever acquired. */
  function commit(place: { id: string; displayName: string }) {
    onChange({ name: place.displayName, id: place.id });
    setQuery(place.displayName);
    setOpen(false);
    setHighlight(-1);
  }

  function onKeyDown(event: React.KeyboardEvent<HTMLInputElement>) {
    if (!visible) return;

    if (event.key === 'ArrowDown') {
      event.preventDefault();
      setHighlight((index) => (index + 1) % suggestions.length);
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      setHighlight((index) => (index <= 0 ? suggestions.length - 1 : index - 1));
    } else if (event.key === 'Enter' && highlight >= 0) {
      event.preventDefault();
      const picked = suggestions[highlight];
      if (picked) commit(picked);
    } else if (event.key === 'Escape') {
      setOpen(false);
      setHighlight(-1);
    }
  }

  return (
    <div ref={containerRef} className="relative">
      <Input
        id={id}
        role="combobox"
        aria-expanded={visible}
        aria-controls={`${id}-listbox`}
        aria-autocomplete="list"
        aria-activedescendant={highlight >= 0 ? `${id}-option-${highlight}` : undefined}
        autoComplete="off"
        value={query}
        invalid={invalid}
        icon={icon ?? <MapPin />}
        placeholder={placeholder}
        onChange={(event) => {
          setQuery(event.target.value);
          // Editing after a pick drops the id: the text no longer necessarily names the row that
          // was chosen, and a stale id would search a place the traveller can no longer see.
          onChange({ name: event.target.value, id: null });
          setOpen(true);
          setHighlight(-1);
        }}
        onFocus={() => setOpen(true)}
        onKeyDown={onKeyDown}
      />

      {isFetching && (
        <span className="absolute right-3 top-1/2 -translate-y-1/2">
          <Spinner />
        </span>
      )}

      <AnimatePresence>
        {visible && (
          <motion.ul
            id={`${id}-listbox`}
            role="listbox"
            initial={{ opacity: 0, y: -4 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -4 }}
            transition={{ duration: 0.16, ease: [0.22, 1, 0.36, 1] }}
            className="absolute left-0 right-0 top-[calc(100%+6px)] z-30 max-h-64 overflow-y-auto rounded-md panel p-1.5 shadow-lg"
          >
            {suggestions.map((place, index) => (
              <li key={place.id} id={`${id}-option-${index}`} role="option" aria-selected={index === highlight}>
                <button
                  type="button"
                  onMouseEnter={() => setHighlight(index)}
                  onClick={() => commit(place)}
                  className={cn(
                    'flex w-full items-center gap-2.5 rounded-sm px-3 py-2 text-left text-body transition-colors',
                    index === highlight
                      ? 'bg-white/[0.07] text-content'
                      : 'text-content-secondary hover:text-content',
                  )}
                >
                  <MapPin className="size-3.5 shrink-0 text-content-muted" />
                  <span className="truncate">{place.displayName}</span>
                  {place.state && (
                    <span className="ml-auto shrink-0 text-caption text-content-muted">{place.state}</span>
                  )}
                </button>
              </li>
            ))}
          </motion.ul>
        )}
      </AnimatePresence>
    </div>
  );
}
