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

    private void playTrainAnimation() {
        mIsAnimating = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                String[] smoke = new String[]{"(@@) (  )  ", "  (@@@) () ", " ( ) (@@)  "};
                for (int step = 0; step <= 36 && mIsAnimating; step += 2) {
                    StringBuilder frame = new StringBuilder();
                    frame.append("\u001B[2J\u001B[H\r\n\r\n\r\n\r\n");

                    String pad = getPadding(step);
                    String s = smoke[(step / 2) % 3];

                    frame.append(pad).append("      ").append(s).append("\r\n");
                    frame.append(pad).append("    ==== ____\r\n");
                    frame.append(pad).append("  __I__n [___]]\r\n");
                    frame.append(pad).append("-(_________)|  \r\n");
                    frame.append(pad).append("  (O)---(O)    \r\n");
                    frame.append("====================================================\r\n");

                    final byte[] b = frame.toString().getBytes();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mTerminalView.appendOutput(b, 0, b.length);
                        }
                    });
                    try { Thread.sleep(75); } catch (Exception ignored) {}
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

    private void playAquariumAnimation() {
        mIsAnimating = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (int f = 0; f < 35 && mIsAnimating; f++) {
                    int fish1X = (f * 2) % 36;
                    int fish2X = 36 - ((f * 2) % 36);

                    StringBuilder sb = new StringBuilder();
                    sb.append("\u001B[2J\u001B[H\r\n");
                    sb.append("\u001B[1;36m~~~~~~~~~~~~~~~ ASCII AQUARIUM ~~~~~~~~~~~~~~~~\u001B[0m\r\n\r\n");

                    // Row 1 (bubbles)
                    sb.append(getPadding((f * 3) % 40)).append(".\r\n");

                    // Row 2 (Fish 1 swimming right in yellow/gold)
                    sb.append(getPadding(fish1X)).append("\u001B[1;33m><(((*>\u001B[0m\r\n");

                    // Row 3 (bubbles)
                    sb.append(getPadding((f * 5 + 10) % 40)).append("o\r\n");

                    // Row 4 (Fish 2 swimming left in magenta)
                    sb.append(getPadding(fish2X)).append("\u001B[1;35m<*)))><\u001B[0m\r\n");

                    // Row 5 (small fish in cyan)
                    sb.append(getPadding((fish1X + 16) % 40)).append("\u001B[1;36m><>\u001B[0m\r\n");

                    // Row 6 (bubbles)
                    sb.append(getPadding((f * 7 + 22) % 40)).append("O\r\n\r\n");

                    // Swaying seaweed in green
                    sb.append("\u001B[1;32m");
                    if (f % 2 == 0) {
                        sb.append("  ( ( (   ) ) )   ( ( (   ) ) )   ( ( (   ) ) ) \r\n");
                        sb.append("   ) ) ) ( ( (     ) ) ) ( ( (     ) ) ) ( ( (  \r\n");
                    } else {
                        sb.append("   ) ) ) ( ( (     ) ) ) ( ( (     ) ) ) ( ( (  \r\n");
                        sb.append("  ( ( (   ) ) )   ( ( (   ) ) )   ( ( (   ) ) ) \r\n");
                    }
                    sb.append("^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^\u001B[0m\r\n");

                    final byte[] b = sb.toString().getBytes();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mTerminalView.appendOutput(b, 0, b.length);
                        }
                    });
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
        }).start();
    }

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

        // --- Animated Unix Classics ---

        if (cmd.equals("sl") || cmd.equals("train")) {
            playTrainAnimation();
            return;
        }

        if (cmd.equals("fish") || cmd.equals("aqua") || cmd.equals("aquarium")) {
            playAquariumAnimation();
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
                "  QCyberdeck Commands Reference\r\n" +
                "================================================\r\n" +
                "  Animated : sl (train), fish (asciiquarium)\r\n" +
                "  ASCII    : skull, cowsay <msg>, deck, clock, matrix\r\n" +
                "  Telemetry: battery, sysinfo, uname -a, uptime\r\n" +
                "  Identity : whoami, id\r\n" +
                "  Files    : tree, ls -la, pwd, cd, mkdir, rm, cp, mv, vi\r\n" +
                "  Network  : ifconfig, netstat, ping, nc, wget\r\n" +
                "  Archives : tar -czvf, gzip\r\n" +
                "  Tools    : grep, awk, sed, find, strings, hexdump\r\n" +
                "  All 350+ : busybox --help\r\n" +
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
