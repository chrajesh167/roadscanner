import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { SettingsView } from './settings-view';
import { SETTINGS_SECTIONS } from '../sections';

/**
 * The placeholder's honesty is the thing under test.
 *
 * <p>A screen that says "coming soon" while showing a greyed-out switch has made a promise the
 * backend cannot keep — the capability is not one permission away, the endpoint does not exist.
 * These assertions hold that line.
 */

describe('SettingsView', () => {
  it('declares every planned section with its badge', () => {
    render(<SettingsView />);

    expect(SETTINGS_SECTIONS).toHaveLength(8);
    for (const section of SETTINGS_SECTIONS) {
      expect(screen.getByText(section.title)).toBeInTheDocument();
    }
    expect(screen.getAllByText(/coming in a future sprint/i)).toHaveLength(
      SETTINGS_SECTIONS.length,
    );
  });

  it('has no form control of any kind, disabled or otherwise', () => {
    render(<SettingsView />);

    // Not even a disabled input: it would read as "you lack permission" rather than "there is no
    // API", which is the actual situation.
    expect(screen.queryByRole('textbox')).toBeNull();
    expect(screen.queryByRole('switch')).toBeNull();
    expect(screen.queryByRole('checkbox')).toBeNull();
    expect(screen.queryByRole('combobox')).toBeNull();
    expect(screen.queryByRole('spinbutton')).toBeNull();
  });

  it('says the gap is the missing API, not the operator’s permissions', () => {
    render(<SettingsView />);

    expect(screen.getByText(/no settings api exists yet/i)).toBeInTheDocument();
    expect(screen.getByText(/not because of your permissions/i)).toBeInTheDocument();
  });

  it('sends the operator to the settings that are editable today', () => {
    render(<SettingsView />);

    // The links are real navigation, not decoration — everything they point at works.
    expect(screen.getByRole('link', { name: /provider timeouts/i })).toHaveAttribute(
      'href',
      '/providers',
    );
    expect(screen.getByRole('link', { name: /partner credentials/i })).toHaveAttribute(
      'href',
      '/credentials',
    );
  });

  it('tells each section where its setting lives today', () => {
    render(<SettingsView />);

    // Every section answers "so where is this controlled?" — a placeholder that only said
    // "not yet" would leave an operator with nowhere to go.
    expect(screen.getAllByText('Today')).toHaveLength(SETTINGS_SECTIONS.length);
    expect(screen.getAllByText('Blocked on')).toHaveLength(SETTINGS_SECTIONS.length);
  });
});
