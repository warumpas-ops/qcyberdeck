package com.bbcyberdeck;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class CyberdeckActivity extends Activity {

    private TerminalView mTerminalView;
    private TerminalSession mSession;

    private LinearLayout mLayoutInstalling;
    private TextView mTvInstallStatus;

    private Button mBtnCtrl;
    private Button mBtnAlt;

    private boolean mCtrlActive = false;
    private boolean mAltActive = false;

    // Line buffer and command history for seamless terminal interaction
    private final StringBuilder mInputLine = new StringBuilder();
    private final List<String> mHistory = new ArrayList<String>();
    private int mHistoryIndex = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cyberdeck);

        mTerminalView = (TerminalView) findViewById(R.id.terminal_view);
        mLayoutInstalling = (LinearLayout) findViewById(R.id.layout_installing);
        mTvInstallStatus = (TextView) findViewById(R.id.tv_install_status);

        setupTopBar();
        setupBottomBar();

        initSession();
        checkAndStart();
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
                        String msg = "\r\n\r\n\u001B[1;31m[Session finished: exit code " + exitCode + "]\u001B[0m\r\n"
                                + "\u001B[1;33mTap 'RESET' at top right to restart session.\u001B[0m\r\n";
                        byte[] bytes = msg.getBytes();
                        mTerminalView.appendOutput(bytes, 0, bytes.length);
                        Toast.makeText(CyberdeckActivity.this, "Session finished (" + exitCode + ")", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void setupTopBar() {
        Button btnTheme = (Button) findViewById(R.id.btn_theme);
        btnTheme.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mTerminalView.cycleTheme();
            }
        });

        Button btnRestart = (Button) findViewById(R.id.btn_restart);
        btnRestart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                restartSession(false);
            }
        });

        // Long-press reset forces complete re-installation of core binaries
        btnRestart.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                restartSession(true);
                return true;
            }
        });
    }

    private void restartSession(boolean reinstall) {
        if (mSession != null) {
            mSession.kill();
        }
        mInputLine.setLength(0);
        mTerminalView.clearScreen();
        if (reinstall) {
            File marker = new File(getFilesDir(), ".installed_v5");
            marker.delete();
            checkAndStart();
            Toast.makeText(this, "Reinstalling core...", Toast.LENGTH_SHORT).show();
        } else {
            initSession();
            mSession.start();
            Toast.makeText(this, "Session restarted", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupBottomBar() {
        mBtnCtrl = (Button) findViewById(R.id.btn_ctrl);
        mBtnCtrl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCtrlActive = !mCtrlActive;
                updateModifierColors();
            }
        });

        mBtnAlt = (Button) findViewById(R.id.btn_alt);
        mBtnAlt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mAltActive = !mAltActive;
                updateModifierColors();
            }
        });

        findViewById(R.id.btn_tab).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { onCharInput('\t'); }
        });

        findViewById(R.id.btn_esc).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mSession != null) mSession.write(new byte[]{0x1B});
            }
        });

        findViewById(R.id.btn_pipe).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { onCharInput('|'); }
        });

        findViewById(R.id.btn_tilde).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { onCharInput('~'); }
        });

        findViewById(R.id.btn_slash).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { onCharInput('/'); }
        });

        findViewById(R.id.btn_backslash).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { onCharInput('\\'); }
        });

        findViewById(R.id.btn_dash).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { onCharInput('-'); }
        });

        findViewById(R.id.btn_up).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { onHistoryUp(); }
        });

        findViewById(R.id.btn_down).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { onHistoryDown(); }
        });

        findViewById(R.id.btn_left).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Future cursor navigation
            }
        });

        findViewById(R.id.btn_right).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Future cursor navigation
            }
        });
    }

    private void updateModifierColors() {
        if (mCtrlActive) {
            mBtnCtrl.setBackgroundColor(Color.rgb(0, 200, 80));
            mBtnCtrl.setTextColor(Color.BLACK);
        } else {
            mBtnCtrl.setBackgroundColor(Color.rgb(30, 40, 54));
            mBtnCtrl.setTextColor(Color.WHITE);
        }

        if (mAltActive) {
            mBtnAlt.setBackgroundColor(Color.rgb(220, 150, 0));
            mBtnAlt.setTextColor(Color.BLACK);
        } else {
            mBtnAlt.setBackgroundColor(Color.rgb(30, 40, 54));
            mBtnAlt.setTextColor(Color.WHITE);
        }
    }

    // --- Line Editing & Local Echo Core ---

    private void onCharInput(char c) {
        mInputLine.append(c);
        mTerminalView.scrollToBottom();
        // Instant visual feedback for physical keyboard
        mTerminalView.appendOutput(new byte[]{(byte) c}, 0, 1);
    }

    private void onBackspace() {
        if (mInputLine.length() > 0) {
            mInputLine.deleteCharAt(mInputLine.length() - 1);
            mTerminalView.scrollToBottom();
            // Erase character from terminal: backspace, space, backspace
            mTerminalView.appendOutput(new byte[]{'\b', ' ', '\b'}, 0, 3);
        }
    }

    private void onEnter() {
        String cmd = mInputLine.toString();
        mInputLine.setLength(0);

        if (cmd.trim().length() > 0) {
            mHistory.add(cmd);
            mHistoryIndex = mHistory.size();
        }

        mTerminalView.scrollToBottom();
        mTerminalView.appendOutput(new byte[]{'\r', '\n'}, 0, 2);

        if (mSession != null) {
            mSession.write(cmd + "\n");
        }
    }

    private void onCtrlC() {
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
                            mTvInstallStatus.setText("Installation Error: " + error);
                        }
                    });
                }
            });
        } else {
            mLayoutInstalling.setVisibility(View.GONE);
            mSession.start();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();

            // Hardware Back button on BlackBerry: clear current line if typing, else send ESC
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (mInputLine.length() > 0) {
                    replaceInputLine("");
                } else if (mSession != null) {
                    mSession.write(new byte[]{0x1B});
                }
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

            // Check CTRL modifier combinations
            if (mCtrlActive || (event.getMetaState() & KeyEvent.META_CTRL_ON) != 0) {
                mCtrlActive = false;
                updateModifierColors();

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

            // Resolve Alt modifier for BlackBerry hardware keys
            int metaState = event.getMetaState();
            if (mAltActive) {
                metaState |= KeyEvent.META_ALT_ON;
                mAltActive = false;
                updateModifierColors();
            }

            int unicode = event.getUnicodeChar(metaState);
            if (unicode <= 0) {
                unicode = event.getUnicodeChar();
            }

            // Robust fallback for physical keys on BlackBerry Q5
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
        if (mSession != null) {
            mSession.kill();
        }
    }
}
