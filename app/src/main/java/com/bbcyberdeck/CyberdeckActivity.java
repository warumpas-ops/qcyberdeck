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

public class CyberdeckActivity extends Activity {

    private TerminalView mTerminalView;
    private TerminalSession mSession;

    private LinearLayout mLayoutInstalling;
    private TextView mTvInstallStatus;

    private Button mBtnCtrl;
    private Button mBtnAlt;

    private boolean mCtrlActive = false;
    private boolean mAltActive = false;

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
                        String msg = "\r\n\r\n\033[1;31m[Session closed with exit code " + exitCode + "]\033[0m\r\n"
                                + "\033[1;33mTap 'RESET' to restart session, or long-press 'RESET' to re-extract rootfs.\033[0m\r\n";
                        byte[] bytes = msg.getBytes();
                        mTerminalView.appendOutput(bytes, 0, bytes.length);
                        Toast.makeText(CyberdeckActivity.this, "Session closed (exit " + exitCode + ")", Toast.LENGTH_SHORT).show();
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
                if (mSession != null) {
                    mSession.kill();
                }
                mTerminalView.clearScreen();
                initSession();
                mSession.start();
                Toast.makeText(CyberdeckActivity.this, "Session restarted", Toast.LENGTH_SHORT).show();
            }
        });

        // Long-press reset forces re-installation
        btnRestart.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (mSession != null) {
                    mSession.kill();
                }
                File marker = new File(getFilesDir(), ".installed_v4");
                marker.delete();
                mTerminalView.clearScreen();
                checkAndStart();
                Toast.makeText(CyberdeckActivity.this, "Reinstalling rootfs...", Toast.LENGTH_SHORT).show();
                return true;
            }
        });
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
            public void onClick(View v) { sendByte((byte) '\t'); }
        });

        findViewById(R.id.btn_esc).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { sendByte((byte) 0x1B); }
        });

        findViewById(R.id.btn_pipe).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { sendString("|"); }
        });

        findViewById(R.id.btn_tilde).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { sendString("~"); }
        });

        findViewById(R.id.btn_slash).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { sendString("/"); }
        });

        findViewById(R.id.btn_backslash).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { sendString("\\"); }
        });

        findViewById(R.id.btn_dash).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { sendString("-"); }
        });

        findViewById(R.id.btn_up).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { sendString("\033[A"); }
        });

        findViewById(R.id.btn_down).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { sendString("\033[B"); }
        });

        findViewById(R.id.btn_left).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { sendString("\033[D"); }
        });

        findViewById(R.id.btn_right).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { sendString("\033[C"); }
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

    private void sendByte(byte b) {
        if (mSession != null) {
            mSession.write(new byte[]{b});
        }
    }

    private void sendString(String s) {
        if (mSession != null) {
            mSession.write(s);
        }
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

            // Handle special keys
            if (keyCode == KeyEvent.KEYCODE_ENTER) {
                sendByte((byte) '\r');
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DEL) {
                sendByte((byte) 0x7F); // ASCII DEL
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_TAB) {
                sendByte((byte) '\t');
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_ESCAPE) {
                sendByte((byte) 0x1B);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                sendString("\033[A");
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                sendString("\033[B");
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                sendString("\033[D");
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                sendString("\033[C");
                return true;
            }

            // If CTRL modifier is active
            if (mCtrlActive || (event.getMetaState() & KeyEvent.META_CTRL_ON) != 0) {
                mCtrlActive = false;
                updateModifierColors();

                int unicode = event.getUnicodeChar();
                if (unicode >= 'a' && unicode <= 'z') {
                    byte ctrlCode = (byte) (unicode - 'a' + 1);
                    sendByte(ctrlCode);
                    return true;
                } else if (unicode >= 'A' && unicode <= 'Z') {
                    byte ctrlCode = (byte) (unicode - 'A' + 1);
                    sendByte(ctrlCode);
                    return true;
                }
            }

            int unicode = event.getUnicodeChar();
            if (unicode > 0) {
                sendString(String.valueOf((char) unicode));
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
