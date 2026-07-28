'use client';

import * as React from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { MapPin } from 'lucide-react';
import { Input } from '@/components/ui/input';
import { Spinner } from '@/components/ui/feedback';
import { useSuggestions } from '@/lib/hooks/use-search';
import { cn } from '@/lib/utils/cn';

/**
 * Origin/destination field backed by `GET /api/v1/search/suggestions`.
 *
 * The suggestion list is a combobox: fully keyboard-operable (arrows, enter, escape) and
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
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  invalid?: boolean;
  icon?: React.ReactNode;
}) {
  const [open, setOpen] = React.useState(false);
  const [query, setQuery] = React.useState(value);
  const [highlight, setHighlight] = React.useState(-1);
  const containerRef = React.useRef<HTMLDivElement>(null);

  // Debounce so a fast typist issues one request, not one per keystroke.
  const [debounced, setDebounced] = React.useState(value);
  React.useEffect(() => {
    const timer = setTimeout(() => setDebounced(query), 220);
    return () => clearTimeout(timer);
  }, [query]);

  React.useEffect(() => setQuery(value), [value]);

  const { data: suggestions = [], isFetching } = useSuggestions(debounced);
  const visible = open && suggestions.length > 0;

  React.useEffect(() => {
    function onPointerDown(event: MouseEvent) {
      if (!containerRef.current?.contains(event.target as Node)) setOpen(false);
    }
    document.addEventListener('mousedown', onPointerDown);
    return () => document.removeEventListener('mousedown', onPointerDown);
  }, []);

  function commit(place: string) {
    onChange(place);
    setQuery(place);
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
          onChange(event.target.value);
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
            className="absolute left-0 right-0 top-[calc(100%+6px)] z-30 max-h-64 overflow-y-auto rounded-md glass p-1.5 shadow-lg"
          >
            {suggestions.map((place, index) => (
              <li key={place} id={`${id}-option-${index}`} role="option" aria-selected={index === highlight}>
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
                  {place}
                </button>
              </li>
            ))}
          </motion.ul>
        )}
      </AnimatePresence>
    </div>
  );
}
