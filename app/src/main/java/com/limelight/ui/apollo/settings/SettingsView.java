package com.limelight.ui.apollo.settings;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.database.DataSetObserver;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.preference.TwoStatePreference;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.os.Build;
import android.text.style.AbsoluteSizeSpan;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ScrollView;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.preferences.ConfirmDeleteOscPreference;
import com.limelight.preferences.LanguagePreference;
import com.limelight.preferences.SeekBarPreference;
import com.limelight.ui.apollo.ApolloUi;
import com.limelight.ui.apollo.ApolloWidgets;
import com.limelight.ui.apollo.hints.ButtonGlyph;
import com.limelight.ui.apollo.hints.HintRow;
import com.limelight.ui.theme.ApolloColors;
import com.limelight.ui.theme.ApolloMotion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The MoonVibe settings screen: categories on the left, the settings of the selected one on the right.
 * It draws the preferences that the original settings fragment builds, and changes them the way the
 * original list would (same change listeners, dependencies and dialogs), so the settings logic stays untouched.
 */
public class SettingsView extends FrameLayout {
    private final Activity activity;
    private final ApolloColors colors;
    private final List<SettingsLayout.Category> categories;
    // One page without the category column, like the settings of a single game
    private final boolean singlePage;
    // Labels set by the screen for titles and options, over the original ones
    private final Map<String, CharSequence> titleOverrides = new HashMap<>();
    private final Map<String, CharSequence> optionOverrides = new HashMap<>();
    private final LinearLayout categoryList;
    private final LinearLayout content;
    private final LinearLayout rowList;
    private final ScrollView rowScroll;
    private final OptionsPopup popup;
    private final InfoPopup infoPopup;
    // On top of the categories; null on a single page
    private final SearchField searchField;
    // What is searched for, empty while a category shows
    private String query = "";

    private PreferenceScreen screen;
    private ListAdapter screenAdapter;
    private final DataSetObserver screenObserver = new DataSetObserver() {
        @Override
        public void onChanged() {
            bindRows(true);
        }
    };
    private final SharedPreferences.OnSharedPreferenceChangeListener prefsListener = (prefs, key) -> post(() -> {
        // A switch changed elsewhere, like auto resume in the quick settings: keep its preference in step
        Preference pref = screen != null && key != null ? screen.findPreference(key) : null;
        if (pref instanceof TwoStatePreference && prefs.contains(key)) {
            boolean stored = prefs.getBoolean(key, false);
            if (((TwoStatePreference) pref).isChecked() != stored) {
                ((TwoStatePreference) pref).setChecked(stored);
            }
        }
        else if (pref instanceof ListPreference && prefs.contains(key)) {
            String stored = prefs.getString(key, null);
            if (stored != null && !stored.equals(((ListPreference) pref).getValue())) {
                ((ListPreference) pref).setValue(stored);
            }
        }
        bindRows(true);
    });

    // What the selected category shows, rebuilt when it changes
    private final List<Row> rows = new ArrayList<>();
    private final List<View> categoryViews = new ArrayList<>();
    private final List<SettingsLayout.Category> shownCategories = new ArrayList<>();
    private int selectedCategory = 0;
    private final Set<String> sectionsToggled = new HashSet<>();

    private static class Row {
        Preference pref;
        View view;
        TextView label;
        ApolloWidgets.SwitchView toggle;
        TextView value;
        SliderView slider;
        int sliderValue;
        // Shown after the name while the setting differs from its default
        DotSpan dot;
        boolean modified;
        // What the setting does, behind the "i" after its name; null for the obvious ones
        SettingsLayout.Info info;
        InfoSpan infoSpan;
    }

    // Defaults of the settings, to mark the changed ones and restore a category; null on a single page
    private final SettingsDefaults defaults;
    private View restoreRow;

    public SettingsView(Activity activity, ApolloColors colors) {
        this(activity, colors, SettingsLayout.categories(), false);
    }

    /**
     * One page with the given preferences in their order, under a title: the settings of a single game.
     */
    public static SettingsView singlePage(Activity activity, ApolloColors colors, CharSequence title, String... keys) {
        SettingsLayout.Item[] items = new SettingsLayout.Item[keys.length];
        for (int i = 0; i < keys.length; i++) {
            items[i] = new SettingsLayout.Item(keys[i]);
        }
        List<SettingsLayout.Category> page = new ArrayList<>();
        page.add(new SettingsLayout.Category(title, (Object[]) items));
        return new SettingsView(activity, colors, page, true);
    }

    public void setTitleOverride(String key, CharSequence title) {
        titleOverrides.put(key, title);
    }

    /** The label of one option of a list setting; value "" is the "default" option some lists have. */
    public void setOptionOverride(String key, String value, CharSequence label) {
        optionOverrides.put(key + "/" + value, label);
    }

