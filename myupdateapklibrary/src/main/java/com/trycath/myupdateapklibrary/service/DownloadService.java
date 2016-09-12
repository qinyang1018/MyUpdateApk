package com.trycath.myupdateapklibrary.service;

import android.app.IntentService;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.trycath.myupdateapklibrary.util.GetAppInfo;
import com.trycath.myupdateapklibrary.dialogactivity.ProgressBarActivity;
import com.trycath.myupdateapklibrary.dialogactivity.PromptDialogActivity;
import com.trycath.myupdateapklibrary.exception.CustomizeException;
import com.trycath.myupdateapklibrary.httprequest.DownloadServiceApi;
import com.trycath.myupdateapklibrary.listener.ProgressResponseListener;
import com.trycath.myupdateapklibrary.listener.ServiceGenerator;
import com.trycath.myupdateapklibrary.model.DownloadModel;
import com.trycath.myupdateapklibrary.util.FileUtils;
import com.trycath.myupdateapklibrary.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import okhttp3.ResponseBody;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

public class DownloadService extends IntentService implements ProgressResponseListener{
    private static final String TAG = "DownloadService";
    private NotificationCompat.Builder notificationBuilder;
    private NotificationManager notificationManager;
    private Subscription subscription;
    private String apkUrl = "";
    public DownloadService() {
        super("DownloadService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(TAG,"onHandleIntent");
        apkUrl = intent.getExtras().getString(PromptDialogActivity.INTENT_DOWNLOAD_URL);
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationBuilder = new NotificationCompat.Builder(this)
                .setContentTitle("Download")
                .setSmallIcon(GetAppInfo.getAppIconId(this))
                .setContentText("Downloading File")
                .setAutoCancel(true);
        notificationManager.notify(0, notificationBuilder.build());
        download();
        ProgressBarActivity.startActivity(this);
    }

    private void download() {
        Log.d(TAG,"download");
        final File outputFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "file.apk");
        DownloadServiceApi downloadService = ServiceGenerator.createResponseService(DownloadServiceApi.class, this);
        subscription = downloadService.download(apkUrl)
            .subscribeOn(Schedulers.io())
            .unsubscribeOn(Schedulers.io())
            .map(new Func1<ResponseBody, InputStream>() {
                @Override
                public InputStream call(ResponseBody responseBody) {
                    return responseBody.byteStream();
                }
            })
            .observeOn(Schedulers.computation())
            .doOnNext(new Action1<InputStream>() {
                @Override
                public void call(InputStream inputStream) {
                    try {
                        FileUtils.writeFile(inputStream, outputFile);
                    } catch (IOException e) {
                        e.printStackTrace();
                        throw new CustomizeException(e.getMessage(), e);
                    }
                }
            })
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(new Subscriber<InputStream>() {
                @Override
                public void onCompleted() {
                    
                }

                @Override
                public void onError(Throwable e) {

                }

                @Override
                public void onNext(InputStream inputStream) {

                }
            });
    }

    private void downloadCompleted() {
        DownloadModel download = new DownloadModel();
        download.setProgress(100);
        sendIntent(download);
        notificationManager.cancel(0);
        notificationBuilder.setProgress(0, 0, false);
        notificationBuilder.setContentText("File Downloaded");
        notificationManager.notify(0, notificationBuilder.build());
    }

    private void sendNotification(DownloadModel download) {
        sendIntent(download);
        notificationBuilder.setProgress(100, download.getProgress(), false);
        notificationBuilder.setContentText(StringUtils.getDataSize(download.getCurrentFileSize()) + "/" + StringUtils.getDataSize(download.getTotalFileSize()));
        notificationManager.notify(0, notificationBuilder.build());
    }

    private void sendIntent(DownloadModel download) {
        Intent intent = new Intent(ProgressBarActivity.MESSAGE_PROGRESS);
        intent.putExtra("download", download);
        LocalBroadcastManager.getInstance(DownloadService.this).sendBroadcast(intent);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.d(TAG,"onTaskRemoved");
        notificationManager.cancel(0);
        if(subscription!=null && !subscription.isUnsubscribed()){
            subscription.unsubscribe();
        }
    }
    
    public static void startDownloadService(Context context,String url){
        Log.d(TAG,"startDownloadService");
        Intent intent = new Intent(context, DownloadService.class);
        intent.putExtra(PromptDialogActivity.INTENT_DOWNLOAD_URL,url);
        context.startService(intent);
    }

    @Override
    public void onResponseProgress(long bytesRead, long contentLength, boolean done) {
        DownloadModel download = new DownloadModel();
        download.setTotalFileSize(contentLength);
        download.setCurrentFileSize(bytesRead);
        int progress = (int) ((bytesRead * 100) / contentLength);
        download.setProgress(progress);
        sendNotification(download);
    }
}
