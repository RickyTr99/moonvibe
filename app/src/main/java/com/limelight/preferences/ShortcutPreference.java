package com.limelight.preferences;

import android.content.Context;
import android.preference.ListPreference;
import android.util.AttributeSet;

import com.limelight.binding.input.Shortcuts;

import java.util.ArrayList;
import java.util.List;

/**
 * MoonVibe: a controller shortcut of the stream. Its options are the actions of Shortcuts,
 * the custom commands of the game menu among them; the settings show them grouped in a popup.
 */
public class ShortcutPreference extends ListPreference {
    public ShortcutPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        reload();
    }

    /** The AYN buttons can also act as another controller button. */
    public boolean isAynButton() {
        String key = getKey();
        return Shortcuts.AYN_BACK.equals(key) || Shortcuts.AYN_M1.equals(key) || Shortcuts.AYN_M2.equals(key);
    }

    public List<Shortcuts.Group> groups() {
        return Shortcuts.groups(getContext(), isAynButton());
    }

    /** Reads the options again: the custom commands may have changed. */
    public void reload() {
        List<CharSequence> entries = new ArrayList<>();
        List<CharSequence> values = new ArrayList<>();
        for (Shortcuts.Group group : groups()) {
            for (Shortcuts.Option option : group.options) {
                entries.add(option.label);
                values.add(option.value);
            }
        }
        setEntries(entries.toArray(new CharSequence[0]));
        setEntryValues(values.toArray(new CharSequence[0]));
    }
}