    private SettingsView(Activity activity, ApolloColors colors, List<SettingsLayout.Category> categories, boolean singlePage) {
        super(activity);
        this.activity = activity;
        this.colors = colors;
        this.categories = categories;
        this.singlePage = singlePage;
        // A game page shows its own overrides, which have no defaults to go back to
        this.defaults = singlePage ? null : new SettingsDefaults(activity);

        content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setPadding(dp(14), dp(2), dp(16), dp(12));
        addView(content, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        ScrollView categoryScroll = new ScrollView(activity);
        categoryScroll.setVerticalScrollBarEnabled(false);
        categoryScroll.setClipToPadding(false);
        categoryList = new LinearLayout(activity);
        categoryList.setOrientation(LinearLayout.VERTICAL);
        categoryList.setPadding(dp(2), dp(2), dp(2), dp(8));
        categoryScroll.addView(categoryList);

        // The search box stays on top of the categories, which scroll under it
        LinearLayout leftColumn = new LinearLayout(activity);
        leftColumn.setOrientation(LinearLayout.VERTICAL);
        searchField = singlePage ? null : new SearchField(activity, colors);
        if (searchField != null) {
            LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
            fieldParams.setMargins(dp(2), dp(2), dp(2), dp(6));
            leftColumn.addView(searchField, fieldParams);
            searchField.setListener(new SearchField.Listener() {
                @Override
                public void onQueryChanged(String newQuery) {
                    if (newQuery.equals(query)) {
                        return;
                    }
                    if (newQuery.isEmpty()) {
                        showCategory(exitSearch(), true);
                    } else {
                        boolean entering = query.isEmpty();
                        query = newQuery;
                        showSearch(entering);
                    }
                }

                @Override
                public void onSearchSubmitted() {
                    View first = firstFocusableRow();
                    if (first != null) {
                        first.requestFocus();
                    }
                }
            });
        }
        leftColumn.addView(categoryScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        content.addView(leftColumn, new LinearLayout.LayoutParams(dp(218), ViewGroup.LayoutParams.MATCH_PARENT));
        leftColumn.setVisibility(singlePage ? GONE : VISIBLE);

        rowScroll = new ScrollView(activity) {
            @Override
            public void requestChildFocus(View child, View focused) {
                super.requestChildFocus(child, focused);
                // Back on the first row the page scrolls to its very top, or the category title above it
                // (which takes no focus) stays out of sight
                if (isFirstRow(focused) && getScrollY() > 0) {
                    smoothScrollTo(0, 0);
                }
            }
        };
        rowScroll.setVerticalScrollBarEnabled(false);
        rowScroll.setBackground(ApolloUi.roundRect(colors.surfaceContainerLow, dp(20)));
        rowScroll.setClipToOutline(true);
        rowList = new LinearLayout(activity);
        rowList.setOrientation(LinearLayout.VERTICAL);
        rowList.setPadding(dp(6), 0, dp(6), dp(10));
        rowScroll.addView(rowList);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        rowParams.leftMargin = singlePage ? 0 : dp(12);
        content.addView(rowScroll, rowParams);

        popup = new OptionsPopup(this, content, colors);
        infoPopup = new InfoPopup(this, content, colors);
    }

    private int dp(float value) {
        return ApolloUi.dp(getContext(), value);
    }

    // Whether the view is, or is inside, the first row of the page that can take the focus
    private boolean isFirstRow(View view) {
        while (view != null && view.getParent() != rowList) {
            view = view.getParent() instanceof View ? (View) view.getParent() : null;
        }
        if (view == null) {
            return false;
        }
        for (int i = 0; i < rowList.getChildCount(); i++) {
            View row = rowList.getChildAt(i);
            if (row.getVisibility() == VISIBLE && row.isFocusable()) {
                return row == view;
            }
        }
        return false;
    }

    /**
     * Called each time the original fragment has built its preferences, it rebuilds them on some changes.
     */
    public void setScreen(PreferenceScreen screen) {
        if (screenAdapter != null) {
            screenAdapter.unregisterDataSetObserver(screenObserver);
        }
        this.screen = screen;
        this.screenAdapter = screen.getRootAdapter();
        screenAdapter.registerDataSetObserver(screenObserver);

        // A setting that upstream adds later still shows up, at the end of the App category
        if (!singlePage) {
            addUnplacedPreferences();
        }

        // Keep the focus where it was, the rows are rebuilt
        View focused = rowList.findFocus();
        int focusedRow = focused != null ? rowList.indexOfChild(focused) : -1;
        boolean categoryFocused = categoryList.hasFocus();

        buildCategories();
        selectedCategory = Math.max(0, Math.min(selectedCategory, shownCategories.size() - 1));
        if (searching()) {
            showSearch(false);
        } else {
            showCategory(selectedCategory, false);
        }
        if (categoryViews.isEmpty()) {
            return;
        }
        View defaultFocus = categoryViews.get(selectedCategory);
        if (singlePage) {
            // No categories: a gamepad starts from the first setting
            for (int i = 0; i < rowList.getChildCount() && defaultFocus == categoryViews.get(selectedCategory); i++) {
                if (rowList.getChildAt(i).isFocusable()) {
                    defaultFocus = rowList.getChildAt(i);
                }
            }
        }
        ApolloUi.focusByDefault(activity, defaultFocus);
        if (focusedRow >= 0 && focusedRow < rowList.getChildCount()) {
            rowList.getChildAt(focusedRow).requestFocus();
        } else if (categoryFocused) {
            categoryViews.get(selectedCategory).requestFocus();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        PreferenceManager.getDefaultSharedPreferences(getContext()).registerOnSharedPreferenceChangeListener(prefsListener);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        PreferenceManager.getDefaultSharedPreferences(getContext()).unregisterOnSharedPreferenceChangeListener(prefsListener);
        if (screenAdapter != null) {
            screenAdapter.unregisterDataSetObserver(screenObserver);
        }
    }

    /** Back closes the explanation, the options menu or the search first */
    public boolean onBackPressed() {
        return infoPopup.dismiss(true) || popup.dismiss(true) || closeSearch();
    }

    /**
     * B of a gamepad: closes the explanation, the options menu or the search,
     * or goes from the settings back to their category
     */
    public boolean onButtonB() {
        if (infoPopup.dismiss(true) || popup.dismiss(true) || closeSearch()) {
            return true;
        }
        // From the empty search box, back to the categories
        if (searchField != null && searchField.edit.hasFocus() && !categoryViews.isEmpty()) {
            searchField.hideKeyboard();
            categoryViews.get(Math.max(0, selectedCategory)).requestFocus();
            return true;
        }
        if (!singlePage && rowList.hasFocus() && !categoryViews.isEmpty()) {
            categoryViews.get(selectedCategory).requestFocus();
            return true;
        }
        return false;
    }

    // --- Search

    private boolean searching() {
        return !query.isEmpty();
    }

    // Android 13+ with predictive back sends back to this instead of onBackPressed(), while a search is open
    private Object searchBackCallback;

    private void updateSearchBack() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        if (searching() && searchBackCallback == null) {
            OnBackInvokedCallback callback = this::closeSearch;
            activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback);
            searchBackCallback = callback;
        } else if (!searching() && searchBackCallback != null) {
            activity.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback((OnBackInvokedCallback) searchBackCallback);
            searchBackCallback = null;
        }
    }

    /** Leaves the search without showing anything: @return the category to show again */
    private int exitSearch() {
        int category = selectedCategory;
        query = "";
        if (searchField != null) {
            searchField.setQuery("");
            searchField.hideKeyboard();
        }
        updateSearchBack();
        // Makes the category count as a new one, so it comes back with its animation
        selectedCategory = -1;
        return Math.max(0, category);
    }

    // B or back during a search: back to the category shown before it
    private boolean closeSearch() {
        if (!searching()) {
            return false;
        }
        int category = exitSearch();
        showCategory(category, true);
        if (category < categoryViews.size()) {
            categoryViews.get(category).requestFocus();
        }
        return true;
    }

    private View firstFocusableRow() {
        for (int i = 0; i < rowList.getChildCount(); i++) {
            View child = rowList.getChildAt(i);
            if (child.isFocusable() && child.getVisibility() == VISIBLE) {
                return child;
            }
        }
        return null;
    }

    /**
     * The settings whose name, explanation or options hold the query, in the order of the categories,
     * each under the category (and section) it belongs to.
     */
    private void showSearch(boolean animate) {
        rows.clear();
        rowList.removeAllViews();
        restoreRow = null;
        styleCategories();
        updateSearchBack();

        String folded = SettingsSearch.fold(query);
        List<Preference> seen = new ArrayList<>();
        int found = 0;
        for (SettingsLayout.Category category : shownCategories) {
            String categoryTitle = category.title(getContext()).toString();
            for (Object entry : category.entries) {
                List<SettingsLayout.Item> items = entry instanceof SettingsLayout.Section
                        ? ((SettingsLayout.Section) entry).items : java.util.Collections.singletonList((SettingsLayout.Item) entry);
                String path = entry instanceof SettingsLayout.Section
                        ? categoryTitle + " › " + getContext().getString(((SettingsLayout.Section) entry).titleRes) : categoryTitle;
                for (SettingsLayout.Item item : items) {
                    Preference pref = find(item);
                    if (pref == null || seen.contains(pref)) {
                        continue;
                    }
                    seen.add(pref);
                    int inTitle = SettingsSearch.indexOf(title(pref), folded);
                    CharSequence snippet = inTitle >= 0 ? null : explanationMatch(pref, folded);
                    if (inTitle >= 0 || snippet != null) {
                        addRow(pref, path, inTitle, snippet);
                        found++;
                    }
                }
            }
        }

        // The title of the page, with how many settings were found
        LinearLayout header = new LinearLayout(getContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.BOTTOM);
        header.setPadding(dp(14), dp(14), dp(14), dp(6));
        TextView title = ApolloUi.text(getContext(), getContext().getString(R.string.apollo_search_results, query), 18, colors.onSurface, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (found > 0) {
            header.addView(ApolloUi.text(getContext(), String.valueOf(found), 13, colors.outline, false));
        }
        rowList.addView(header, 0);
        if (found == 0) {
            TextView none = ApolloUi.text(getContext(), getContext().getString(R.string.apollo_search_none), 14, colors.onSurfaceVariant, false);
            none.setPadding(dp(14), dp(10), dp(14), dp(10));
            rowList.addView(none);
        }

        bindRows(false);
        linkFocus();
        rowScroll.scrollTo(0, 0);
        if (animate) {
            rowList.setAlpha(0f);
            rowList.setTranslationY(dp(12));
            rowList.animate().alpha(1f).translationY(0).setDuration(ApolloMotion.MEDIUM)
                    .setInterpolator(ApolloMotion.EMPHASIZED_DECELERATE).start();
        }
    }

    // The query in what the "i" of a setting explains, or in one of its options: the words around it, or null
    private CharSequence explanationMatch(Preference pref, String folded) {
        List<String> texts = new ArrayList<>();
        SettingsLayout.Info info = pref.getKey() != null ? SettingsLayout.INFO.get(pref.getKey()) : null;
        if (info != null) {
            texts.add(getContext().getString(info.text));
            if (info.optionValues != 0) {
                String[] names = getResources().getStringArray(info.optionNames);
                String[] optionTexts = getResources().getStringArray(info.optionTexts);
                for (int i = 0; i < names.length && i < optionTexts.length; i++) {
                    texts.add(names[i] + ": " + optionTexts[i]);
                }
            }
        }
        if (pref instanceof ListPreference && !(pref instanceof LanguagePreference) && ((ListPreference) pref).getEntries() != null) {
            ListPreference list = (ListPreference) pref;
            for (int i = 0; i < list.getEntries().length; i++) {
                texts.add(optionLabel(list, i).toString());
            }
        }
        for (String text : texts) {
            int at = SettingsSearch.indexOf(text, folded);
            if (at >= 0) {
                return SettingsSearch.snippet(text, at, folded.length(), colors.onSurfaceVariant, colors.primary);
            }
        }
        return null;
    }

    // --- Preferences

    private void collectPreferences(PreferenceGroup group, List<Preference> out) {
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            Preference pref = group.getPreference(i);
            if (pref instanceof PreferenceCategory) {
                collectPreferences((PreferenceGroup) pref, out);
            } else {
                out.add(pref);
            }
        }
    }

    private Preference find(SettingsLayout.Item item) {
        if (item.key != null) {
            return screen.findPreference(item.key);
        }
        List<Preference> all = new ArrayList<>();
        collectPreferences(screen, all);
        for (Preference pref : all) {
            if (item.type.isInstance(pref)) {
                return pref;
            }
        }
        return null;
    }

    private void addUnplacedPreferences() {
        Set<String> placedKeys = new HashSet<>();
        Set<Class<?>> placedTypes = new HashSet<>();
        SettingsLayout.Section other = null;
        for (SettingsLayout.Category category : categories) {
            for (Object entry : category.entries) {
                List<SettingsLayout.Item> items = entry instanceof SettingsLayout.Section
                        ? ((SettingsLayout.Section) entry).items : java.util.Collections.singletonList((SettingsLayout.Item) entry);
                for (SettingsLayout.Item item : items) {
                    if (item.key != null) {
                        placedKeys.add(item.key);
                    } else {
                        placedTypes.add(item.type);
                    }
                }
                if (entry instanceof SettingsLayout.Section && ((SettingsLayout.Section) entry).titleRes == R.string.apollo_section_other) {
                    other = (SettingsLayout.Section) entry;
                }
            }
        }

        List<Preference> all = new ArrayList<>();
        collectPreferences(screen, all);
        for (Preference pref : all) {
            boolean placed = pref.getKey() != null ? placedKeys.contains(pref.getKey()) : placedTypes.contains(pref.getClass());
            if (placed || pref.getKey() == null) {
                continue;
            }
            if (other == null) {
                other = new SettingsLayout.Section(R.string.apollo_section_other, true);
                categories.get(categories.size() - 1).entries.add(other);
            }
            other.items.add(new SettingsLayout.Item(pref.getKey()));
        }
    }

    private boolean hasAny(SettingsLayout.Category category) {
        for (Object entry : category.entries) {
            if (entry instanceof SettingsLayout.Item) {
                if (find((SettingsLayout.Item) entry) != null) {
                    return true;
                }
            } else {
                for (SettingsLayout.Item item : ((SettingsLayout.Section) entry).items) {
                    if (find(item) != null) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // Like a tap on the row of the original list: change listeners, dialogs and dependencies all apply
    private void performClick(Preference pref) {
        for (int i = 0; i < screenAdapter.getCount(); i++) {
            if (screenAdapter.getItem(i) == pref) {
                screen.onItemClick(null, null, i, screenAdapter.getItemId(i));
                return;
            }
        }
    }

    private static boolean callChangeListener(Preference pref, Object newValue) {
        Preference.OnPreferenceChangeListener listener = pref.getOnPreferenceChangeListener();
        return listener == null || listener.onPreferenceChange(pref, newValue);
    }

    private CharSequence optionLabel(ListPreference pref, int index) {
        String value = pref.getEntryValues()[index].toString();
        CharSequence override = optionOverrides.get(pref.getKey() + "/" + value);
        if (override != null) {
            return override;
        }
        Integer shortLabel = SettingsLayout.SHORT_OPTIONS.get(pref.getKey() + "/" + value);
        return shortLabel != null ? getContext().getString(shortLabel) : pref.getEntries()[index];
    }

    private CharSequence title(Preference pref) {
        if (pref instanceof ConfirmDeleteOscPreference) {
            return getContext().getString(SettingsLayout.SHORT_TITLE_RESET_OSC);
        }
        if (pref.getKey() != null && titleOverrides.containsKey(pref.getKey())) {
            return titleOverrides.get(pref.getKey());
        }
        Integer shortTitle = pref.getKey() != null ? SettingsLayout.SHORT_TITLES.get(pref.getKey()) : null;
        return shortTitle != null ? getContext().getString(shortTitle) : pref.getTitle();
    }

    // --- Categories

    private void buildCategories() {
        categoryList.removeAllViews();
        categoryViews.clear();
        shownCategories.clear();

        for (SettingsLayout.Category category : categories) {
            if (!hasAny(category)) {
                continue;
            }
            final int index = shownCategories.size();
            shownCategories.add(category);

            LinearLayout button = new LinearLayout(getContext());
            button.setId(View.generateViewId());
            button.setOrientation(LinearLayout.HORIZONTAL);
            button.setGravity(Gravity.CENTER_VERTICAL);
            button.setPadding(dp(14), 0, dp(14), 0);
            button.setFocusable(true);
            button.setClickable(true);
            button.setBackground(ApolloUi.stateLayer(colors.surfaceContainerHigh, colors.secondaryContainer, dp(ApolloUi.ROW_RADIUS_DP)));

            ImageView icon = new ImageView(getContext());
            icon.setImageResource(category.iconRes);
            button.addView(icon, new LinearLayout.LayoutParams(dp(19), dp(19)));
            TextView label = ApolloUi.text(getContext(), category.title(getContext()), 13.5f, colors.onSurfaceVariant, true);
            label.setPadding(dp(12), 0, 0, 0);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            button.addView(label);

            // A category also closes the search
            button.setOnClickListener(v -> {
                if (searching()) {
                    exitSearch();
                }
                showCategory(index, true);
                // A on a gamepad goes on to the settings of the category, like right
                if (!v.isInTouchMode()) {
                    ArrayList<View> settings = rowList.getFocusables(View.FOCUS_FORWARD);
                    if (!settings.isEmpty()) {
                        settings.get(0).requestFocus();
                    }
                }
            });
            HintRow.set(button, KeyEvent.KEYCODE_BUTTON_A, R.string.apollo_hint_open, KeyEvent.KEYCODE_BUTTON_X, R.string.apollo_hint_search,
                    KeyEvent.KEYCODE_BUTTON_B, R.string.apollo_hint_home, KeyEvent.KEYCODE_BUTTON_START, R.string.apollo_hint_quick_settings);
            // The D-pad selects a category just by moving on it, like tabs
            button.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus && (index != selectedCategory || searching())) {
                    if (searching()) {
                        exitSearch();
                    }
                    showCategory(index, true);
                }
            });

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
            params.bottomMargin = dp(2);
            categoryList.addView(button, params);
            categoryViews.add(button);
        }
    }

    private void styleCategories() {
        for (int i = 0; i < categoryViews.size(); i++) {
            LinearLayout button = (LinearLayout) categoryViews.get(i);
            // None while searching: the results come from all of them
            boolean selected = i == selectedCategory && !searching();
            if (button.isActivated() == selected && button.getTag() != null) {
                continue;
            }
            // The selected pill and its text color fade from the old category to the new one
            button.setActivated(selected);
            ImageView icon = (ImageView) button.getChildAt(0);
            TextView label = (TextView) button.getChildAt(1);
            int from = label.getCurrentTextColor();
            int to = selected ? colors.onSecondaryContainer : colors.onSurfaceVariant;
            if (button.getTag() instanceof ValueAnimator) {
                ((ValueAnimator) button.getTag()).cancel();
            }
            ValueAnimator animator = ValueAnimator.ofObject(new ArgbEvaluator(), from, to);
            animator.setDuration(ApolloMotion.MEDIUM);
            animator.setInterpolator(ApolloMotion.STANDARD);
            animator.addUpdateListener(a -> {
                int color = (int) a.getAnimatedValue();
                icon.setImageTintList(ColorStateList.valueOf(color));
                label.setTextColor(color);
            });
            button.setTag(animator);
            animator.start();
        }
    }

    private void showCategory(int index, boolean animate) {
        if (index < 0 || index >= shownCategories.size()) {
            return;
        }
        boolean changed = index != selectedCategory;
        selectedCategory = index;
        styleCategories();
        buildRows(shownCategories.get(index));
        if (changed) {
            rowScroll.scrollTo(0, 0);
        }
        if (animate && changed) {
            rowList.setAlpha(0f);
            rowList.setTranslationY(dp(12));
            rowList.animate().alpha(1f).translationY(0).setDuration(ApolloMotion.MEDIUM)
                    .setInterpolator(ApolloMotion.EMPHASIZED_DECELERATE).start();
        }
    }

    // --- Rows

    private void buildRows(SettingsLayout.Category category) {
        rows.clear();
        rowList.removeAllViews();

        TextView header = ApolloUi.text(getContext(), category.title(getContext()), 18, colors.onSurface, true);
        header.setPadding(dp(14), dp(14), dp(14), dp(6));
        rowList.addView(header);

        for (Object entry : category.entries) {
            if (entry instanceof SettingsLayout.Item) {
                Preference pref = find((SettingsLayout.Item) entry);
                if (pref != null) {
                    addRow(pref, null, -1, null);
                }
                continue;
            }

            SettingsLayout.Section section = (SettingsLayout.Section) entry;
            List<Preference> prefs = new ArrayList<>();
            for (SettingsLayout.Item item : section.items) {
                Preference pref = find(item);
                if (pref != null) {
                    prefs.add(pref);
                }
            }
            if (prefs.isEmpty()) {
                continue;
            }

            String sectionKey = category.titleRes + "/" + section.titleRes;
            boolean open = section.openByDefault != sectionsToggled.contains(sectionKey);
            List<View> sectionRows = new ArrayList<>();
            View sectionHeader = sectionHeader(getContext().getString(section.titleRes), open, sectionKey, sectionRows);
            rowList.addView(sectionHeader);
            for (Preference pref : prefs) {
                View row = addRow(pref, null, -1, null);
                row.setVisibility(open ? VISIBLE : GONE);
                sectionRows.add(row);
            }
        }

        restoreRow = defaults != null ? addRestoreRow(category) : null;

        bindRows(false);
        linkFocus();
    }

    // At the end of the category, shown while some of its settings differ from the defaults
    private View addRestoreRow(SettingsLayout.Category category) {
        LinearLayout view = new LinearLayout(getContext());
        view.setId(View.generateViewId());
        view.setOrientation(LinearLayout.HORIZONTAL);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setMinimumHeight(dp(46));
        view.setPadding(dp(14), 0, dp(14), 0);
        view.setFocusable(true);
        view.setClickable(true);
        view.setBackground(ApolloUi.stateLayer(colors.surfaceContainerHigh, Color.TRANSPARENT, dp(ApolloUi.ROW_RADIUS_DP)));

        ImageView icon = new ImageView(getContext());
        icon.setImageResource(R.drawable.ic_apollo_restore);
        icon.setImageTintList(ColorStateList.valueOf(colors.primary));
        view.addView(icon, new LinearLayout.LayoutParams(dp(20), dp(20)));
        TextView label = ApolloUi.text(getContext(), getContext().getString(R.string.apollo_settings_restore), 14, colors.primary, true);
        label.setPadding(dp(12), 0, 0, 0);
        view.addView(label);

        view.setOnClickListener(v -> confirmRestore(category));
        HintRow.set(view, KeyEvent.KEYCODE_BUTTON_A, R.string.apollo_hint_select, KeyEvent.KEYCODE_BUTTON_B, backHint());

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(8);
        rowList.addView(view, params);
        return view;
    }

    private List<Preference> modifiedPrefs(SettingsLayout.Category category) {
        List<Preference> modified = new ArrayList<>();
        for (Object entry : category.entries) {
            List<SettingsLayout.Item> items = entry instanceof SettingsLayout.Section
                    ? ((SettingsLayout.Section) entry).items : java.util.Collections.singletonList((SettingsLayout.Item) entry);
            for (SettingsLayout.Item item : items) {
                Preference pref = find(item);
                if (pref != null && defaults.isModified(pref)) {
                    modified.add(pref);
                }
            }
        }
        return modified;
    }

    private void confirmRestore(SettingsLayout.Category category) {
        new AlertDialog.Builder(activity)
                .setTitle(getContext().getString(R.string.apollo_settings_restore_title, category.title(getContext())))
                .setMessage(R.string.apollo_settings_restore_message)
                .setNegativeButton(R.string.apollo_settings_restore_cancel, null)
                .setPositiveButton(R.string.apollo_settings_restore_confirm, (dialog, which) -> restore(category))
                .show();
    }

    // Puts the changed settings back the way the original list would set them, change listeners included,
    // so restoring the resolution also restores the bitrate that follows it
    private void restore(SettingsLayout.Category category) {
        for (Preference pref : modifiedPrefs(category)) {
            // A setting restored before may have already brought this one back
            Object def = defaults.defaultOf(pref);
            if (def == null || !defaults.isModified(pref)) {
                continue;
            }
            if (pref instanceof SeekBarPreference) {
                ((SeekBarPreference) pref).applyValue((Integer) def);
            } else if (pref instanceof TwoStatePreference) {
                boolean value = (Boolean) def;
                if (callChangeListener(pref, value)) {
                    ((TwoStatePreference) pref).setChecked(value);
                }
            } else if (pref instanceof ListPreference) {
                String value = def.toString();
                if (callChangeListener(pref, value)) {
                    ((ListPreference) pref).setValue(value);
                }
            }
        }
        bindRows(true);
        if (restoreRow != null && restoreRow.hasFocus() && !rows.isEmpty()) {
            // The row goes away: the focus moves to the last setting
            rows.get(rows.size() - 1).view.requestFocus();
        }
    }

    private static void fadeTo(View view, boolean shown, boolean animate) {
        float alpha = shown ? 1f : 0f;
        if (view.getAlpha() == alpha) {
            return;
        }
        view.animate().cancel();
        if (animate) {
            view.animate().alpha(alpha).setDuration(ApolloMotion.MEDIUM).setInterpolator(ApolloMotion.STANDARD).start();
        } else {
            view.setAlpha(alpha);
        }
    }

    // Marks the changed settings and their categories, and shows the restore row when there is something to restore
    private void bindModified(boolean animate) {
        if (defaults == null) {
            return;
        }
        for (Row row : rows) {
            boolean modified = defaults.isModified(row.pref);
            if (row.dot == null || (modified == row.modified && animate)) {
                continue;
            }
            row.modified = modified;
            int to = modified ? 255 : 0;
            if (!animate) {
                row.dot.setAlpha(to);
                row.label.invalidate();
                continue;
            }
            ValueAnimator fade = ValueAnimator.ofInt(row.dot.getAlpha(), to);
            fade.setDuration(ApolloMotion.MEDIUM);
            fade.setInterpolator(ApolloMotion.STANDARD);
            fade.addUpdateListener(a -> {
                row.dot.setAlpha((int) a.getAnimatedValue());
                row.label.invalidate();
            });
            fade.start();
        }

        if (restoreRow != null && selectedCategory < shownCategories.size()) {
            boolean show = !modifiedPrefs(shownCategories.get(selectedCategory)).isEmpty();
            if (show && restoreRow.getVisibility() != VISIBLE) {
                restoreRow.setVisibility(VISIBLE);
                restoreRow.setAlpha(0f);
                fadeTo(restoreRow, true, animate);
            } else if (!show) {
                restoreRow.setVisibility(GONE);
            }
        }
    }

    private View sectionHeader(String label, boolean open, String sectionKey, List<View> sectionRows) {
        LinearLayout header = new LinearLayout(getContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), 0, dp(10), 0);
        header.setFocusable(true);
        header.setClickable(true);
        header.setBackground(ApolloUi.stateLayer(colors.surfaceContainerHigh, Color.TRANSPARENT, dp(ApolloUi.ROW_RADIUS_DP)));

        TextView text = ApolloUi.text(getContext(), label.toUpperCase(), 12, colors.primary, true);
        text.setLetterSpacing(0.08f);
        header.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        ImageView chevron = new ImageView(getContext());
        chevron.setImageResource(R.drawable.ic_apollo_expand);
        chevron.setImageTintList(ColorStateList.valueOf(colors.primary));
        chevron.setRotation(open ? 180 : 0);
        header.addView(chevron, new LinearLayout.LayoutParams(dp(20), dp(20)));

        HintRow.set(header, KeyEvent.KEYCODE_BUTTON_A, R.string.apollo_hint_open, KeyEvent.KEYCODE_BUTTON_B, backHint());
        header.setOnClickListener(v -> {
            boolean nowOpen = sectionRows.isEmpty() || sectionRows.get(0).getVisibility() != VISIBLE;
            if (!sectionsToggled.remove(sectionKey)) {
                sectionsToggled.add(sectionKey);
            }
            chevron.animate().rotation(nowOpen ? 180 : 0).setDuration(ApolloMotion.MEDIUM)
                    .setInterpolator(ApolloMotion.STANDARD).start();
            for (View row : sectionRows) {
                if (nowOpen) {
                    row.setVisibility(VISIBLE);
                    row.setAlpha(0f);
                    row.animate().alpha(1f).setDuration(ApolloMotion.MEDIUM)
                            .setInterpolator(ApolloMotion.EMPHASIZED_DECELERATE).start();
                } else {
                    row.setVisibility(GONE);
                }
            }
            linkFocus();
        });

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
        params.topMargin = dp(6);
        header.setLayoutParams(params);
        return header;
    }

    /**
     * @param path in the search results, the category (and section) of the setting, over its name
     * @param titleMatch in the search results, where the query starts in the name, or -1
     * @param snippet in the search results, the words of the explanation that hold the query, under the name
     */
    private View addRow(Preference pref, String path, int titleMatch, CharSequence snippet) {
        Row row = new Row();
        row.pref = pref;
        row.info = pref.getKey() != null ? SettingsLayout.INFO.get(pref.getKey()) : null;

        LinearLayout view = new LinearLayout(getContext()) {
            // A touch on the "i" (a 48 dp square around it) opens the explanation instead of the setting,
            // also on a setting that is turned off
            private boolean onInfo;

            @Override
            public boolean dispatchTouchEvent(MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    onInfo = isOnInfo(row, event.getX(), event.getY());
                }
                if (!onInfo) {
                    return super.dispatchTouchEvent(event);
                }
                if (event.getActionMasked() == MotionEvent.ACTION_UP && isOnInfo(row, event.getX(), event.getY())) {
                    showInfo(row);
                }
                if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    onInfo = false;
                }
                return true;
            }
        };
        view.setId(View.generateViewId());
        view.setOrientation(LinearLayout.HORIZONTAL);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setMinimumHeight(dp(46));
        view.setPadding(dp(14), 0, dp(14), 0);
        view.setFocusable(true);
        view.setClickable(true);
        view.setBackground(ApolloUi.stateLayer(colors.surfaceContainerHigh, Color.TRANSPARENT, dp(ApolloUi.ROW_RADIUS_DP)));
        row.view = view;

