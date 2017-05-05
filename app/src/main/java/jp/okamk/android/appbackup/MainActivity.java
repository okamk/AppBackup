package jp.okamk.android.appbackup;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "AppBackup";
    private HandlerThread th = null;
    private Handler mAsyncHanlder = null;
    private static final int UIMESSAGE_BACKUP_START = 1;
    private static final int UIMESSAGE_PROGRESS_START = 2;
    private static final int UIMESSAGE_PROGRESS_END = 3;
    private static final int UIMESSAGE_BACKUP_END = 4;
    private static final int MESSAGE_BACKUP_START = 1;
    private static final int MESSAGE_BACKUP_CANCEL = 2;
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
                backupApplications();
            }
        });

        th = new HandlerThread("AppBackup");
        th.start();
        mAsyncHanlder = new Handler(th.getLooper()) {
            boolean isCanceled = false;

            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MESSAGE_BACKUP_START:
                        isCanceled = false;
                        new Thread(r).start();
                        break;
                    case MESSAGE_BACKUP_CANCEL:
                        Log.v(TAG, "cancel message detected");
                        isCanceled = true;
                        break;
                }
            }

            Runnable r = new Runnable() {
                @Override
                public void run() {
                    ArrayList<String> stored = new ArrayList<String>();
                    ArrayList<String> failed = new ArrayList<String>();
                    PackageManager packageManager = getPackageManager();
                    List<ApplicationInfo> appInfos = getApplications();
                    Message messageBackupStart = mUIHanlder.obtainMessage(
                            UIMESSAGE_BACKUP_START, appInfos.size());
                    mUIHanlder.sendMessage(messageBackupStart);
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

                            Message messageAppStart = mUIHanlder.obtainMessage(
                                    UIMESSAGE_PROGRESS_START, saveAppName);
                            mUIHanlder.sendMessage(messageAppStart);
                            if (saveApplication(sourceDir, saveAppName)) {
                                stored.add(saveAppName);
                            } else {
                                failed.add(saveAppName);
                            }
                            Message messageAppEnd = mUIHanlder.obtainMessage(
                                    UIMESSAGE_PROGRESS_END, saveAppName);
                            mUIHanlder.sendMessage(messageAppEnd);
                        }
                    }
                    String backupResult = String.format(getString(R.string.backup_result), stored.size(), failed.size());
                    Message messageBackupEnd = mUIHanlder.obtainMessage(
                            UIMESSAGE_BACKUP_END, backupResult);
                    mUIHanlder.sendMessage(messageBackupEnd);
                }
            };
        };
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            th.quitSafely();
        } else {
            th.quit();
        }
    }

    private Handler mUIHanlder = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UIMESSAGE_BACKUP_START:
                    int appCount = (Integer) msg.obj;
                    Log.v(TAG, "Backup start appCount = " + appCount);
                    buttonBackup.setEnabled(false);

                    progressDialog = new ProgressDialog(MainActivity.this);
                    progressDialog.setTitle(R.string.app_name);
                    progressDialog.setMessage("");
                    progressDialog.setButton(ProgressDialog.BUTTON_NEGATIVE,
                            getString(android.R.string.cancel), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog,
                                                    int which) {
                                    Log.v(TAG, "send MESSAGE_BACKUP_CANCEL");
                                    mAsyncHanlder
                                            .sendEmptyMessage(MESSAGE_BACKUP_CANCEL);
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

    void backupApplications() {
        mAsyncHanlder.sendEmptyMessage(MESSAGE_BACKUP_START);
    }

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
        if (new File(outFile.getParent()).mkdirs()) {
            return ret;
        }

        FileInputStream inStream = null;
        FileOutputStream outStream = null;
        try {
            inStream = new FileInputStream(inFile);
            outStream = new FileOutputStream(outFile);

            byte[] buffer = new byte[1024];
            int total = 0;
            int read = -1;
            while ((read = inStream.read(buffer)) != -1) {
                total += read;
                outStream.write(buffer, 0, read);
            }
            // Log.v(TAG,
            // inPath + " is stored into " + outPath + " ("
            // + String.valueOf(total) + "bytes)");
            outStream.close();
            inStream.close();
            ret = true;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
        }
        return ret;
    }
}
