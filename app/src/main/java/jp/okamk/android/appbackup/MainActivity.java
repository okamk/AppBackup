package jp.okamk.android.appbackup;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "AppBackup";

    private static final int UIMESSAGE_PREPARE_BACKUP = 0;
    private static final int UIMESSAGE_CANCEL_BACKUP = 1;
    private static final int UIMESSAGE_BACKUP_START = 2;
    private static final int UIMESSAGE_PROGRESS_START = 3;
    private static final int UIMESSAGE_PROGRESS_END = 4;
    private static final int UIMESSAGE_BACKUP_END = 5;
    boolean isCanceled = false;

    private Button buttonBackup = null;
    private ProgressDialog progressDialog = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        buttonBackup = (Button) findViewById(R.id.ButtonBackup);
        buttonBackup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                mHanlder.sendEmptyMessage(UIMESSAGE_PREPARE_BACKUP);
            }
        });
    }

    Runnable r = new Runnable() {
        @Override
        public void run() {
            ArrayList<String> stored = new ArrayList<String>();
            ArrayList<String> failed = new ArrayList<String>();
            PackageManager packageManager = getPackageManager();
            List<ApplicationInfo> appInfos = getApplications();
            Message messageBackupStart = mHanlder.obtainMessage(
                    UIMESSAGE_BACKUP_START, appInfos.size());
            mHanlder.sendMessage(messageBackupStart);
            if (appInfos != null) {
                for (ApplicationInfo appInfo : appInfos) {
                    if (isCanceled) {
                        Log.v(TAG, "cancel detected");
                        break;
                    }
                    // ソースパス
                    String sourceDir = appInfo.publicSourceDir;
                    // アプリケーションラベル
                    String appLabel = (String) appInfo
                            .loadLabel(packageManager);

                    // バージョン番号
                    String versionName = null;
                    try {
                        PackageInfo packageInfo = packageManager
                                .getPackageInfo(appInfo.packageName,
                                        PackageManager.GET_META_DATA);
                        if (packageInfo != null) {
                            versionName = packageInfo.versionName;
                        }
                    } catch (PackageManager.NameNotFoundException e) {
                        e.printStackTrace();
                    }

                    String saveAppName = appLabel
                            + (versionName != null ? " " : "")
                            + versionName + ".apk";

                    Message messageAppStart = mHanlder.obtainMessage(
                            UIMESSAGE_PROGRESS_START, saveAppName);
                    mHanlder.sendMessage(messageAppStart);
                    if (saveApplication(sourceDir, saveAppName)) {
                        stored.add(saveAppName);
                    } else {
                        failed.add(saveAppName);
                    }
                    Message messageAppEnd = mHanlder.obtainMessage(
                            UIMESSAGE_PROGRESS_END, saveAppName);
                    mHanlder.sendMessage(messageAppEnd);
                }
            }
            String backupResult = String.format(getString(R.string.backup_result), stored.size(), failed.size());
            Message messageBackupEnd = mHanlder.obtainMessage(
                    UIMESSAGE_BACKUP_END, backupResult);
            mHanlder.sendMessage(messageBackupEnd);
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private Handler mHanlder = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UIMESSAGE_PREPARE_BACKUP:
                    buttonBackup.setEnabled(false);
                    isCanceled = false;
                    new Thread(r).start();
                    break;
                case UIMESSAGE_CANCEL_BACKUP:
                    isCanceled = true;
                    break;
                case UIMESSAGE_BACKUP_START:
                    int appCount = (Integer) msg.obj;
                    Log.v(TAG, "Backup start appCount = " + appCount);

                    progressDialog = new ProgressDialog(MainActivity.this);
                    progressDialog.setTitle(R.string.app_name);
                    progressDialog.setMessage("");
                    progressDialog.setButton(ProgressDialog.BUTTON_NEGATIVE,
                            getString(android.R.string.cancel), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog,
                                                    int which) {
                                    Log.v(TAG, "send MESSAGE_BACKUP_CANCEL");
                                    sendEmptyMessage(UIMESSAGE_CANCEL_BACKUP);
                                }
                            });
                    progressDialog.setCancelable(false);
                    progressDialog
                            .setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                    progressDialog.setMax(appCount);
                    progressDialog.incrementProgressBy(0);
                    progressDialog.show();
                    break;
                case UIMESSAGE_PROGRESS_START:
                    String startAppName = (String) msg.obj;
                    Log.v(TAG, "Backup start appName = " + startAppName);
                    if (progressDialog != null) {
                        progressDialog.setMessage(getString(R.string.storing) + startAppName);
                    }
                    break;
                case UIMESSAGE_PROGRESS_END:
                    String endAppName = (String) msg.obj;
                    Log.v(TAG, "Backup end appName = " + endAppName);
                    if (progressDialog != null) {
                        progressDialog.incrementProgressBy(1);
                    }
                    break;
                case UIMESSAGE_BACKUP_END:
                    String backupResult = (String) msg.obj;
                    Log.v(TAG, "Backup end");
                    buttonBackup.setEnabled(true);
                    if (progressDialog != null) {
                        progressDialog.dismiss();
                        AlertDialog.Builder alertDialog = new AlertDialog.Builder(
                                MainActivity.this);
                        alertDialog.setTitle(R.string.app_name);
                        alertDialog.setMessage(backupResult);
                        alertDialog.setIcon(android.R.drawable.ic_dialog_info);
                        alertDialog.setPositiveButton(android.R.string.ok,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog,
                                                        int which) {
                                        dialog.dismiss();
                                    }
                                });
                        alertDialog.create().show();
                    }
                    break;
            }
        }
    };

    List<ApplicationInfo> getApplications() {
        List<ApplicationInfo> ret = new ArrayList<ApplicationInfo>();
        PackageManager packageManager = getPackageManager();
        List<ApplicationInfo> appInfos = packageManager
                .getInstalledApplications(PackageManager.GET_META_DATA);
        if (appInfos != null) {
            for (ApplicationInfo appInfo : appInfos) {
                if ((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                    ret.add(appInfo);
                }
            }
        }
        return ret;
    }

    // \/:*?"<>|
    String replace(String in) {
        in = in.replace("\\", "_");
        in = in.replace("/", "_");
        in = in.replace(":", "_");
        in = in.replace("*", "_");
        in = in.replace("?", "_");
        in = in.replace("\"", "_");
        in = in.replace("<", "_");
        in = in.replace(">", "_");
        in = in.replace("|", "_");
        return in;
    }

    boolean saveApplication(String inPath, String outFileName) {
        boolean ret = false;
        File inFile = new File(inPath);
        File outFile = new File(getExternalFilesDir(null), replace(outFileName));

        // フォルダ作成
        if (outFile.getParentFile().mkdirs()) {
            return ret;
        }

        BufferedInputStream in = null;
        BufferedOutputStream out = null;
        try {
            in = new BufferedInputStream(new FileInputStream(inFile));
            out = new BufferedOutputStream(new FileOutputStream(outFile));
            byte[] buffer = new byte[1024];
            int total = 0;
            int read = -1;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                out.write(buffer, 0, read);
            }
            ret = true;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        if (ret) {
            mediascan(outFile);
        }
        return ret;
    }

    void mediascan(File file) {
        MediaScannerConnection.scanFile(this,
                new String[]{file.getAbsolutePath()}, null,
                new MediaScannerConnection.OnScanCompletedListener() {
                    @Override
                    public void onScanCompleted(String path, Uri uri) {
                        Log.v("MediaScanWork", "file " + path
                                + " was scanned seccessfully: " + uri);
                    }
                });
    }
}