        row.label = ApolloUi.text(getContext(), title(pref), 14, colors.onSurface, true);
        row.label.setPadding(0, dp(6), dp(12), dp(6));
        row.label.setLineSpacing(0, 1.15f);
        if (defaults != null || row.info != null || path != null) {
            SpannableStringBuilder text = new SpannableStringBuilder();
            if (path != null) {
                text.append(path);
                text.setSpan(new AbsoluteSizeSpan(12, true), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                text.setSpan(new ForegroundColorSpan(colors.outline), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                text.append('\n');
            }
            int titleStart = text.length();
            text.append(title(pref));
            if (titleMatch >= 0) {
                SettingsSearch.mark(text, titleStart + titleMatch, query.length(), colors.primary);
            }
            // The "i" and the dot are part of the text, so they stay right after the last word when the name wraps;
            // a no-break space keeps them on the line of that word
            if (row.info != null) {
                row.infoSpan = new InfoSpan(colors.surfaceContainerHighest, colors.onSurfaceVariant, dp(16), dp(6));
                text.append(' ');
                text.setSpan(row.infoSpan, text.length() - 1, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (defaults != null) {
                row.dot = new DotSpan(colors.primary, dp(4), dp(5));
                text.append(' ');
                text.setSpan(row.dot, text.length() - 1, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (snippet != null) {
                text.append('\n');
                int snippetStart = text.length();
                text.append(snippet);
                text.setSpan(new AbsoluteSizeSpan(12, true), snippetStart, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            row.label.setText(text);
        }
        view.addView(row.label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        if (pref instanceof TwoStatePreference) {
            row.toggle = new ApolloWidgets.SwitchView(getContext());
            row.toggle.setColors(colors.primary, colors.onPrimary, colors.surfaceContainerHighest, colors.outline);
            view.addView(row.toggle);
            view.setOnClickListener(v -> performClick(pref));
            HintRow.set(view, KeyEvent.KEYCODE_BUTTON_A, R.string.apollo_hint_change, KeyEvent.KEYCODE_BUTTON_B, backHint());
        }
        else if (pref instanceof SeekBarPreference) {
            SeekBarPreference seekBar = (SeekBarPreference) pref;
            row.slider = new SliderView(getContext(), colors);
            row.slider.setRange(seekBar.getMinValue(), seekBar.getMaxValue(), seekBar.getStepSize());
            view.addView(row.slider);
            row.value = ApolloUi.text(getContext(), "", 13, colors.onSurface, true);
            row.value.setGravity(Gravity.END);
            view.addView(row.value, new LinearLayout.LayoutParams(dp(78), ViewGroup.LayoutParams.WRAP_CONTENT));

            row.slider.setListener(new SliderView.Listener() {
                @Override
                public void onSliderMoved(int value) {
                    row.value.setText(seekBar.formatValue(value));
                }

                @Override
                public void onSliderReleased(int value) {
                    row.sliderValue = value;
                    seekBar.applyValue(value);
                }
            });
            // Left and right move the slider while its row has the focus
            view.setOnKeyListener((v, keyCode, event) -> {
                if (keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) {
                    return false;
                }
                if (event.getAction() == KeyEvent.ACTION_DOWN && pref.isEnabled()) {
                    // Holding the D-pad speeds up, so the 500 steps of the bitrate stay quick to cross
                    int step = seekBar.getKeyStepSize() * sliderSpeed(event) * (keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
                    int value = Math.max(seekBar.getMinValue(), Math.min(seekBar.getMaxValue(), row.sliderValue + step));
                    if (value != row.sliderValue) {
                        row.sliderValue = value;
                        row.slider.setValue(value);
                        row.value.setText(seekBar.formatValue(value));
                        seekBar.applyValue(value);
                    }
                }
                return true;
            });
            // The slider is set right in the row, so the original dialog is not needed any more
            view.setClickable(false);
            HintRow.set(view, ButtonGlyph.DPAD_LEFT_RIGHT, R.string.apollo_hint_adjust, KeyEvent.KEYCODE_BUTTON_B, backHint());
        }
        else if (pref instanceof ListPreference && !(pref instanceof LanguagePreference)) {
            row.value = valueChip();
            view.addView(row.value);
            view.setOnClickListener(v -> showOptions(row));
            HintRow.set(view, KeyEvent.KEYCODE_BUTTON_A, R.string.apollo_hint_change, KeyEvent.KEYCODE_BUTTON_B, backHint());
        }
        else {
            // Language, text fields, dialogs and links: the original behavior
            row.value = ApolloUi.text(getContext(), "", 13, colors.onSurfaceVariant, false);
            row.value.setMaxWidth(dp(220));
            row.value.setSingleLine(true);
            row.value.setEllipsize(TextUtils.TruncateAt.END);
            view.addView(row.value);
            ImageView chevron = new ImageView(getContext());
            chevron.setImageResource(R.drawable.ic_apollo_chevron_right);
            chevron.setImageTintList(ColorStateList.valueOf(colors.onSurfaceVariant));
            view.addView(chevron, new LinearLayout.LayoutParams(dp(18), dp(18)));
            view.setOnClickListener(v -> performClick(pref));
            HintRow.set(view, KeyEvent.KEYCODE_BUTTON_A, R.string.apollo_hint_open, KeyEvent.KEYCODE_BUTTON_B, backHint());
        }

        if (row.info != null) {
            insertHint(view, KeyEvent.KEYCODE_BUTTON_Y, R.string.apollo_hint_info);
        }
        if (searchField != null) {
            insertHint(view, KeyEvent.KEYCODE_BUTTON_X, R.string.apollo_hint_search);
        }

        rows.add(row);
        rowList.addView(view, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return view;
    }

    // "Y Info" and "X Search" go before the last hint of a row, which is always B
    @SuppressWarnings("unchecked")
    private void insertHint(View view, int key, int labelRes) {
        Object tag = view.getTag(R.id.apollo_hints);
        List<HintRow.Hint> hints = tag instanceof List ? new ArrayList<>((List<HintRow.Hint>) tag) : new ArrayList<>();
        hints.add(Math.max(0, hints.size() - 1), new HintRow.Hint(key, getContext().getString(labelRes)));
        HintRow.set(view, hints.toArray(new HintRow.Hint[0]));
    }

    private boolean isOnInfo(Row row, float x, float y) {
        if (row.infoSpan == null) {
            return false;
        }
        float[] center = row.infoSpan.center(row.label);
        if (center == null) {
            return false;
        }
        float half = dp(24);
        float cx = row.label.getLeft() + center[0], cy = row.label.getTop() + center[1];
        return Math.abs(x - cx) <= half && Math.abs(y - cy) <= half;
    }

    private void showInfo(Row row) {
        if (row.info == null) {
            return;
        }
        popup.dismiss(false);
        List<InfoPopup.Option> options = null;
        SettingsLayout.Info info = row.info;
        if (info.optionValues != 0 && row.pref instanceof ListPreference) {
            ListPreference list = (ListPreference) row.pref;
            String[] values = getResources().getStringArray(info.optionValues);
            String[] names = getResources().getStringArray(info.optionNames);
            String[] texts = getResources().getStringArray(info.optionTexts);
            options = new ArrayList<>();
            for (int i = 0; i < values.length && i < names.length && i < texts.length; i++) {
                // Only the options this device offers
                if (list.findIndexOfValue(values[i]) >= 0) {
                    options.add(new InfoPopup.Option(names[i], texts[i], values[i].equals(list.getValue())));
                }
            }
        }
        infoPopup.show(row.view, title(row.pref), getContext().getString(info.text), options);
    }

    // Y on a setting with an "i" opens its explanation, and closes it again; X goes to the search from anywhere
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_X && searchField != null
                && !infoPopup.isShowing() && !popup.isShowing()) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                searchField.openKeyboard();
            }
            return true;
        }
        if (event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_Y) {
            if (infoPopup.isShowing()) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                    infoPopup.dismiss(true);
                }
                return true;
            }
            for (Row row : rows) {
                if (row.info != null && row.view.isFocused()) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                        showInfo(row);
                    }
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    // B leaves a single page, or goes back to the categories
    private int backHint() {
        if (searching()) {
            return R.string.apollo_hint_close_search;
        }
        return singlePage ? R.string.apollo_hint_back : R.string.apollo_hint_categories;
    }

    // Steps per D-pad press: one, then more while the button is held down
    static int sliderSpeed(KeyEvent event) {
        int repeats = event.getRepeatCount();
        return repeats >= 25 ? 10 : repeats >= 8 ? 5 : 1;
    }

    private TextView valueChip() {
        TextView chip = ApolloUi.text(getContext(), "", 13, colors.onSurface, true);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setSingleLine(true);
        chip.setEllipsize(TextUtils.TruncateAt.END);
        chip.setMaxWidth(dp(260));
        chip.setMinHeight(dp(30));
        chip.setPadding(dp(12), 0, dp(6), 0);
        chip.setBackground(ApolloUi.roundRect(colors.surfaceContainerHighest, dp(8)));
        chip.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_apollo_expand, 0);
        chip.setCompoundDrawableTintList(ColorStateList.valueOf(colors.onSurfaceVariant));
        chip.setCompoundDrawablePadding(dp(2));
        return chip;
    }

    private void showOptions(Row row) {
        ListPreference list = (ListPreference) row.pref;
        CharSequence[] options = new CharSequence[list.getEntries().length];
        for (int i = 0; i < options.length; i++) {
            options[i] = optionLabel(list, i);
        }
        int rightEdge = getWidth() - content.getPaddingRight() - dp(14);
        popup.show(row.view, rightEdge, options, list.findIndexOfValue(list.getValue()), index -> {
            String value = list.getEntryValues()[index].toString();
            if (!value.equals(list.getValue()) && callChangeListener(list, value)) {
                list.setValue(value);
            }
            bindRows(true);
        });
    }

    private void bindRows(boolean animate) {
        if (screen == null) {
            return;
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        for (Row row : rows) {
            Preference pref = row.pref;
            boolean enabled = pref.isEnabled();
            row.view.setAlpha(enabled ? 1f : 0.38f);
            row.view.setEnabled(enabled);
            row.view.setFocusable(enabled);

            if (row.toggle != null) {
                row.toggle.setChecked(((TwoStatePreference) pref).isChecked(), animate);
            }
            else if (row.slider != null) {
                SeekBarPreference seekBar = (SeekBarPreference) pref;
                // Read from the stored settings: the bitrate is rewritten when the resolution or frame rate change
                row.sliderValue = prefs.getInt(pref.getKey(), seekBar.getDefaultValue());
                row.slider.setEnabled(enabled);
                row.slider.setValue(row.sliderValue);
                row.value.setText(seekBar.formatValue(row.sliderValue));
            }
            else if (pref instanceof ListPreference && !(pref instanceof LanguagePreference)) {
                ListPreference list = (ListPreference) pref;
                int index = list.findIndexOfValue(list.getValue());
                row.value.setText(index >= 0 ? optionLabel(list, index) : "");
            }
            else if (pref instanceof ListPreference) {
                CharSequence entry = ((ListPreference) pref).getEntry();
                row.value.setText(entry != null ? entry : "");
            }
            else if (singlePage && row.value != null) {
                // A game page shows what each setting is set to, as its summary tells
                CharSequence summary = pref.getSummary();
                row.value.setText(summary == null ? "" : "Not set".contentEquals(summary.toString())
                        ? getContext().getString(R.string.apollo_not_set) : summary);
            }
            else if (pref instanceof EditTextPreference) {
                String text = ((EditTextPreference) pref).getText();
                row.value.setText(text != null ? text : "");
            }
        }

        bindModified(animate);

        // A setting that turned on or off changes where the list starts and ends
        linkFocus();
    }

    /**
     * D-pad paths, so the focus never wanders to the other column by itself:
     * the categories wrap around from the last to the first, the settings stop at their ends,
     * left from a setting goes back to its category and right from a category goes to the first setting.
     */
    private void linkFocus() {
        if (categoryViews.isEmpty()) {
            return;
        }
        View category = categoryViews.get(Math.max(0, selectedCategory));
        // Left from the search results goes back to the search box
        View left = searching() && searchField != null ? searchField.edit : category;
        View first = null, last = null;
        for (int i = 0; i < rowList.getChildCount(); i++) {
            View child = rowList.getChildAt(i);
            if (child.isFocusable() && child.getVisibility() == VISIBLE) {
                if (child.getId() == View.NO_ID) {
                    child.setId(View.generateViewId());
                }
                child.setNextFocusLeftId(singlePage ? child.getId() : left.getId());
                child.setNextFocusRightId(child.getId());
                child.setNextFocusUpId(View.NO_ID);
                child.setNextFocusDownId(View.NO_ID);
                if (first == null) {
                    first = child;
                }
                last = child;
            }
        }
        if (first != null) {
            first.setNextFocusUpId(first.getId());
            last.setNextFocusDownId(last.getId());
        }

        int count = categoryViews.size();
        for (int i = 0; i < count; i++) {
            View view = categoryViews.get(i);
            view.setNextFocusRightId(first != null ? first.getId() : view.getId());
            view.setNextFocusLeftId(view.getId());
            view.setNextFocusUpId(categoryViews.get((i - 1 + count) % count).getId());
            view.setNextFocusDownId(categoryViews.get((i + 1) % count).getId());
        }

        // The search box sits over the first category; down from it goes to the results while there are some
        if (searchField != null) {
            View edit = searchField.edit;
            categoryViews.get(0).setNextFocusUpId(edit.getId());
            edit.setNextFocusUpId(edit.getId());
            edit.setNextFocusLeftId(edit.getId());
            edit.setNextFocusRightId(first != null ? first.getId() : edit.getId());
            edit.setNextFocusDownId(searching() && first != null ? first.getId() : categoryViews.get(0).getId());
        }
    }
}
