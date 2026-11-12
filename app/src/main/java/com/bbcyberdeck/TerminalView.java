package com.bbcyberdeck;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class TerminalView extends View {

    // Themes
    public static final int THEME_MATRIX_GREEN = 0;
    public static final int THEME_AMBER_CRT    = 1;
    public static final int THEME_CYBER_BLUE   = 2;
    public static final int THEME_CLASSIC_WHITE= 3;

    private int mTheme = THEME_MATRIX_GREEN;

    // Default colors
    private int mColorBg;
    private int mColorFg;
    private int mColorCursor;

    private Paint mTextPaint;
    private Paint mBgPaint;
    private Paint mCursorPaint;

    private float mCharWidth;
    private float mCharHeight;
    private float mCharDescent;

    private int mColumns = 80;
    private int mRows = 25;

    private int mCursorCol = 0;
    private int mCursorRow = 0;
    private boolean mCursorVisible = true;

    // Scrolling support
    private int mScrollOffset = 0; // 0 = at bottom (latest output)
    private float mLastTouchY;
    private boolean mIsDragging = false;

    // Screen buffer: lines of characters and their attributes
    private final List<StringBuilder> mLines = new ArrayList<StringBuilder>();
    private final List<int[]> mColors = new ArrayList<int[]>();
    private final int MAX_HISTORY = 2000;

    // ANSI escape parser state
    private boolean mInEscape = false;
    private final StringBuilder mEscapeBuffer = new StringBuilder();
    private int mCurrentColor = Color.GREEN;

    public TerminalView(Context context) {
        super(context);
        init();
    }

    public TerminalView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.setTypeface(Typeface.MONOSPACE);
        mTextPaint.setTextSize(22f); // Optimized for Q5 720x720 display

        mBgPaint = new Paint();
        mCursorPaint = new Paint();

        setTheme(THEME_MATRIX_GREEN);
        measureChar();
        clearScreen();
    }

    public void setTheme(int theme) {
        mTheme = theme;
        switch (theme) {
            case THEME_MATRIX_GREEN:
                mColorBg     = Color.rgb(10, 14, 20);
                mColorFg     = Color.rgb(0, 255, 102);   // Phosphor neon green
                mColorCursor = Color.rgb(0, 255, 102);
                break;
            case THEME_AMBER_CRT:
                mColorBg     = Color.rgb(18, 14, 6);
                mColorFg     = Color.rgb(255, 176, 0);   // Amber vintage CRT
                mColorCursor = Color.rgb(255, 176, 0);
                break;
            case THEME_CYBER_BLUE:
                mColorBg     = Color.rgb(8, 16, 26);
                mColorFg     = Color.rgb(0, 210, 255);   // Cyberdeck Cyan
                mColorCursor = Color.rgb(0, 210, 255);
                break;
            case THEME_CLASSIC_WHITE:
            default:
                mColorBg     = Color.rgb(13, 17, 23);
                mColorFg     = Color.rgb(230, 237, 243); // Clean terminal white
                mColorCursor = Color.rgb(230, 237, 243);
                break;
        }
        mCurrentColor = mColorFg;
        mBgPaint.setColor(mColorBg);
        mCursorPaint.setColor(mColorCursor);
        postInvalidate();
    }

    public int getTheme() {
        return mTheme;
    }

    public void cycleTheme() {
        setTheme((mTheme + 1) % 4);
    }

    private void measureChar() {
        mCharWidth = mTextPaint.measureText("X");
        Paint.FontMetrics fm = mTextPaint.getFontMetrics();
        mCharHeight = fm.bottom - fm.top;
        mCharDescent = fm.descent;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (mCharWidth > 0 && mCharHeight > 0) {
            mColumns = Math.max(20, (int) (w / mCharWidth));
            mRows = Math.max(10, (int) (h / mCharHeight));
        }
    }

    public synchronized void clearScreen() {
        mLines.clear();
        mColors.clear();
        mLines.add(new StringBuilder());
        mColors.add(new int[160]);
        mCursorCol = 0;
        mCursorRow = 0;
        mScrollOffset = 0;
        postInvalidate();
    }

    public void scrollToBottom() {
        if (mScrollOffset != 0) {
            mScrollOffset = 0;
            postInvalidate();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mLastTouchY = event.getY();
                mIsDragging = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                float deltaY = event.getY() - mLastTouchY;
                if (Math.abs(deltaY) > 6 && mCharHeight > 0) {
                    mIsDragging = true;
                    int lineDelta = (int) (deltaY / mCharHeight);
                    if (lineDelta != 0) {
                        mScrollOffset = Math.max(0, Math.min(mLines.size() - 5, mScrollOffset + lineDelta));
                        mLastTouchY = event.getY();
                        postInvalidate();
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (!mIsDragging) {
                    scrollToBottom();
                }
                return true;
        }
        return super.onTouchEvent(event);
    }

    public synchronized void appendOutput(byte[] buf, int offset, int length) {
        for (int i = offset; i < offset + length; i++) {
            char c = (char) (buf[i] & 0xFF);
            processChar(c);
        }
        postInvalidate();
    }

    private void processChar(char c) {
        if (mInEscape) {
            mEscapeBuffer.append(c);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) {
                handleAnsiSequence(mEscapeBuffer.toString());
                mInEscape = false;
                mEscapeBuffer.setLength(0);
            }
            return;
        }

        if (c == 0x1B) { // ESC
            mInEscape = true;
            mEscapeBuffer.setLength(0);
            return;
        }

        if (c == '\r') {
            mCursorCol = 0;
            return;
        }

        if (c == '\n') {
            newLine();
            return;
        }

        if (c == '\b') {
            if (mCursorCol > 0) mCursorCol--;
            return;
        }

        if (c == '\t') {
            mCursorCol = (mCursorCol + 8) & ~7;
            return;
        }

        if (c < 32 && c != 0) {
            return;
        }

        putChar(c);
    }

    private void putChar(char c) {
        ensureLine(mCursorRow);
        StringBuilder line = mLines.get(mCursorRow);
        int[] colColors = mColors.get(mCursorRow);

        while (line.length() < mCursorCol) {
            line.append(' ');
        }

        if (mCursorCol < line.length()) {
            line.setCharAt(mCursorCol, c);
        } else {
            line.append(c);
        }

        if (mCursorCol < colColors.length) {
            colColors[mCursorCol] = mCurrentColor;
        }

        mCursorCol++;
        if (mCursorCol >= mColumns) {
            newLine();
        }
    }

    private void newLine() {
        mCursorCol = 0;
        mCursorRow++;
        ensureLine(mCursorRow);

        while (mLines.size() > MAX_HISTORY) {
            mLines.remove(0);
            mColors.remove(0);
            if (mCursorRow > 0) mCursorRow--;
        }
    }

    private void ensureLine(int row) {
        while (mLines.size() <= row) {
            mLines.add(new StringBuilder());
            mColors.add(new int[160]);
        }
    }

    private void handleAnsiSequence(String seq) {
        if (seq.startsWith("[")) {
            String params = seq.substring(1, seq.length() - 1);
            char cmd = seq.charAt(seq.length() - 1);

            switch (cmd) {
                case 'J':
                    if ("2".equals(params) || params.isEmpty()) {
                        clearScreen();
                    }
                    break;
                case 'K':
                    if (mCursorRow < mLines.size()) {
                        StringBuilder line = mLines.get(mCursorRow);
                        if (mCursorCol < line.length()) {
                            line.setLength(mCursorCol);
                        }
                    }
                    break;
                case 'H':
                case 'f':
                    if (params.isEmpty()) {
                        mCursorCol = 0;
                        mCursorRow = 0;
                    } else {
                        String[] parts = params.split(";");
                        try {
                            int r = Integer.parseInt(parts[0]) - 1;
                            int c = parts.length > 1 ? Integer.parseInt(parts[1]) - 1 : 0;
                            mCursorRow = Math.max(0, r);
                            mCursorCol = Math.max(0, c);
                            ensureLine(mCursorRow);
                        } catch (Exception ignored) {}
                    }
                    break;
                case 'm':
                    parseSgr(params);
                    break;
            }
        }
    }

    private void parseSgr(String params) {
        if (params.isEmpty()) {
            mCurrentColor = mColorFg;
            return;
        }
        String[] codes = params.split(";");
        for (String codeStr : codes) {
            try {
                int code = Integer.parseInt(codeStr);
                if (code == 0) {
                    mCurrentColor = mColorFg;
                } else if (code == 30) {
                    mCurrentColor = Color.DKGRAY;
                } else if (code == 31) {
                    mCurrentColor = Color.rgb(255, 85, 85);   // Red
                } else if (code == 32) {
                    mCurrentColor = Color.rgb(80, 250, 123);  // Green
                } else if (code == 33) {
                    mCurrentColor = Color.rgb(241, 250, 140); // Yellow
                } else if (code == 34) {
                    mCurrentColor = Color.rgb(98, 114, 164);  // Blue
                } else if (code == 35) {
                    mCurrentColor = Color.rgb(255, 121, 198); // Magenta
                } else if (code == 36) {
                    mCurrentColor = Color.rgb(139, 233, 253); // Cyan
                } else if (code == 37) {
                    mCurrentColor = Color.WHITE;
                } else if (code == 39) {
                    mCurrentColor = mColorFg;
                }
            } catch (Exception ignored) {}
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(mColorBg);

        int totalLines = mLines.size();
        int visibleRows = Math.min(mRows, (int) (getHeight() / mCharHeight));
        int bottomLine = Math.max(0, totalLines - mScrollOffset);
        int startLine = Math.max(0, bottomLine - visibleRows);

        float y = mCharHeight - mCharDescent;

        synchronized (this) {
            for (int i = startLine; i < bottomLine; i++) {
                StringBuilder line = mLines.get(i);
                int[] colColors = mColors.get(i);
                int len = line.length();

                for (int j = 0; j < len; j++) {
                    char c = line.charAt(j);
                    int color = (j < colColors.length && colColors[j] != 0) ? colColors[j] : mColorFg;
                    mTextPaint.setColor(color);
                    canvas.drawText(String.valueOf(c), j * mCharWidth, y, mTextPaint);
                }
                y += mCharHeight;
            }

            // Draw cursor (only when viewing bottom of screen)
            if (mCursorVisible && mScrollOffset == 0) {
                int screenCursorRow = mCursorRow - startLine;
                if (screenCursorRow >= 0 && screenCursorRow < visibleRows) {
                    float curX = mCursorCol * mCharWidth;
                    float curY = (screenCursorRow * mCharHeight);
                    mCursorPaint.setColor(mColorCursor);
                    mCursorPaint.setAlpha(180);
                    canvas.drawRect(curX, curY, curX + mCharWidth, curY + mCharHeight, mCursorPaint);
                }
            }
        }
    }
}
