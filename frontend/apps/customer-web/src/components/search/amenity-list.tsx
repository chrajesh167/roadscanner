import {
  BatteryCharging,
  Bath,
  Bed,
  Coffee,
  Droplets,
  Luggage,
  Monitor,
  Snowflake,
  Sparkles,
  Wifi,
  type LucideIcon,
} from 'lucide-react';

/**
 * Amenities arrive as free-text strings from the operator's catalogue, so matching is done on
 * keywords rather than an enum. Anything unrecognised still renders — with a neutral icon — so a
 * new amenity never silently disappears from the UI.
 */
const ICONS: ReadonlyArray<[RegExp, LucideIcon]> = [
  [/wifi|wi-fi|internet/i, Wifi],
  [/charg|usb|power|socket/i, BatteryCharging],
  [/blanket|bed|sleeper|berth/i, Bed],
  [/water|bottle/i, Droplets],
  [/ac|air.?condition|cooling/i, Snowflake],
  [/tv|screen|entertain|movie/i, Monitor],
  [/snack|meal|coffee|tea|refresh/i, Coffee],
  [/toilet|washroom|restroom/i, Bath],
  [/luggage|baggage|storage/i, Luggage],
];

function iconFor(amenity: string): LucideIcon {
  return ICONS.find(([pattern]) => pattern.test(amenity))?.[1] ?? Sparkles;
}

export function AmenityList({ amenities, max }: { amenities: string[]; max?: number }) {
  if (amenities.length === 0) return null;

  const shown = max ? amenities.slice(0, max) : amenities;
  const remaining = amenities.length - shown.length;

  return (
    <ul className="flex flex-wrap items-center gap-x-4 gap-y-2">
      {shown.map((amenity) => {
        const Icon = iconFor(amenity);
        return (
          <li key={amenity} className="flex items-center gap-1.5 text-caption text-content-secondary">
            <Icon className="size-3.5 shrink-0 text-content-muted" aria-hidden />
            <span className="capitalize">{amenity.toLowerCase()}</span>
          </li>
        );
      })}
      {remaining > 0 && (
        <li className="text-caption text-content-muted">+{remaining} more</li>
      )}
    </ul>
  );
}
