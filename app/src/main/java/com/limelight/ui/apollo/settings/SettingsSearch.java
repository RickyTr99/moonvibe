package com.limelight.ui.apollo.settings;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Text matching for the settings search: case and accents don't count ("modalita" finds "Modalità"),
 * and folding keeps every character in place, so a match can be marked in the original text.
 */
final class SettingsSearch {
    // Shown around a match found in an explanation
    private static final int SNIPPET_BEFORE = 28;
    private static final int SNIPPET_AFTER = 70;

    private SettingsSearch() {
    }

    static String fold(CharSequence text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 128) {
                out.append(Character.toLowerCase(c));
                continue;
            }
            String base = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD);
            out.append(base.isEmpty() ? c : base.substring(0, 1).toLowerCase(Locale.ROOT).charAt(0));
        }
        return out.toString();
    }

    /** Where the folded query starts in the text, or -1 */
    static int indexOf(CharSequence text, String foldedQuery) {
        if (text == null || foldedQuery.isEmpty()) {
            return -1;
        }
        return fold(text).indexOf(foldedQuery);
    }

    /** Colors a match in place */
    static void mark(SpannableStringBuilder text, int start, int length, int color) {
        text.setSpan(new ForegroundColorSpan(color), start, start + length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), start, start + length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    /** A piece of the text around a match, cut at spaces, with the match colored */
    static SpannableStringBuilder snippet(String text, int at, int length, int textColor, int matchColor) {
        int start = Math.max(0, at - SNIPPET_BEFORE);
        int end = Math.min(text.length(), at + length + SNIPPET_AFTER);
        if (start > 0) {
            int space = text.indexOf(' ', start);
            start = space >= 0 && space < at ? space + 1 : start;
        }
        if (end < text.length()) {
            int space = text.lastIndexOf(' ', end);
            end = space > at + length ? space : end;
        }
        SpannableStringBuilder out = new SpannableStringBuilder();
        if (start > 0) {
            out.append('…');
        }
        int offset = out.length() - start;
        out.append(text, start, end);
        if (end < text.length()) {
            out.append('…');
        }
        // The text color first, so the match color set after it wins
        out.setSpan(new ForegroundColorSpan(textColor), 0, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        mark(out, at + offset, length, matchColor);
        return out;
    }
}
