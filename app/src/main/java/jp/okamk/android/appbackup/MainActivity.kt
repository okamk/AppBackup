package jp.okamk.android.appbackup

import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.*

class MainActivity : AppCompatActivity() {

    private val TAG = "AppBackup"
    private val UIMESSAGE_PREPARE_BACKUP = 0
    private val UIMESSAGE_CANCEL_BACKUP = 1
    private val UIMESSAGE_BACKUP_START = 2
    private val UIMESSAGE_PROGRESS_START = 3
    private val UIMESSAGE_PROGRESS_END = 4
    private val UIMESSAGE_BACKUP_END = 5

    private var isCanceled = false
    private var progressDialog: ProgressDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        buttonBackup.setOnClickListener { mHanlder.sendEmptyMessage(UIMESSAGE_PREPARE_BACKUP) }
    }

    private var runnable = Runnable {
        val stored = ArrayList<String>()
        val failed = ArrayList<String>()

        val applications = applications

        val messageBackupStart = mHanlder.obtainMessage(UIMESSAGE_BACKUP_START, applications.size)
        mHanlder.sendMessage(messageBackupStart)

        applications.forEach { appInfo ->
            if (isCanceled) {
                Log.v(TAG, "cancel detected")
                return@forEach
            }
            // ソースパス
            val sourceDir = appInfo.publicSourceDir
            val packageInfo = packageManager.getPackageInfo(appInfo.packageName, PackageManager.GET_META_DATA)
            // 保存先apkファイル名
            val saveAppName: String =
                    // アプリケーションラベル
                    appInfo.loadLabel(packageManager) as String +
                            try {
                                //バージョン名
                                " ${packageInfo.versionName}"
                            } catch (e: PackageManager.NameNotFoundException) {
                                e.printStackTrace()
                                ""
                            } +
                            ".apk"

            val messageAppStart = mHanlder.obtainMessage(UIMESSAGE_PROGRESS_START, saveAppName)
            mHanlder.sendMessage(messageAppStart)

            if (saveApplication(sourceDir, saveAppName)) {
                stored.add(saveAppName)
            } else {
                failed.add(saveAppName)
            }

            val messageAppEnd = mHanlder.obtainMessage(UIMESSAGE_PROGRESS_END, saveAppName)
            mHanlder.sendMessage(messageAppEnd)
        }
        val backupResult = String.format(getString(R.string.backup_result), stored.size, failed.size)
        val messageBackupEnd = mHanlder.obtainMessage(UIMESSAGE_BACKUP_END, backupResult)
        mHanlder.sendMessage(messageBackupEnd)
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private val mHanlder: Handler = @SuppressLint("HandlerLeak")
    object : Handler() {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                UIMESSAGE_PREPARE_BACKUP -> {
                    buttonBackup.isEnabled = false
                    isCanceled = false
                    Thread(runnable).start()
                }
                UIMESSAGE_CANCEL_BACKUP -> isCanceled = true
                UIMESSAGE_BACKUP_START -> {
                    val appCount = msg.obj as Int
                    Log.v(TAG, "Backup start appCount = $appCount")
                    progressDialog = ProgressDialog(this@MainActivity)
                    progressDialog?.setTitle(R.string.app_name)
                    progressDialog?.setMessage("")
                    progressDialog?.setButton(ProgressDialog.BUTTON_NEGATIVE,
                            getString(android.R.string.cancel)) { dialog, which ->
                        Log.v(TAG, "send MESSAGE_BACKUP_CANCEL")
                        sendEmptyMessage(UIMESSAGE_CANCEL_BACKUP)
                    }
                    progressDialog?.setCancelable(false)
                    progressDialog?.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
                    progressDialog?.max = appCount
                    progressDialog?.incrementProgressBy(0)
                    progressDialog?.show()
                }
                UIMESSAGE_PROGRESS_START -> {
                    val startAppName = msg.obj as String
                    Log.v(TAG, "Backup start appName = $startAppName")
                    progressDialog?.setMessage(getString(R.string.storing) + startAppName)
                }
                UIMESSAGE_PROGRESS_END -> {
                    val endAppName = msg.obj as String
                    Log.v(TAG, "Backup end appName = $endAppName")
                    progressDialog?.incrementProgressBy(1)
                }
                UIMESSAGE_BACKUP_END -> {
                    val backupResult = msg.obj as String
                    Log.v(TAG, "Backup end")
                    buttonBackup.isEnabled = true
                    if (progressDialog != null) {
                        progressDialog?.dismiss()
                        val alertDialog = AlertDialog.Builder(
                                this@MainActivity)
                        alertDialog.setTitle(R.string.app_name)
                        alertDialog.setMessage(backupResult)
                        alertDialog.setIcon(android.R.drawable.ic_dialog_info)
                        alertDialog.setPositiveButton(android.R.string.ok
                        ) { dialog, which -> dialog.dismiss() }
                        alertDialog.create().show()
                    }
                }
            }
        }
    }

    private val applications: List<ApplicationInfo>
        get() {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                packageManager.getInstalledApplications(
                        PackageManager.GET_META_DATA + PackageManager.MATCH_SYSTEM_ONLY
                )
            } else {
                val ret = ArrayList<ApplicationInfo>()
                packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                        .forEach {
                            if (it.flags and ApplicationInfo.FLAG_SYSTEM == 0) {
                                ret.add(it)
                            }
                        }
                ret
            }
        }

    // \/:*?"<>|
    private fun replace(s: String): String {
        var out = s.replace("\\", "_")
        out = out.replace("/", "_")
        out = out.replace(":", "_")
        out = out.replace("*", "_")
        out = out.replace("?", "_")
        out = out.replace("\"", "_")
        out = out.replace("<", "_")
        out = out.replace(">", "_")
        out = out.replace("|", "_")
        return out
    }

    private fun saveApplication(inPath: String, outFileName: String): Boolean {
        var ret = false
        val inFile = File(inPath)
        val outFile = File(getExternalFilesDir(null), replace(outFileName))
        // フォルダ作成
        outFile.parentFile.mkdirs()
        try {
            inFile.inputStream().buffered().use { inputStream ->
                outFile.outputStream().buffered().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            ret = true
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
        }
        if (ret) {
            mediascan(outFile)
        }
        return ret
    }

    private fun mediascan(file: File) {
        MediaScannerConnection.scanFile(this, arrayOf(file.absolutePath), null) { path, uri ->
            Log.v("MediaScanWork", "file $path was scanned seccessfully: $uri")
        }
    }
}