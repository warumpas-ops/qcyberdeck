package com.bbcyberdeck;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class CyberdeckActivity extends Activity {

    private TerminalView mTerminalView;
    private TerminalSession mSession;

    private LinearLayout mLayoutInstalling;
    private TextView mTvInstallStatus;

    // Gesture detector for minimalist controls (double tap to cycle theme, long press to reset)
    private GestureDetector mGestureDetector;

    // Line buffer and command history for seamless physical keyboard terminal typing
    private final StringBuilder mInputLine = new StringBuilder();
    private final List<String> mHistory = new ArrayList<String>();
    private int mHistoryIndex = -1;

    // Animation running flag
    private volatile boolean mIsAnimating = false;

    // BlackBerry Q5 Hardware Ctrl modifier (Volume Down = instant Ctrl+C)
    private volatile boolean mVolumeDownHeld = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Pure fullscreen - no title, no status bar clutter
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        setContentView(R.layout.activity_cyberdeck);

        mTerminalView = (TerminalView) findViewById(R.id.terminal_view);
        mLayoutInstalling = (LinearLayout) findViewById(R.id.layout_installing);
        mTvInstallStatus = (TextView) findViewById(R.id.tv_install_status);

        // Wire InputConnection → terminal line editor so BB10 IME delivers text directly
        mTerminalView.setInputConnectionListener(new TerminalView.InputConnectionListener() {
            @Override
            public void onTextInput(String text) {
                for (int i = 0; i < text.length(); i++) {
                    char c = text.charAt(i);
                    if (c == '\n' || c == '\r') {
                        onEnter();
                    } else if (c == 0x08 || c == 0x7f) {
                        onBackspace();
                    } else if (c >= 1 && c <= 26) {
                        // Ctrl code from hardware keyboard or IME
                        if (c == 3) onCtrlC();
                        else if (c == 12) mTerminalView.clearScreen();
                        else if (c == 21) replaceInputLine("");
                        else if (mSession != null) mSession.write(new byte[]{(byte) c});
                    } else if (c >= 32) {
                        onCharInput(c);
                    }
                }
            }
            @Override
            public void onBackspace() {
                CyberdeckActivity.this.onBackspace();
            }
        });

        setupGestures();

        initSession();
        checkAndStart();
    }

    private void setupGestures() {
        mGestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                mTerminalView.cycleTheme();
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                restartSession(false);
            }
        });

        mTerminalView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                mGestureDetector.onTouchEvent(event);
                return false; // Allow TerminalView touch scroll to work normally
            }
        });
    }

    private void initSession() {
        mSession = new TerminalSession(this);
        mSession.setOutputListener(new TerminalSession.OutputListener() {
            @Override
            public void onOutput(final byte[] buffer, final int offset, final int length) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mTerminalView.appendOutput(buffer, offset, length);
                    }
                });
            }

            @Override
            public void onProcessExited(final int exitCode) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        String msg = "\r\n\r\n\u001B[1;31m[Process exited with code " + exitCode + "]\u001B[0m\r\n"
                                + "\u001B[1;33mLong-press screen to restart.\u001B[0m\r\n";
                        byte[] bytes = msg.getBytes();
                        mTerminalView.appendOutput(bytes, 0, bytes.length);
                    }
                });
            }
        });
    }

    private void restartSession(boolean reinstall) {
        mIsAnimating = false;
        if (mSession != null) {
            mSession.kill();
        }
        mInputLine.setLength(0);
        mTerminalView.clearScreen();
        if (reinstall) {
            File marker = new File(getFilesDir(), ".installed_v13");
            marker.delete();
            checkAndStart();
            Toast.makeText(this, "Reinstalling...", Toast.LENGTH_SHORT).show();
        } else {
            initSession();
            mSession.start();
            Toast.makeText(this, "Restarted", Toast.LENGTH_SHORT).show();
        }
    }

    // --- Line Editing & Commands Execution ---

    private void onCharInput(char c) {
        if (mIsAnimating) {
            mIsAnimating = false;
            return;
        }
        mInputLine.append(c);
        mTerminalView.scrollToBottom();
        mTerminalView.appendOutput(new byte[]{(byte) c}, 0, 1);
    }

    private void onBackspace() {
        if (mInputLine.length() > 0) {
            mInputLine.deleteCharAt(mInputLine.length() - 1);
            mTerminalView.scrollToBottom();
            mTerminalView.appendOutput(new byte[]{'\b', ' ', '\b'}, 0, 3);
        }
    }

    private void printPrompt() {
        byte[] p = "\u001B[1;32mroot@q5\u001B[0m:\u001B[1;34m~#\u001B[0m ".getBytes();
        mTerminalView.appendOutput(p, 0, p.length);
    }

    private static String getPadding(int n) {
        if (n <= 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(' ');
        return sb.toString();
    }

    // --- Authentic Ubuntu Unix Classics: sl & asciiquarium ---

    // Authentic Toyoda Masashi D51 Locomotive (from Ubuntu 'sl' package)
    private static final String[] SL_D51_BASE = {
        "      ====        ________                ___________ ",
        "  _D _|  |_______/        \\__I_I_____===__|_________| ",
        "   |(_)---  |   H\\________/ |   |        =|___ ___|   ",
        "   /     |  |   H  |  |     |   |         ||_| |_||   ",
        "  |      |  |   H  |__--------------------| [___] |   ",
        "  | ________|___H__/__|_____/[][]~\\_______|       |   ",
        "  |/ |   |-----------I_____I [][] []  D   |=======|__ "
    };

    private static final String[][] SL_D51_WHEELS = {
        {
            "__/ =| o |=-~~\\  /~~\\  /~~\\  /~~\\ ____Y___________|__ ",
            " |/-=|___|=    ||    ||    ||    |_____/~\\___/        ",
            "  \\_/      \\O=====O=====O=====O_/      \\_/            "
        },
        {
            "__/ =| o |=-~~\\  /~~\\  /~~\\  /~~\\ ____Y___________|__ ",
            " |/-=|___|=O=====O=====O=====O   |_____/~\\___/        ",
            "  \\_/      \\__/  \\__/  \\__/  \\__/      \\_/            "
        },
        {
            "__/ =| o |=-O=====O=====O=====O \\ ____Y___________|__ ",
            " |/-=|___|=    ||    ||    ||    |_____/~\\___/        ",
            "  \\_/      \\__/  \\__/  \\__/  \\__/      \\_/            "
        },
        {
            "__/ =| o |=-~O=====O=====O=====O\\ ____Y___________|__ ",
            " |/-=|___|=    ||    ||    ||    |_____/~\\___/        ",
            "  \\_/      \\__/  \\__/  \\__/  \\__/      \\_/            "
        },
        {
            "__/ =| o |=-~~\\  /~~\\  /~~\\  /~~\\ ____Y___________|__ ",
            " |/-=|___|=   O=====O=====O=====O|_____/~\\___/        ",
            "  \\_/      \\__/  \\__/  \\__/  \\__/      \\_/            "
        },
        {
            "__/ =| o |=-~~\\  /~~\\  /~~\\  /~~\\ ____Y___________|__ ",
            " |/-=|___|=    ||    ||    ||    |_____/~\\___/        ",
            "  \\_/      \\_O=====O=====O=====O/      \\_/            "
        }
    };

    private static final String[] SL_D51_COAL = {
        "                              ",
        "                              ",
        "    _________________         ",
        "   _|                \\_____A  ",
        " =|                        |  ",
        " -|                        |  ",
        "__|________________________|_ ",
        "|__________________________|_ ",
        "   |_D__D__D_|  |_D__D__D_|   ",
        "    \\_/   \\_/    \\_/   \\_/    "
    };

    private static class SlSmoke {
        int r, c, step;
        SlSmoke(int r, int c) { this.r = r; this.c = c; this.step = 0; }
    }

    private void playTrainAnimation() {
        mIsAnimating = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final int COLS = 54;
                final int ROWS = 22;
                final int Y_START = 8;
                final String[] smokeText = {"(@@@)", "(@@@@)", "(@@)", "(@)", "@@", "@", "."};
                final List<SlSmoke> smokes = new ArrayList<SlSmoke>();

                // Train runs from right to left across the screen (COLS down to -85)
                for (int x = COLS; x >= -85 && mIsAnimating; x--) {
                    char[][] grid = new char[ROWS][COLS];
                    for (int r = 0; r < ROWS; r++) {
                        for (int c = 0; c < COLS; c++) grid[r][c] = ' ';
                    }

                    // 1. Update and draw smoke particles
                    if (x % 3 == 0) {
                        smokes.add(new SlSmoke(Y_START - 1, x + 7)); // Smokestack
                    }
                    for (int s = smokes.size() - 1; s >= 0; s--) {
                        SlSmoke sp = smokes.get(s);
                        sp.step++;
                        if (sp.step % 2 == 0) sp.r--; // Rise
                        sp.c += 2; // Drift rightwards
                        if (sp.r < 0 || sp.c >= COLS || sp.step / 3 >= smokeText.length) {
                            smokes.remove(s);
                        } else {
                            String pStr = smokeText[sp.step / 3];
                            for (int k = 0; k < pStr.length(); k++) {
                                int sc = sp.c + k;
                                if (sc >= 0 && sc < COLS && sp.r >= 0 && sp.r < ROWS) {
                                    grid[sp.r][sc] = pStr.charAt(k);
                                }
                            }
                        }
                    }

                    // 2. Draw D51 locomotive and 6-phase rotating wheels
                    int wheelPhase = Math.abs(x) % 6;
                    String[] wheels = SL_D51_WHEELS[wheelPhase];
                    for (int r = 0; r < 7; r++) {
                        String line = SL_D51_BASE[r];
                        for (int c = 0; c < line.length(); c++) {
                            int tc = x + c;
                            if (tc >= 0 && tc < COLS && (Y_START + r) < ROWS) {
                                grid[Y_START + r][tc] = line.charAt(c);
                            }
                        }
                    }
                    for (int r = 0; r < 3; r++) {
                        String line = wheels[r];
                        for (int c = 0; c < line.length(); c++) {
                            int tc = x + c;
                            if (tc >= 0 && tc < COLS && (Y_START + 7 + r) < ROWS) {
                                grid[Y_START + 7 + r][tc] = line.charAt(c);
                            }
                        }
                    }

                    // 3. Draw Coal Tender attached behind (offset +53)
                    for (int r = 0; r < 10; r++) {
                        String line = SL_D51_COAL[r];
                        for (int c = 0; c < line.length(); c++) {
                            int tc = x + 53 + c;
                            if (tc >= 0 && tc < COLS && (Y_START + r) < ROWS) {
                                grid[Y_START + r][tc] = line.charAt(c);
                            }
                        }
                    }

                    // Convert grid to ANSI frame
                    StringBuilder sb = new StringBuilder();
                    sb.append("\u001B[2J\u001B[H"); // Reset cursor to (0,0)
                    for (int r = 0; r < ROWS; r++) {
                        sb.append(new String(grid[r])).append("\r\n");
                    }

                    final byte[] b = sb.toString().getBytes();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mTerminalView.appendOutput(b, 0, b.length);
                        }
                    });

                    try { Thread.sleep(40); } catch (Exception ignored) {}
                }

                mIsAnimating = false;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mTerminalView.appendOutput("\r\n".getBytes(), 0, 2);
                        printPrompt();
                    }
                });
            }
        }).start();
    }

    // Authentic Kirk Baucom / Joan Stark ASCIQUARIUM (from Ubuntu 'asciiquarium' package)
    private void playAquariumAnimation() {
        mIsAnimating = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final int COLS = 54;
                final int ROWS = 22;

                final String[] castle = {
                    "        T~~   ",
                    "        |     ",
                    "       /^\\    ",
                    "      /   \\   ",
                    " _ _ / _ _ \\[_]",
                    "|_=__-_ =_|_=-_",
                    " | _- =  |=_ = ",
                    " |= -[]  |_-=_ ",
                    " |_______|_____"
                };

                final String[] duck = {
                    ">(o)___",
                    "  (_===/"
                };

                final String[] fish1Right = {
                    "  \\       ",
                    "   \\  __  ",
                    "----\\/ o\\ ",
                    "----/\\__/ ",
                    "   /      ",
                    "  /       "
                };

                final String[] seaweedA = {
                    " (   ) ",
                    "  ) (  ",
                    " (   ) ",
                    "  ) (  "
                };

                final String[] seaweedB = {
                    "  ) (  ",
                    " (   ) ",
                    "  ) (  ",
                    " (   ) "
                };

                for (int frame = 0; frame < 200 && mIsAnimating; frame++) {
                    char[][] grid = new char[ROWS][COLS];
                    for (int r = 0; r < ROWS; r++) {
                        for (int c = 0; c < COLS; c++) grid[r][c] = ' ';
                    }

                    // 1. Waterline
                    for (int c = 0; c < COLS; c++) grid[0][c] = '~';
                    String waves = "^^^^    ^^^    ^^^^    ^^^    ^^^^    ^^^    ^^^^    ^";
                    for (int c = 0; c < Math.min(COLS, waves.length()); c++) grid[1][c] = waves.charAt(c);

                    // 2. Surface duck swimming left-to-right
                    int duckX = ((frame * 2) % (COLS + 15)) - 8;
                    for (int r = 0; r < 2; r++) {
                        String dl = duck[r];
                        for (int c = 0; c < dl.length(); c++) {
                            int tc = duckX + c;
                            if (tc >= 0 && tc < COLS) grid[r][tc] = dl.charAt(c);
                        }
                    }

                    // 3. Joan Stark Fish 1 (long tail) swimming right at row 4
                    int f1X = ((frame * 2) % (COLS + 25)) - 12;
                    for (int r = 0; r < fish1Right.length; r++) {
                        String fl = fish1Right[r];
                        for (int c = 0; c < fl.length(); c++) {
                            char ch = fl.charAt(c);
                            if (ch != ' ') {
                                int tc = f1X + c;
                                if (tc >= 0 && tc < COLS && (4 + r) < ROWS) grid[4 + r][tc] = ch;
                            }
                        }
                    }

                    // 4. Fish 2 swimming left at row 11: <º)))><
                    String f2 = "<º)))><";
                    int f2X = COLS - ((frame * 2) % (COLS + 15));
                    for (int c = 0; c < f2.length(); c++) {
                        int tc = f2X + c;
                        if (tc >= 0 && tc < COLS) grid[11][tc] = f2.charAt(c);
                    }

                    // 5. Fish 3 swimming right at row 14: ><(((º>
                    String f3 = "><(((º>";
                    int f3X = ((frame * 3 + 12) % (COLS + 15)) - 8;
                    for (int c = 0; c < f3.length(); c++) {
                        int tc = f3X + c;
                        if (tc >= 0 && tc < COLS) grid[14][tc] = f3.charAt(c);
                    }

                    // 6. Fish 4 guppy: ><>
                    String f4 = "><>";
                    int f4X = ((frame * 2 + 30) % (COLS + 10)) - 4;
                    for (int c = 0; c < f4.length(); c++) {
                        int tc = f4X + c;
                        if (tc >= 0 && tc < COLS) grid[16][tc] = f4.charAt(c);
                    }

                    // 7. Bubbles rising from bottom
                    int[] bubbleCols = {8, 22, 34};
                    char[] bTypes = {'o', 'O', '.'};
                    for (int bIdx = 0; bIdx < bubbleCols.length; bIdx++) {
                        int bc = bubbleCols[bIdx];
                        int bStep = (frame * 2 + bIdx * 6) % 17;
                        int br = 18 - bStep;
                        if (br >= 2 && br < ROWS) {
                            grid[br][bc] = bTypes[bIdx % bTypes.length];
                        }
                    }

                    // 8. Atlantis Castle at bottom-right (columns 38 to 52, rows 13 to 21)
                    for (int r = 0; r < castle.length; r++) {
                        String cl = castle[r];
                        for (int c = 0; c < cl.length(); c++) {
                            char ch = cl.charAt(c);
                            if (ch != ' ') {
                                int tc = 38 + c;
                                if (tc >= 0 && tc < COLS && (13 + r) < ROWS) grid[13 + r][tc] = ch;
                            }
                        }
                    }

                    // 9. Swaying seaweed at bottom (columns 2, 14, 26, rows 18 to 21)
                    String[] sw = ((frame / 4) % 2 == 0) ? seaweedA : seaweedB;
                    int[] swCols = {2, 14, 26};
                    for (int sc : swCols) {
                        for (int r = 0; r < sw.length; r++) {
                            String sl = sw[r];
                            for (int c = 0; c < sl.length(); c++) {
                                char ch = sl.charAt(c);
                                if (ch != ' ') {
                                    int tc = sc + c;
                                    if (tc >= 0 && tc < COLS && (18 + r) < ROWS) grid[18 + r][tc] = ch;
                                }
                            }
                        }
                    }

                    // Convert frame to ANSI screen
                    StringBuilder sb = new StringBuilder();
                    sb.append("\u001B[2J\u001B[H"); // Home cursor
                    for (int r = 0; r < ROWS; r++) {
                        sb.append(new String(grid[r])).append("\r\n");
                    }

                    final byte[] b = sb.toString().getBytes();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mTerminalView.appendOutput(b, 0, b.length);
                        }
                    });

                    try { Thread.sleep(70); } catch (Exception ignored) {}
                }

                mIsAnimating = false;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mTerminalView.appendOutput("\r\n".getBytes(), 0, 2);
                        printPrompt();
                    }
                });
            }
        }).start();
    }

    // Authentic Chris Allegretta CMATRIX (from Ubuntu 'cmatrix' package)
    private void playMatrixAnimation() {
        mIsAnimating = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final int COLS = 54;
                final int ROWS = 22;
                final Random rng = new Random();
                final int[] headY = new int[COLS];
                final int[] length = new int[COLS];
                final char[] glyphs = "0123456789ABCDEFHIJKLMNOPQRSTUVWXYZ@#$%&*+-=~".toCharArray();

                for (int c = 0; c < COLS; c++) {
                    headY[c] = -rng.nextInt(ROWS);
                    length[c] = 5 + rng.nextInt(12);
                }

                while (mIsAnimating) {
                    char[][] grid = new char[ROWS][COLS];
                    int[][] colorGrid = new int[ROWS][COLS]; // 0=none, 1=white head, 2=bright green, 3=dim green

                    for (int c = 0; c < COLS; c++) {
                        headY[c]++;
                        if (headY[c] - length[c] >= ROWS) {
                            headY[c] = -rng.nextInt(10);
                            length[c] = 5 + rng.nextInt(12);
                        }
                        for (int i = 0; i < length[c]; i++) {
                            int r = headY[c] - i;
                            if (r >= 0 && r < ROWS) {
                                grid[r][c] = glyphs[rng.nextInt(glyphs.length)];
                                colorGrid[r][c] = (i == 0) ? 1 : (i < 4) ? 2 : 3;
                            }
                        }
                    }

                    StringBuilder sb = new StringBuilder("\u001B[2J\u001B[H");
                    for (int r = 0; r < ROWS; r++) {
                        int lastColor = -1;
                        for (int c = 0; c < COLS; c++) {
                            char ch = grid[r][c] == 0 ? ' ' : grid[r][c];
                            int col = colorGrid[r][c];
                            if (col != lastColor) {
                                if (col == 1) sb.append("\u001B[1;37m");       // Bright white head
                                else if (col == 2) sb.append("\u001B[1;32m");  // Bright matrix green
                                else if (col == 3) sb.append("\u001B[0;32m");  // Dim phosphor green
                                else sb.append("\u001B[0m");
                                lastColor = col;
                            }
                            sb.append(ch);
                        }
                        sb.append("\u001B[0m\r\n");
                    }

                    final byte[] b = sb.toString().getBytes();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mTerminalView.appendOutput(b, 0, b.length);
                        }
                    });

                    try { Thread.sleep(55); } catch (Exception ignored) {}
                }

                mIsAnimating = false;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mTerminalView.appendOutput("\r\n".getBytes(), 0, 2);
                        printPrompt();
                    }
                });
            }
        }).start();
    }

    // Authentic AAlib AAFIRE (from Ubuntu 'libaa-bin' package)
    private void playFireAnimation() {
        mIsAnimating = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final int COLS = 54;
                final int ROWS = 22;
                final Random rng = new Random();
                final int[] heat = new int[COLS * ROWS];
                final char[] fireChars = " .:;+=xX$&@".toCharArray();

                while (mIsAnimating) {
                    // Feed fuel to bottom row
                    for (int c = 0; c < COLS; c++) {
                        heat[(ROWS - 1) * COLS + c] = rng.nextInt(100) > 25 ? 255 : 0;
                    }

                    // Diffuse upwards with cooling
                    for (int r = 0; r < ROWS - 1; r++) {
                        for (int c = 0; c < COLS; c++) {
                            int left = heat[(r + 1) * COLS + ((c - 1 + COLS) % COLS)];
                            int right = heat[(r + 1) * COLS + ((c + 1) % COLS)];
                            int below = heat[(r + 1) * COLS + c];
                            int below2 = heat[Math.min(ROWS - 1, r + 2) * COLS + c];
                            int avg = (left + right + below + below2) / 4;
                            heat[r * COLS + c] = Math.max(0, avg - rng.nextInt(4));
                        }
                    }

                    StringBuilder sb = new StringBuilder("\u001B[2J\u001B[H");
                    for (int r = 0; r < ROWS; r++) {
                        int lastColor = -1;
                        for (int c = 0; c < COLS; c++) {
                            int h = heat[r * COLS + c];
                            int color = (h < 40) ? 0 : (h < 100) ? 1 : (h < 180) ? 2 : (h < 230) ? 3 : 4;
                            if (color != lastColor) {
                                if (color == 0) sb.append("\u001B[0;30m");      // Dark gray
                                else if (color == 1) sb.append("\u001B[0;31m"); // Dark red
                                else if (color == 2) sb.append("\u001B[1;31m"); // Bright red
                                else if (color == 3) sb.append("\u001B[1;33m"); // Bright yellow
                                else sb.append("\u001B[1;37m");                 // Hot white
                                lastColor = color;
                            }
                            int charIdx = (h * fireChars.length) / 256;
                            if (charIdx >= fireChars.length) charIdx = fireChars.length - 1;
                            sb.append(fireChars[charIdx]);
                        }
                        sb.append("\u001B[0m\r\n");
                    }

                    final byte[] b = sb.toString().getBytes();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mTerminalView.appendOutput(b, 0, b.length);
                        }
                    });

                    try { Thread.sleep(45); } catch (Exception ignored) {}
                }

                mIsAnimating = false;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mTerminalView.appendOutput("\r\n".getBytes(), 0, 2);
                        printPrompt();
                    }
                });
            }
        }).start();
    }

    // Authentic Matthew Simpson PIPES.SH (from Ubuntu 'pipes.sh' package)
    private void playPipesAnimation() {
        mIsAnimating = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final int COLS = 54;
                final int ROWS = 22;
                final Random rng = new Random();

                final char[][] turnChars = {
                    // up=0: left->3, right->1
                    {'┃', '┏', '┃', '┓'},
                    // right=1: up->0, down->2
                    {'┛', '━', '┓', '━'},
                    // down=2: left->3, right->1
                    {'┃', '┗', '┃', '┛'},
                    // left=3: up->0, down->2
                    {'┗', '━', '┏', '━'}
                };
                final char[] straightChars = {'┃', '━', '┃', '━'};
                final String[] colors = {
                    "\u001B[1;31m", "\u001B[1;32m", "\u001B[1;33m",
                    "\u001B[1;34m", "\u001B[1;35m", "\u001B[1;36m"
                };

                final int PIPE_COUNT = 3;
                final int[] px = new int[PIPE_COUNT];
                final int[] py = new int[PIPE_COUNT];
                final int[] pdir = new int[PIPE_COUNT]; // 0=up, 1=right, 2=down, 3=left
                final int[] pcol = new int[PIPE_COUNT];

                for (int i = 0; i < PIPE_COUNT; i++) {
                    px[i] = rng.nextInt(COLS);
                    py[i] = rng.nextInt(ROWS);
                    pdir[i] = rng.nextInt(4);
                    pcol[i] = i % colors.length;
                }

                char[][] screen = new char[ROWS][COLS];
                int[][] colorScreen = new int[ROWS][COLS];
                for (int r = 0; r < ROWS; r++) {
                    for (int c = 0; c < COLS; c++) {
                        screen[r][c] = ' ';
                        colorScreen[r][c] = -1;
                    }
                }

                while (mIsAnimating) {
                    for (int i = 0; i < PIPE_COUNT; i++) {
                        int oldDir = pdir[i];
                        int newDir = oldDir;
                        if (rng.nextInt(100) < 25) {
                            newDir = (oldDir + (rng.nextBoolean() ? 1 : 3)) % 4;
                        }
                        char ch = (oldDir == newDir) ? straightChars[newDir] : turnChars[oldDir][newDir];
                        screen[py[i]][px[i]] = ch;
                        colorScreen[py[i]][px[i]] = pcol[i];

                        pdir[i] = newDir;
                        if (newDir == 0) py[i] = (py[i] - 1 + ROWS) % ROWS;
                        else if (newDir == 1) px[i] = (px[i] + 1) % COLS;
                        else if (newDir == 2) py[i] = (py[i] + 1) % ROWS;
                        else if (newDir == 3) px[i] = (px[i] - 1 + COLS) % COLS;
                    }

                    StringBuilder sb = new StringBuilder("\u001B[2J\u001B[H");
                    for (int r = 0; r < ROWS; r++) {
                        int lastColor = -1;
                        for (int c = 0; c < COLS; c++) {
                            int ci = colorScreen[r][c];
                            if (ci != lastColor) {
                                if (ci >= 0 && ci < colors.length) sb.append(colors[ci]);
                                else sb.append("\u001B[0m");
                                lastColor = ci;
                            }
                            sb.append(screen[r][c]);
                        }
                        sb.append("\u001B[0m\r\n");
                    }

                    final byte[] b = sb.toString().getBytes();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mTerminalView.appendOutput(b, 0, b.length);
                        }
                    });

                    try { Thread.sleep(60); } catch (Exception ignored) {}
                }

                mIsAnimating = false;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mTerminalView.appendOutput("\r\n".getBytes(), 0, 2);
                        printPrompt();
                    }
                });
            }
        }).start();
    }

    // Authentic K. Lange NYANCAT (from Ubuntu 'nyancat' package)
    private void playNyanCatAnimation() {
        mIsAnimating = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final int COLS = 54;
                final int ROWS = 22;
                final String[] catFrames = {
                    ",------,\r\n|   /\\_/\\\r\n~|__( ^ .^)\r\n \"\"  \"\"",
                    ",------,\r\n|   /\\_/\\\r\n~|__( - .^)\r\n  \"\"  \"\""
                };

                final String[] rainbowColors = {
                    "\u001B[1;31m", "\u001B[1;33m", "\u001B[1;33m",
                    "\u001B[1;32m", "\u001B[1;36m", "\u001B[1;35m"
                };

                int frame = 0;
                while (mIsAnimating) {
                    frame++;
                    StringBuilder sb = new StringBuilder("\u001B[2J\u001B[H");

                    // 1. Twinkling stars in deep space
                    char[][] grid = new char[ROWS][COLS];
                    for (int r = 0; r < ROWS; r++) {
                        for (int c = 0; c < COLS; c++) grid[r][c] = ' ';
                    }
                    for (int s = 0; s < 12; s++) {
                        int sr = (s * 7 + frame / 2) % (ROWS - 2);
                        int sc = (COLS - ((frame * 2 + s * 9) % COLS) + COLS) % COLS;
                        grid[sr][sc] = (s % 3 == 0) ? '+' : (s % 3 == 1) ? '*' : '.';
                    }

                    // 2. Render rows with waving rainbow trail
                    int rainbowStartRow = 8;
                    int catCol = 28;
                    for (int r = 0; r < ROWS; r++) {
                        int rOffset = r - rainbowStartRow;
                        if (rOffset >= 0 && rOffset < 6) {
                            String colStr = rainbowColors[rOffset];
                            sb.append(colStr);
                            int wave = (int) Math.sin((frame + rOffset) * 0.5) * 1;
                            for (int c = 0; c < catCol; c++) {
                                sb.append((c % 2 == 0) ? '=' : '-');
                            }
                            sb.append("\u001B[0m");
                            // Cat body parts
                            if (rOffset == 0) sb.append("\u001B[1;37m,------,\u001B[0m");
                            else if (rOffset == 1) sb.append("\u001B[1;37m|  \u001B[1;35m/\\_/\\\u001B[0m");
                            else if (rOffset == 2) sb.append("\u001B[1;37m~|__(\u001B[1;33m ^ .\u001B[1;37m^)\u001B[0m");
                            else if (rOffset == 3) sb.append("\u001B[1;37m \"\"  \"\"\u001B[0m");
                            sb.append("\r\n");
                        } else {
                            sb.append(new String(grid[r])).append("\r\n");
                        }
                    }

                    final byte[] b = sb.toString().getBytes();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mTerminalView.appendOutput(b, 0, b.length);
                        }
                    });

                    try { Thread.sleep(80); } catch (Exception ignored) {}
                }

                mIsAnimating = false;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mTerminalView.appendOutput("\r\n".getBytes(), 0, 2);
                        printPrompt();
                    }
                });
            }
        }).start();
    }

    // QCyberdeck Package Repository Map
    private static final Map<String, String> PKG_URLS = new HashMap<String, String>();
    static {
        PKG_URLS.put("curl", "https://raw.githubusercontent.com/Inknyto/arm-binaries/main/curl");
        PKG_URLS.put("htop", "https://raw.githubusercontent.com/Inknyto/arm-binaries/main/htop");
        PKG_URLS.put("vim", "https://raw.githubusercontent.com/Inknyto/arm-binaries/main/vim");
        PKG_URLS.put("ncdu", "https://raw.githubusercontent.com/Inknyto/arm-binaries/main/ncdu");
        PKG_URLS.put("strace", "https://raw.githubusercontent.com/Inknyto/arm-binaries/main/strace");
        PKG_URLS.put("tcpdump", "https://raw.githubusercontent.com/Inknyto/arm-binaries/main/tcpdump");
        PKG_URLS.put("socat", "https://raw.githubusercontent.com/Inknyto/arm-binaries/main/socat");
        PKG_URLS.put("iperf", "https://raw.githubusercontent.com/Inknyto/arm-binaries/main/iperf");
    }

    private void handlePkgCommand(String cmd) {
        String[] parts = cmd.split("\\s+");
        if (parts.length < 2 || parts[1].equals("help")) {
            printPkgHelp();
            return;
        }

        String sub = parts[1].toLowerCase();
        if (sub.equals("list") || sub.equals("search")) {
            printPkgList();
            return;
        }

        if (sub.equals("install") || sub.equals("add") || sub.equals("get")) {
            if (parts.length < 3) {
                String u = "\r\nUsage: pkg install <package-name>\r\nRun 'pkg list' to view available packages.\r\n\r\n";
                mTerminalView.appendOutput(u.getBytes(), 0, u.length());
                printPrompt();
                return;
            }
            installPackageAsync(parts[2].toLowerCase());
            return;
        }

        if (sub.equals("remove") || sub.equals("uninstall") || sub.equals("rm")) {
            if (parts.length < 3) {
                String u = "\r\nUsage: pkg remove <package-name>\r\n\r\n";
                mTerminalView.appendOutput(u.getBytes(), 0, u.length());
                printPrompt();
                return;
            }
            removePackage(parts[2].toLowerCase());
            return;
        }

        printPkgHelp();
    }

    private void printPkgHelp() {
        String h =
            "\r\n================================================\r\n" +
            "  QCyberdeck Package Manager (pkg)\r\n" +
            "================================================\r\n" +
            "  Commands:\r\n" +
            "    pkg list             List available & installed packages\r\n" +
            "    pkg install <name>   Download & install static ARMv7 binary\r\n" +
            "    pkg remove <name>    Remove an installed package\r\n" +
            "    pkg help             Show this help screen\r\n\r\n" +
            "  Try:  pkg list\r\n" +
            "        pkg install curl\r\n" +
            "        pkg install htop\r\n" +
            "================================================\r\n\r\n";
        byte[] b = h.getBytes();
        mTerminalView.appendOutput(b, 0, b.length);
        printPrompt();
    }

    private void printPkgList() {
        File binDir = new File(getFilesDir(), "usr/bin");
        StringBuilder sb = new StringBuilder("\r\n================================================\r\n");
        sb.append("  QCyberdeck Package Index (Static ARMv7)\r\n");
        sb.append("================================================\r\n");

        String[][] core = {
            {"ssh", "Dropbear SSH Client (dbclient)"},
            {"scp", "Secure Copy file transfer"},
            {"git", "Fast static Git version control"},
            {"nano", "GNU Nano terminal text editor"}
        };
        for (String[] item : core) {
            String name = item[0];
            String desc = item[1];
            File f = new File(binDir, name.equals("ssh") || name.equals("scp") ? "dropbearmulti" : name);
            sb.append("  • ").append(String.format("%-10s", name))
              .append(f.exists() ? "\u001B[1;32m[INSTALLED]\u001B[0m" : "\u001B[1;30m[NOT INSTALLED]\u001B[0m")
              .append("  ").append(desc).append("\r\n");
        }

        String[][] online = {
            {"curl", "cURL with HTTPS/TLS support (2.5 MB)"},
            {"htop", "Interactive visual process monitor (294 KB)"},
            {"vim", "Vim modal text editor (1.9 MB)"},
            {"ncdu", "NCurses disk usage analyzer (248 KB)"},
            {"tcpdump", "Network packet capture analyzer (1.1 MB)"},
            {"socat", "Multipurpose socket relay (885 KB)"},
            {"strace", "System call tracer (571 KB)"},
            {"iperf", "Network bandwidth testing tool (278 KB)"}
        };
        for (String[] item : online) {
            String name = item[0];
            String desc = item[1];
            File f = new File(binDir, name);
            sb.append("  • ").append(String.format("%-10s", name))
              .append(f.exists() ? "\u001B[1;32m[INSTALLED]\u001B[0m" : "\u001B[1;33m[AVAILABLE]\u001B[0m")
              .append("  ").append(desc).append("\r\n");
        }

        sb.append("================================================\r\n");
        sb.append("Install:  pkg install <name>\r\n");
        sb.append("Remove:   pkg remove <name>\r\n\r\n");
        byte[] b = sb.toString().getBytes();
        mTerminalView.appendOutput(b, 0, b.length);
        printPrompt();
    }

    private void removePackage(String pkgName) {
        File binDir = new File(getFilesDir(), "usr/bin");
        File target = new File(binDir, pkgName);
        if (!target.exists()) {
            String msg = "\r\n[-] Package '" + pkgName + "' is not installed.\r\n\r\n";
            mTerminalView.appendOutput(msg.getBytes(), 0, msg.length());
            printPrompt();
            return;
        }
        if (pkgName.equals("busybox") || pkgName.equals("sh")) {
            String msg = "\r\n\u001B[1;31m[-] Cannot remove core system tool: " + pkgName + "\u001B[0m\r\n\r\n";
            mTerminalView.appendOutput(msg.getBytes(), 0, msg.length());
            printPrompt();
            return;
        }
        target.delete();
        String msg = "\r\n\u001B[1;32m[+] Removed package '" + pkgName + "' from /usr/bin/.\u001B[0m\r\n\r\n";
        mTerminalView.appendOutput(msg.getBytes(), 0, msg.length());
        printPrompt();
    }

    private void installPackageAsync(final String pkgName) {
        final String urlStr = PKG_URLS.get(pkgName);
        if (urlStr == null) {
            String err = "\r\n\u001B[1;31m[-] Unknown package: " + pkgName + "\u001B[0m\r\n" +
                         "Run 'pkg list' to see all available packages.\r\n\r\n";
            mTerminalView.appendOutput(err.getBytes(), 0, err.length());
            printPrompt();
            return;
        }

        final File binDir = new File(getFilesDir(), "usr/bin");
        final File destFile = new File(binDir, pkgName);

        String startMsg = "\r\n\u001B[1;36m[*] Querying repository for '" + pkgName + "' (static armv7l)...\u001B[0m\r\n";
        mTerminalView.appendOutput(startMsg.getBytes(), 0, startMsg.length());

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL(urlStr);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(30000);
                    conn.setRequestProperty("User-Agent", "QCyberdeck-Pkg/1.0 (BlackBerry Q5)");
                    conn.connect();

                    int responseCode = conn.getResponseCode();
                    if (responseCode != 200) {
                        throw new Exception("HTTP server returned " + responseCode);
                    }

                    int totalSize = conn.getContentLength();
                    InputStream in = conn.getInputStream();
                    File tmpFile = new File(binDir, pkgName + ".tmp");
                    FileOutputStream out = new FileOutputStream(tmpFile);

                    byte[] buffer = new byte[8192];
                    int read;
                    int downloaded = 0;
                    long lastUpdate = System.currentTimeMillis();

                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                        downloaded += read;
                        long now = System.currentTimeMillis();
                        if (now - lastUpdate > 400) {
                            lastUpdate = now;
                            final int kb = downloaded / 1024;
                            final int totalKb = totalSize > 0 ? totalSize / 1024 : 0;
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    String progress = "\r[*] Downloading: " + kb + (totalKb > 0 ? " / " + totalKb : "") + " KB";
                                    mTerminalView.appendOutput(progress.getBytes(), 0, progress.length());
                                }
                            });
                        }
                    }

                    out.flush();
                    out.close();
                    in.close();

                    tmpFile.renameTo(destFile);
                    destFile.setReadable(true, false);
                    destFile.setExecutable(true, false);
                    try {
                        Runtime.getRuntime().exec(new String[]{"chmod", "755", destFile.getAbsolutePath()}).waitFor();
                    } catch (Exception ignored) {}

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            String done = "\r\n\u001B[1;32m[+] Successfully installed " + pkgName + " to /usr/bin/" + pkgName + "!\u001B[0m\r\n" +
                                          "[+] You can now run '" + pkgName + "' anytime from your terminal.\r\n\r\n";
                            mTerminalView.appendOutput(done.getBytes(), 0, done.length());
                            printPrompt();
                        }
                    });

                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            String err = "\r\n\u001B[1;31m[-] Install failed: " + e.getMessage() + "\u001B[0m\r\n\r\n";
                            mTerminalView.appendOutput(err.getBytes(), 0, err.length());
                            printPrompt();
                        }
                    });
                }
            }
        }).start();
    }

    // Authentic Tony Monroe cowsay -f dragon for Linux (adapts to active CRT theme)
    private void printDragon(String msg) {
        StringBuilder sb = new StringBuilder("\r\n ");
        for (int i = 0; i < msg.length() + 2; i++) sb.append("-");
        sb.append("\r\n< ").append(msg).append(" >\r\n ");
        for (int i = 0; i < msg.length() + 2; i++) sb.append("-");
        sb.append("\r\n");
        sb.append("         \\                    / \\  //\\\r\n");
        sb.append("          \\    |\\___/|      /   \\//  \\\\\r\n");
        sb.append("               /0  0  \\__  /    //  | \\ \\\r\n");
        sb.append("              /     /  \\/_/    //   |  \\  \\\r\n");
        sb.append("              @_^_@'/   \\/_   //    |   \\   \\\r\n");
        sb.append("              //_^_/     \\/_ //     |    \\    \\\r\n");
        sb.append("           ( //) |        \\///      |     \\     \\\r\n");
        sb.append("         ( / /) _|_ /   )  //       |      \\     _\\\r\n");
        sb.append("       ( // /) '/,_ _ _/  ( ; -.    |    _ _\\.-~\r\n");
        sb.append("     (( / / )) ,-{        _      `-.|.-~-.\r\n");
        sb.append("    (( // / ))  '/\\      /                 ~-.\r\n");
        sb.append("    (( /// ))      `.   {            }\r\n");
        sb.append("     (( / ))     .----~-.\\        \\-`\r\n\r\n");
        byte[] b = sb.toString().getBytes();
        mTerminalView.appendOutput(b, 0, b.length);
        printPrompt();
    }

    // Authentic Linux Neofetch / Fastfetch for BlackBerry 10
    private void printNeofetch() {
        Intent bi = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int bLvl = bi != null ? bi.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) : -1;
        long upMs = android.os.SystemClock.elapsedRealtime();
        long upHrs = upMs / (1000 * 60 * 60);
        long upMins = (upMs / (1000 * 60)) % 60;

        StringBuilder sb = new StringBuilder("\r\n");
        sb.append("\u001B[1;32m          \\  /          \u001B[1;32mroot\u001B[0m@\u001B[1;32mq5\u001B[0m\r\n");
        sb.append("\u001B[1;32m         .-'`'-.        \u001B[1;37m-------\u001B[0m\r\n");
        sb.append("\u001B[1;32m       /`  ||   `\\      \u001B[1;36mOS\u001B[0m: BlackBerry 10.3 (QNX Neutrino)\r\n");
        sb.append("\u001B[1;32m      /   /__\\    \\     \u001B[1;36mHost\u001B[0m: BlackBerry Q5 (SQR100-2)\r\n");
        sb.append("\u001B[1;32m     '._  `\"\"`  _.'     \u001B[1;36mKernel\u001B[0m: 3.4.0-QNX-Neutrino-6.6.0\r\n");
        sb.append("\u001B[1;32m        /`-....-'\\       \u001B[1;36mUptime\u001B[0m: ").append(upHrs).append("h ").append(upMins).append("m\r\n");
        sb.append("\u001B[1;32m      /  (o)(o)(o) \\     \u001B[1;36mShell\u001B[0m: /system/bin/sh (Musl BusyBox)\r\n");
        sb.append("\u001B[1;32m     | (o)(o)(o)(o) |    \u001B[1;36mDisplay\u001B[0m: 720x720 LCD (328 PPI)\r\n");
        sb.append("\u001B[1;32m     | (o)(o)(o)(o) |    \u001B[1;36mTerminal\u001B[0m: QCyberdeck CRT Phosphor\r\n");
        sb.append("\u001B[1;32m      \\ (o)(o)(o)  /     \u001B[1;36mCPU\u001B[0m: Qualcomm MSM8960 (2) @ 1.20GHz\r\n");
        sb.append("\u001B[1;32m       \\  (o)(o)  /      \u001B[1;36mMemory\u001B[0m: 742MB / 2048MB (LPDDR2)\r\n");
        sb.append("\u001B[1;32m         `-(o)-'         \u001B[1;36mBattery\u001B[0m: ").append(bLvl > 0 ? (bLvl + "% (Good)") : "Optimal").append("\r\n\r\n");
        sb.append("                         \u001B[40m   \u001B[41m   \u001B[42m   \u001B[43m   \u001B[44m   \u001B[45m   \u001B[46m   \u001B[47m   \u001B[0m\r\n");
        sb.append("                         \u001B[100m   \u001B[101m   \u001B[102m   \u001B[103m   \u001B[104m   \u001B[105m   \u001B[106m   \u001B[107m   \u001B[0m\r\n\r\n");
        byte[] b = sb.toString().getBytes();
        mTerminalView.appendOutput(b, 0, b.length);
        printPrompt();
    }

    // Authentic CBONSAI: Procedural growing bonsai tree animation
    private void playBonsaiAnimation() {
        mIsAnimating = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final int COLS = 48;
                final int ROWS = 20;
                final Random rng = new Random();
                final char[][] screen = new char[ROWS][COLS];
                final String[][] colorScreen = new String[ROWS][COLS];

                for (int r = 0; r < ROWS; r++) {
                    for (int c = 0; c < COLS; c++) {
                        screen[r][c] = ' ';
                        colorScreen[r][c] = "";
                    }
                }

                // Draw pot at rows 16-19
                String[] pot = {
                    "       :___________________________:       ",
                    "        \\_________________________/        ",
                    "             )_______________(             ",
                    "           /__|_____________|__\\           "
                };
                for (int r = 0; r < pot.length; r++) {
                    int startX = (COLS - pot[r].length()) / 2;
                    for (int c = 0; c < pot[r].length(); c++) {
                        if (startX + c < COLS) {
                            screen[16 + r][startX + c] = pot[r].charAt(c);
                            colorScreen[16 + r][startX + c] = "\u001B[1;36m";
                        }
                    }
                }

                int curX = COLS / 2;
                int curY = 15;
                screen[curY][curX] = '|';
                colorScreen[curY][curX] = "\u001B[1;33m";

                List<int[]> branches = new ArrayList<int[]>();
                branches.add(new int[]{curX, curY, 0});

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mTerminalView.clearScreen();
                    }
                });

                // Step 1: Grow trunk & branches
                for (int step = 0; step < 16 && mIsAnimating; step++) {
                    List<int[]> nextBranches = new ArrayList<int[]>();
                    for (int[] b : branches) {
                        int bx = b[0];
                        int by = b[1];
                        int lvl = b[2];
                        if (by <= 4) continue;

                        int dx = rng.nextInt(3) - 1;
                        int ny = by - 1;
                        int nx = bx + dx;

                        if (ny >= 3 && nx >= 3 && nx < COLS - 3) {
                            char ch = dx == 0 ? '|' : (dx < 0 ? '/' : '\\');
                            screen[ny][nx] = ch;
                            colorScreen[ny][nx] = "\u001B[1;33m";
                            nextBranches.add(new int[]{nx, ny, lvl + 1});

                            if (rng.nextFloat() < 0.40f && lvl < 4) {
                                int sdx = dx != 0 ? -dx : (rng.nextBoolean() ? 1 : -1);
                                int snx = bx + sdx;
                                if (snx >= 3 && snx < COLS - 3) {
                                    char sch = sdx < 0 ? '/' : '\\';
                                    screen[ny][snx] = sch;
                                    colorScreen[ny][snx] = "\u001B[1;33m";
                                    nextBranches.add(new int[]{snx, ny, lvl + 1});
                                }
                            }
                        }
                    }
                    branches = nextBranches;
                    renderBonsaiFrame(screen, colorScreen, ROWS, COLS);
                    try { Thread.sleep(70); } catch (Exception ignored) {}
                }

                // Step 2: Bloom foliage clusters around branches
                final char[] leaves = {'&', '%', '@', '#', '*'};
                for (int y = 3; y < 13 && mIsAnimating; y++) {
                    for (int x = 3; x < COLS - 3; x++) {
                        if (screen[y][x] == '/' || screen[y][x] == '\\' || screen[y][x] == '|') {
                            for (int dy = -1; dy <= 1; dy++) {
                                for (int dx = -2; dx <= 2; dx++) {
                                    int ny = y + dy;
                                    int nx = x + dx;
                                    if (ny >= 2 && ny < 14 && nx >= 2 && nx < COLS - 2 && screen[ny][nx] == ' ') {
                                        if (rng.nextFloat() < 0.65f) {
                                            screen[ny][nx] = leaves[rng.nextInt(leaves.length)];
                                            colorScreen[ny][nx] = "\u001B[1;32m";
                                        }
                                    }
                                }
                            }
                        }
                    }
                    renderBonsaiFrame(screen, colorScreen, ROWS, COLS);
                    try { Thread.sleep(90); } catch (Exception ignored) {}
                }

                mIsAnimating = false;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mTerminalView.appendOutput("\r\n".getBytes(), 0, 2);
                        printPrompt();
                    }
                });
            }

            private void renderBonsaiFrame(char[][] screen, String[][] colors, int rows, int cols) {
                StringBuilder frame = new StringBuilder("\u001B[H");
                for (int r = 0; r < rows; r++) {
                    String lastCol = "";
                    for (int c = 0; c < cols; c++) {
                        String col = colors[r][c];
                        if (!col.equals(lastCol)) {
                            frame.append(col.isEmpty() ? "\u001B[0m" : col);
                            lastCol = col;
                        }
                        frame.append(screen[r][c]);
                    }
                    frame.append("\u001B[0m\r\n");
                }
                final byte[] b = frame.toString().getBytes();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mTerminalView.appendOutput(b, 0, b.length);
                    }
                });
            }
        }).start();
    }

    // Classic Unix Fortunes Database
    private static final String[] FORTUNES = {
        "The best way to predict the future is to invent it. -- Alan Kay",
        "Simplicity is prerequisite for reliability. -- Edsger W. Dijkstra",
        "Unix is simple. It just takes a genius to understand its simplicity. -- Dennis Ritchie",
        "There are two ways of constructing a software design: One way is to make it so simple that there are obviously no deficiencies, and the other way is to make it so complicated that there are no obvious deficiencies. -- C.A.R. Hoare",
        "Talk is cheap. Show me the code. -- Linus Torvalds",
        "Any fool can write code that a computer can understand. Good programmers write code that humans can understand. -- Martin Fowler",
        "Software is like sex: it's better when it's free. -- Linus Torvalds",
        "BlackBerry Q5: Real physical keyboard, real terminal, real freedom.",
        "A computer lets you make more mistakes faster than any other invention with the possible exceptions of handguns and Tequila. -- Mitch Radcliffe",
        "There are 10 types of people in the world: those who understand binary, and those who don't."
    };

    private void onEnter() {
        if (mIsAnimating) {
            mIsAnimating = false;
            return;
        }

        String cmd = mInputLine.toString().trim();
        mInputLine.setLength(0);

        if (cmd.length() > 0) {
            mHistory.add(cmd);
            mHistoryIndex = mHistory.size();
        }

        mTerminalView.scrollToBottom();
        mTerminalView.appendOutput(new byte[]{'\r', '\n'}, 0, 2);

        if (cmd.isEmpty()) {
            printPrompt();
            return;
        }

        if (cmd.startsWith("pkg")) {
            handlePkgCommand(cmd);
            return;
        }

        // --- Canonical Ubuntu Terminal Classics ---

        if (cmd.equals("sl") || cmd.equals("train")) {
            playTrainAnimation();
            return;
        }

        if (cmd.equals("fish") || cmd.equals("aqua") || cmd.equals("aquarium") || cmd.equals("asciiquarium")) {
            playAquariumAnimation();
            return;
        }

        if (cmd.equals("cmatrix") || cmd.equals("matrix")) {
            playMatrixAnimation();
            return;
        }

        if (cmd.equals("aafire") || cmd.equals("fire")) {
            playFireAnimation();
            return;
        }

        if (cmd.equals("pipes") || cmd.equals("pipes.sh")) {
            playPipesAnimation();
            return;
        }

        if (cmd.equals("nyancat")) {
            playNyanCatAnimation();
            return;
        }

        if (cmd.equals("neofetch") || cmd.equals("fastfetch")) {
            printNeofetch();
            return;
        }

        if (cmd.equals("cbonsai") || cmd.equals("bonsai")) {
            playBonsaiAnimation();
            return;
        }

        if (cmd.equals("fortune") || cmd.equals("fortune | cowsay")) {
            Random rng = new Random();
            String f = FORTUNES[rng.nextInt(FORTUNES.length)];
            if (cmd.contains("cowsay")) {
                StringBuilder sb = new StringBuilder("\r\n ");
                for (int i = 0; i < f.length() + 2; i++) sb.append("-");
                sb.append("\r\n< ").append(f).append(" >\r\n ");
                for (int i = 0; i < f.length() + 2; i++) sb.append("-");
                sb.append("\r\n        \\   ^__^\r\n");
                sb.append("         \\  (oo)\\_______\r\n");
                sb.append("            (__)\\       )\\/\\\r\n");
                sb.append("                ||----w |\r\n");
                sb.append("                ||     ||\r\n\r\n");
                byte[] b = sb.toString().getBytes();
                mTerminalView.appendOutput(b, 0, b.length);
            } else {
                String out = "\r\n" + f + "\r\n\r\n";
                byte[] b = out.getBytes();
                mTerminalView.appendOutput(b, 0, b.length);
            }
            printPrompt();
            return;
        }

        if (cmd.startsWith("cowthink")) {
            String msg = cmd.length() > 8 ? cmd.substring(8).trim() : "Thinking...";
            if (msg.isEmpty()) msg = "Thinking...";
            StringBuilder sb = new StringBuilder("\r\n ");
            for (int i = 0; i < msg.length() + 2; i++) sb.append("-");
            sb.append("\r\n( ").append(msg).append(" )\r\n ");
            for (int i = 0; i < msg.length() + 2; i++) sb.append("-");
            sb.append("\r\n        o   ^__^\r\n");
            sb.append("         o  (oo)\\_______\r\n");
            sb.append("            (__)\\       )\\/\\\r\n");
            sb.append("                ||----w |\r\n");
            sb.append("                ||     ||\r\n\r\n");
            byte[] b = sb.toString().getBytes();
            mTerminalView.appendOutput(b, 0, b.length);
            printPrompt();
            return;
        }

        // --- Instant In-Memory ASCII and Hardware Utilities ---

        if (cmd.equals("skull")) {
            String skull =
                "\r\n\u001B[1;31m" +
                "          .---.\r\n" +
                "         /     \\\r\n" +
                "        | () () |\r\n" +
                "         \\  ^  /\r\n" +
                "          |||||\r\n" +
                "      .---'---'---.\r\n" +
                "     /  CYBERDECK  \\\r\n" +
                "    |  ROOT ACCESS  |\r\n" +
                "     \\_____________/\r\n" +
                "\u001B[0m\r\n";
            byte[] b = skull.getBytes();
            mTerminalView.appendOutput(b, 0, b.length);
            printPrompt();
            return;
        }

        if (cmd.startsWith("cowsay")) {
            String msg = cmd.length() > 6 ? cmd.substring(6).trim() : "QCyberdeck online.";
            if (msg.isEmpty()) msg = "QCyberdeck online.";
            StringBuilder sb = new StringBuilder("\r\n ");
            for (int i = 0; i < msg.length() + 2; i++) sb.append("-");
            sb.append("\r\n< ").append(msg).append(" >\r\n ");
            for (int i = 0; i < msg.length() + 2; i++) sb.append("-");
            sb.append("\r\n        \\   ^__^\r\n");
            sb.append("         \\  (oo)\\_______\r\n");
            sb.append("            (__)\\       )\\/\\\r\n");
            sb.append("                ||----w |\r\n");
            sb.append("                ||     ||\r\n\r\n");
            byte[] b = sb.toString().getBytes();
            mTerminalView.appendOutput(b, 0, b.length);
            printPrompt();
            return;
        }

        if (cmd.startsWith("dragon")) {
            String msg = cmd.length() > 6 ? cmd.substring(6).trim() : "Rawr! BlackBerry Cyberdeck online.";
            if (msg.isEmpty()) msg = "Rawr! BlackBerry Cyberdeck online.";
            printDragon(msg);
            return;
        }

        if (cmd.equals("deck")) {
            String deck =
                "\r\n" +
                "   .------------------------.\r\n" +
                "   | [=] QCYBERDECK    100% |\r\n" +
                "   |------------------------|\r\n" +
                "   | root@q5:~# _           |\r\n" +
                "   | Linux 3.4 MSM8960 2GB  |\r\n" +
                "   |========================|\r\n" +
                "   | [Q][W][E][R][T][Y] ... |\r\n" +
                "   | [A][S][D][F][G][H] ... |\r\n" +
                "   | [ALT][Z][X][C][V] [ENT]|\r\n" +
                "   '------------------------'\r\n\r\n";
            byte[] b = deck.getBytes();
            mTerminalView.appendOutput(b, 0, b.length);
            printPrompt();
            return;
        }

        if (cmd.equals("clock")) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
            String clock = "\r\n  System Time : " + sdf.format(new Date()) + "\r\n\r\n";
            byte[] b = clock.getBytes();
            mTerminalView.appendOutput(b, 0, b.length);
            printPrompt();
            return;
        }

        if (cmd.equals("battery") || cmd.equals("dumpsys battery")) {
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent bIntent = registerReceiver(null, ifilter);
            StringBuilder sb = new StringBuilder("\r\n=== BlackBerry Q5 Battery Telemetry ===\r\n");
            if (bIntent != null) {
                int level = bIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = bIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                int status = bIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                int health = bIntent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1);
                int temp = bIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
                int voltage = bIntent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);

                String statusStr = (status == BatteryManager.BATTERY_STATUS_CHARGING) ? "Charging" :
                                   (status == BatteryManager.BATTERY_STATUS_FULL) ? "Full" : "Discharging";
                String healthStr = (health == BatteryManager.BATTERY_HEALTH_GOOD) ? "Good" : "Normal";
                int pct = (scale > 0) ? (level * 100) / scale : level;

                sb.append("  Charge Level : ").append(pct).append("%\r\n");
                sb.append("  Status       : ").append(statusStr).append("\r\n");
                sb.append("  Voltage      : ").append(voltage).append(" mV\r\n");
                sb.append("  Temperature  : ").append(temp / 10.0f).append(" °C\r\n");
                sb.append("  Health       : ").append(healthStr).append("\r\n");
                sb.append("  Technology   : Li-ion (2180 mAh)\r\n");
            } else {
                sb.append("  Unable to query battery sensor.\r\n");
            }
            sb.append("=======================================\r\n\r\n");
            byte[] b = sb.toString().getBytes();
            mTerminalView.appendOutput(b, 0, b.length);
            printPrompt();
            return;
        }

        if (cmd.equals("whoami")) {
            byte[] b = "root\r\n".getBytes();
            mTerminalView.appendOutput(b, 0, b.length);
            printPrompt();
            return;
        }

        if (cmd.equals("id")) {
            byte[] b = "uid=0(root) gid=0(root) groups=0(root)\r\n".getBytes();
            mTerminalView.appendOutput(b, 0, b.length);
            printPrompt();
            return;
        }

        if (cmd.equals("help")) {
            String help =
                "\r\n================================================\r\n" +
                "  QCyberdeck v3.8.0 - Quick Reference\r\n" +
                "================================================\r\n" +
                "  Handbook    : tutorial  (or  manual)\r\n" +
                "  Packages    : pkg list, pkg install <name>, pkg remove <name>\r\n" +
                "  SSH / Git   : ssh, scp, dropbearkey, git, nano\r\n" +
                "  Animations  : dragon, cbonsai, sl, fish, matrix, aafire, pipes, nyancat\r\n" +
                "  System Info : neofetch, battery, sysinfo, uname -a, uptime\r\n" +
                "  Identity    : whoami, id\r\n" +
                "  Files       : tree, ls -la, pwd, cd, mkdir, rm, cp, mv, vi\r\n" +
                "  Network     : ifconfig, netstat, ping, nc, wget, curl\r\n" +
                "  Archives    : tar -czvf, gzip, bzip2\r\n" +
                "  Tools       : grep, awk, sed, find, strings, hexdump\r\n" +
                "  All 350+    : busybox --help\r\n" +
                "================================================\r\n\r\n";
            byte[] b = help.getBytes();
            mTerminalView.appendOutput(b, 0, b.length);
            printPrompt();
            return;
        }

        if (cmd.equals("tutorial") || cmd.equals("manual")) {
            byte[] b = Installer.TUTORIAL_TEXT.getBytes();
            mTerminalView.appendOutput(b, 0, b.length);
            printPrompt();
            return;
        }

        // Forward general Unix commands to the real Linux shell
        if (mSession != null) {
            mSession.write(cmd + "\nprintf \"\\033[1;32mroot@q5\\033[0m:\\033[1;34m~#\\033[0m \"\n");
        }
    }

    private void onCtrlC() {
        if (mIsAnimating) {
            mIsAnimating = false;
            return;
        }
        mInputLine.setLength(0);
        mTerminalView.scrollToBottom();
        mTerminalView.appendOutput(new byte[]{'^', 'C', '\r', '\n'}, 0, 4);
        if (mSession != null) {
            mSession.write(new byte[]{0x03}); // SIGINT
        }
    }

    private void onHistoryUp() {
        if (mHistory.isEmpty()) return;
        if (mHistoryIndex > 0) {
            mHistoryIndex--;
        } else if (mHistoryIndex == -1) {
            mHistoryIndex = mHistory.size() - 1;
        }
        replaceInputLine(mHistory.get(mHistoryIndex));
    }

    private void onHistoryDown() {
        if (mHistory.isEmpty()) return;
        if (mHistoryIndex < mHistory.size() - 1) {
            mHistoryIndex++;
            replaceInputLine(mHistory.get(mHistoryIndex));
        } else {
            mHistoryIndex = mHistory.size();
            replaceInputLine("");
        }
    }

    private void replaceInputLine(String newLine) {
        mTerminalView.scrollToBottom();
        while (mInputLine.length() > 0) {
            mTerminalView.appendOutput(new byte[]{'\b', ' ', '\b'}, 0, 3);
            mInputLine.deleteCharAt(mInputLine.length() - 1);
        }
        mInputLine.append(newLine);
        byte[] bytes = newLine.getBytes();
        mTerminalView.appendOutput(bytes, 0, bytes.length);
    }

    private void checkAndStart() {
        if (!Installer.isInstalled(this)) {
            mLayoutInstalling.setVisibility(View.VISIBLE);
            Installer.install(this, new Installer.InstallCallback() {
                @Override
                public void onProgress(final String status) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mTvInstallStatus.setText(status);
                        }
                    });
                }

                @Override
                public void onComplete() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mLayoutInstalling.setVisibility(View.GONE);
                            initSession();
                            mSession.start();
                        }
                    });
                }

                @Override
                public void onError(final String error) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mTvInstallStatus.setText("error: " + error);
                        }
                    });
                }
            });
        } else {
            mLayoutInstalling.setVisibility(View.GONE);
            mSession.start();
        }
    }

    // --- BlackBerry Q5 Physical Keyboard Key Handler ---

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // Hardware Volume Down button on BlackBerry: acts as CTRL modifier or tap for instant Ctrl+C
        if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                mVolumeDownHeld = true;
                return true;
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                if (mVolumeDownHeld) {
                    mVolumeDownHeld = false;
                    onCtrlC(); // Tapping Volume Down sends Ctrl+C!
                }
                return true;
            }
        }

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();

            // Hardware Back button on BlackBerry: stop animation, or clear line, or send ESC
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (mIsAnimating) {
                    mIsAnimating = false;
                    return true;
                }
                if (mInputLine.length() > 0) {
                    replaceInputLine("");
                } else if (mSession != null) {
                    mSession.write(new byte[]{0x1B});
                }
                return true;
            }

            // Volume Up cycles terminal theme
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                mTerminalView.cycleTheme();
                return true;
            }

            // Quick terminal symbol shortcuts via Alt layer on Q5 physical keyboard.
            // We resolve via getUnicodeChar(metaState) to catch what BB10 actually maps:
            // Alt+Space → '|' , Alt+Enter → '\'
            // (isAltPressed() alone doesn't reliably catch hardware Alt on BB10)
            {
                int metaState = event.getMetaState();
                if ((metaState & KeyEvent.META_ALT_ON) != 0) {
                    int altUnicode = event.getUnicodeChar(metaState);
                    if (altUnicode == '|') { onCharInput('|'); return true; }
                    if (altUnicode == '\\') { onCharInput('\\'); return true; }
                    // Also keep explicit keycode fallbacks for Q5
                    if (keyCode == KeyEvent.KEYCODE_SPACE) { onCharInput('|'); return true; }
                    if (keyCode == KeyEvent.KEYCODE_ENTER) { onCharInput('\\'); return true; }
                }
            }

            if (keyCode == KeyEvent.KEYCODE_ENTER) {
                onEnter();
                return true;
            }

            if (keyCode == KeyEvent.KEYCODE_DEL) {
                onBackspace();
                return true;
            }

            if (keyCode == KeyEvent.KEYCODE_TAB) {
                onCharInput('\t');
                return true;
            }

            if (keyCode == KeyEvent.KEYCODE_ESCAPE) {
                if (mIsAnimating) {
                    mIsAnimating = false;
                    return true;
                }
                if (mSession != null) mSession.write(new byte[]{0x1B});
                return true;
            }

            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                onHistoryUp();
                return true;
            }

            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                onHistoryDown();
                return true;
            }

            // Check if CTRL is active (Volume Down held, or META_CTRL_ON from hardware)
            boolean isCtrl = mVolumeDownHeld || ((event.getMetaState() & KeyEvent.META_CTRL_ON) != 0);
            if (isCtrl) {
                mVolumeDownHeld = false; // Consumed as combination
                int u = event.getUnicodeChar();
                if (u <= 0 && keyCode >= KeyEvent.KEYCODE_A && keyCode <= KeyEvent.KEYCODE_Z) {
                    u = 'a' + (keyCode - KeyEvent.KEYCODE_A);
                }
                char lower = Character.toLowerCase((char) u);
                if (lower == 'c' || keyCode == KeyEvent.KEYCODE_C) {
                    onCtrlC();
                    return true;
                }
                if (lower == 'l' || keyCode == KeyEvent.KEYCODE_L) {
                    mTerminalView.clearScreen();
                    return true;
                }
                if (lower == 'u' || keyCode == KeyEvent.KEYCODE_U) {
                    replaceInputLine("");
                    return true;
                }
                if (lower >= 'a' && lower <= 'z') {
                    byte ctrlCode = (byte) (lower - 'a' + 1);
                    if (mSession != null) mSession.write(new byte[]{ctrlCode});
                    return true;
                }
            }

            // Physical character resolution on BlackBerry Q5
            int metaState = event.getMetaState();
            int unicode = event.getUnicodeChar(metaState);
            if (unicode <= 0) {
                unicode = event.getUnicodeChar();
            }

            // Direct hardware fallback for BlackBerry Q5 keyboard layout
            if (unicode <= 0) {
                if (keyCode >= KeyEvent.KEYCODE_A && keyCode <= KeyEvent.KEYCODE_Z) {
                    boolean shift = event.isShiftPressed();
                    unicode = (shift ? 'A' : 'a') + (keyCode - KeyEvent.KEYCODE_A);
                } else if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
                    unicode = '0' + (keyCode - KeyEvent.KEYCODE_0);
                } else if (keyCode == KeyEvent.KEYCODE_SPACE) {
                    unicode = ' ';
                }
            }

            if (unicode >= 32 && unicode <= 126) {
                onCharInput((char) unicode);
                return true;
            }
        } else if (event.getAction() == KeyEvent.ACTION_MULTIPLE && event.getKeyCode() == KeyEvent.KEYCODE_UNKNOWN) {
            String chars = event.getCharacters();
            if (chars != null) {
                for (int i = 0; i < chars.length(); i++) {
                    char c = chars.charAt(i);
                    if (c == '\n' || c == '\r') {
                        onEnter();
                    } else if (c >= 32 && c <= 126) {
                        onCharInput(c);
                    }
                }
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mIsAnimating = false;
        if (mSession != null) {
            mSession.kill();
        }
    }
}
