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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
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
            File marker = new File(getFilesDir(), ".installed_v10");
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
                "  QCyberdeck Canonical Ubuntu Extensions\r\n" +
                "================================================\r\n" +
                "  Ubuntu Classics : sl, asciiquarium, cmatrix, aafire, pipes, nyancat\r\n" +
                "  ASCII Fun       : fortune, cowsay <msg>, cowthink <msg>, skull, deck, clock\r\n" +
                "  Telemetry       : battery, sysinfo, uname -a, uptime\r\n" +
                "  Identity        : whoami, id\r\n" +
                "  Files           : tree, ls -la, pwd, cd, mkdir, rm, cp, mv, vi\r\n" +
                "  Network         : ifconfig, netstat, ping, nc, wget\r\n" +
                "  Archives        : tar -czvf, gzip\r\n" +
                "  Tools           : grep, awk, sed, find, strings, hexdump\r\n" +
                "  All 350+        : busybox --help\r\n" +
                "================================================\r\n\r\n";
            byte[] b = help.getBytes();
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

            // Check if CTRL is pressed on hardware keyboard
            if ((event.getMetaState() & KeyEvent.META_CTRL_ON) != 0) {
                int u = event.getUnicodeChar();
                if (u == 'c' || u == 'C' || keyCode == KeyEvent.KEYCODE_C) {
                    onCtrlC();
                    return true;
                }
                if (u == 'l' || u == 'L' || keyCode == KeyEvent.KEYCODE_L) {
                    mTerminalView.clearScreen();
                    return true;
                }
                if (u >= 'a' && u <= 'z') {
                    byte ctrlCode = (byte) (u - 'a' + 1);
                    if (mSession != null) mSession.write(new byte[]{ctrlCode});
                    return true;
                } else if (u >= 'A' && u <= 'Z') {
                    byte ctrlCode = (byte) (u - 'A' + 1);
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
