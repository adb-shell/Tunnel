package com.tunnel.adbhelper;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;

/** Bounded semantic-label layout; shaping stays intact when digits change color. */
final class HierarchyText {
    private HierarchyText() { }

    static CharSequence coloredDigits(String text) {
        SpannableString styled = new SpannableString(text);
        int run = -1;
        for (int offset = 0; offset < text.length();) {
            int cp = text.codePointAt(offset);
            if (Character.isDigit(cp)) {
                if (run < 0) run = offset;
            } else if (run >= 0) {
                styled.setSpan(new ForegroundColorSpan(Color.RED), run, offset, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                run = -1;
            }
            offset += Character.charCount(cp);
        }
        if (run >= 0) styled.setSpan(new ForegroundColorSpan(Color.RED), run, text.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return styled;
    }

    static void draw(Canvas canvas, String text, Rect bounds, float scale, Typeface typeface) {
        if (typeface == null || text.isEmpty() || bounds.isEmpty()) return;
        float padding = Math.min(2f / scale, Math.min(bounds.width(), bounds.height()) / 8f);
        int width = Math.max(1, (int) (bounds.width() - padding * 2));
        float height = Math.max(1f, bounds.height() - padding * 2);
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        // Assign before EVERY measurement; the shell's global DEFAULT may still
        // be absent when the explicit installed-font fallback was used.
        paint.setTypeface(typeface);
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(Math.min(14f / scale, height * .75f));
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float fontHeight = metrics.descent - metrics.ascent;
        if (fontHeight > height) paint.setTextSize(paint.getTextSize() * height / fontHeight);
        fontHeight = Math.max(1f, paint.descent() - paint.ascent());
        int maxLines = Math.max(1, Math.min(8, (int) (height / fontHeight)));
        paint.setShadowLayer(1.5f / scale, 0, 0, Color.BLACK);
        CharSequence styled = coloredDigits(text);
        StaticLayout layout;
        try {
            layout = StaticLayout.Builder.obtain(styled, 0, styled.length(), paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
                .setIncludePad(false).setUseLineSpacingFromFallbacks(true)
                .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
                .setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END).build();
        } catch (RuntimeException unsupportedLayout) {
            // Match the accessibility renderer's basic Canvas path if a vendor's
            // text layout stack is unavailable in app_process. No null Typeface.
            drawSimple(canvas, text, bounds, padding, paint);
            return;
        }
        int save = canvas.save();
        try {
            canvas.clipRect(bounds);
            canvas.translate(bounds.left + padding,
                    bounds.top + padding + Math.max(0, (height - layout.getHeight()) / 2f));
            layout.draw(canvas);
        } finally { canvas.restoreToCount(save); }
    }

    private static void drawSimple(Canvas canvas, String text, Rect bounds, float padding, TextPaint paint) {
        int save = canvas.save();
        try {
            canvas.clipRect(bounds);
            float left = bounds.left + padding, x = left;
            float y = bounds.top + padding - paint.ascent();
            float lineHeight = Math.max(1f, paint.descent() - paint.ascent());
            int lines = 1;
            for (int start = 0; start < text.length();) {
                int cp = text.codePointAt(start), end = start + Character.charCount(cp);
                float advance = paint.measureText(text, start, end);
                if (cp == '\n' || (x > left && x + advance > bounds.right - padding)) {
                    x = left; y += lineHeight;
                    if (++lines > 8 || y + paint.descent() > bounds.bottom) break;
                }
                if (cp != '\n') {
                    paint.setColor(Character.isDigit(cp) ? Color.RED : Color.WHITE);
                    canvas.drawText(text, start, end, x, y, paint);
                    x += advance;
                }
                start = end;
            }
        } finally { canvas.restoreToCount(save); }
    }
}
