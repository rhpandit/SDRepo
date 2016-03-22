package macys.com.sdscanner;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Created by ravi
 */
public class ScannerService extends Service{

    private static final String TAG = MainActivity.TAG;

    private NotificationManager mNM;
    private int NOTIFICATION = R.string.scanner_service_started;

    private final IBinder mBinder = new ScannerBinder();
    private ScanTask mScanTask;
    private ScanUpdateListener mScanUpdateListener;

    private long mTotalFiles;
    private long mTotalFileSize;

    private HashMap<String, Integer> mExtnHashmap = new HashMap<String, Integer>();

    // 10 Biggest files
    private LargeFile[] mBiggestFiles;

    public interface ScanUpdateListener{
        public void onScanFile(File fileScanned);
        public void onScanComplete(long avgFileSize, LargeFile[] biggestFiles, ArrayList<FileFrequency> frequentFiles);
        public void onScanError(ScanError sdNotMounted);
    }

    public enum ScanError{
        SD_NOT_MOUNTED
    }

    class LargeFile {
        public String mFilenName;
        public long mFileSize;
    }

    class FileFrequency{
        public String mFileExtension;
        public long mFreuency;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "ScannerService onCreate");
        mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "ScannerService onStartCommand");
        Log.i(TAG, "Received start id " + startId + ": " + intent);
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "ScannerService onBind");
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "ScannerService onUnbind");
        return super.onUnbind(intent);
    }

    public class ScannerBinder extends Binder {
        ScannerService getService() {
            return ScannerService.this;
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "ScannerService onDestroy");
        super.onDestroy();
    }

    private void showNotification() {
        CharSequence text = "Scanning SD Files...";

        Intent activityIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, activityIntent, 0);

        Notification notification = new Notification.Builder(this)
                .setSmallIcon(android.R.drawable.ic_menu_rotate)
                .setTicker(text)
                .setWhen(System.currentTimeMillis())
                .setContentTitle("SD Scanner")
                .setContentText(text)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
        mNM.notify(NOTIFICATION, notification);
    }

    public void startSDScan(ScanUpdateListener listener){
        Log.d(TAG, "startSDScan");
        this.mScanUpdateListener = listener;
        showNotification();
        cleanup();

        mScanTask = new ScanTask();
        mScanTask.execute();
    }

    private void cleanup() {
        mBiggestFiles = new LargeFile[10];
        mExtnHashmap.clear();
        mTotalFiles = 0;
        mTotalFileSize = 0;
    }

    public void stopSDScan(){
        Log.d(TAG, "stopSDScan");
        mNM.cancel(NOTIFICATION);

        this.mScanUpdateListener = null;
        if(null != mScanTask) {
            mScanTask.cancel(false);
        }
    }

    public class ScanTask extends AsyncTask<Void, File, Integer> {

        @Override
        protected Integer doInBackground(Void... extensions) {

            String externalStorageState = Environment.getExternalStorageState();
            Log.d(TAG, "externalStorageState: " + externalStorageState);
            if(Environment.MEDIA_MOUNTED.equals(externalStorageState)){
                try {
                    File externalStorageDirectory = Environment.getExternalStorageDirectory();
                    Log.d(TAG, "externalStorageDirectory: " + externalStorageDirectory);

                    scanFiles(externalStorageDirectory);

                } catch (IOException ex) {
                    ex.printStackTrace();
                }
                return 1;
            }else{
                return 0;
            }
        }

        @Override
        protected void onProgressUpdate(File... values) {
            super.onProgressUpdate(values);
            if(null != mScanUpdateListener){
                File fileScanned = values[0];
                mScanUpdateListener.onScanFile(fileScanned);
            }
        }

        @Override
        protected void onPostExecute(Integer result) {
            super.onPostExecute(result);

            Log.d(TAG, "onPostExecute");
            mNM.cancel(NOTIFICATION);

            if(result == 1){
                long avgFileSize = 0;
                if(mTotalFiles > 0) {
                    avgFileSize = mTotalFileSize / mTotalFiles;
                }

                List<Map.Entry<String, Integer>> list = new LinkedList<Map.Entry<String, Integer>>( mExtnHashmap.entrySet());
                Collections.sort(list, new Comparator<Map.Entry<String, Integer>>() {
                    public int compare(Map.Entry<String, Integer> o1,
                                       Map.Entry<String, Integer> o2) {
                        int val1 = o1.getValue();
                        int val2 = o2.getValue();
                        return (val2 - val1);
                    }
                });
                Iterator<Map.Entry<String, Integer>> it = list.iterator();

                ArrayList<FileFrequency> mFrequentFileList = new ArrayList<>();
                int j = 0;
                while (it.hasNext() && j < 5) {
                    Map.Entry<String, Integer> pair = it.next();
                    Log.d(TAG, pair.getKey() + " = " + pair.getValue());
                    FileFrequency frequentFile = new FileFrequency();
                    frequentFile.mFileExtension = pair.getKey();
                    frequentFile.mFreuency = pair.getValue();
                    mFrequentFileList.add(frequentFile);
                    j++;
                }

                if(null != mScanUpdateListener){
                    mScanUpdateListener.onScanComplete(avgFileSize, mBiggestFiles, mFrequentFileList);
                }else{
                    Log.d(TAG, "mScanUpdateListener is null");
                }
            }else{
                if(null != mScanUpdateListener){
                    mScanUpdateListener.onScanError(ScanError.SD_NOT_MOUNTED);
                }else{
                    Log.d(TAG, "mScanUpdateListener is null");
                }
            }
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
        }

        public void scanFiles(File root) throws IOException{
            File[] list = root.listFiles();

            if(!isCancelled()){
                if (null != list) {
                    if(!isSymLink(root)){
                        for (int i = 0; i < list.length; i++) {
                            if (!isCancelled()) {
                                if (list[i].isDirectory()) {
                                    Log.d(TAG, "Directory Name " + list[i].getAbsoluteFile());
                                    scanFiles(list[i]);
                                } else {
                                    File file = list[i].getAbsoluteFile();
                                    Log.d(TAG, "File Name: " + file);

                                    publishProgress(file);

                                    String fileName = file.getName();
                                    long fileSize = file.length();
                                    int fileNameLength = fileName.length();

                                    // File size check
                                    mTotalFiles++;
                                    mTotalFileSize += fileSize;

                                    validateFileSize(file);

                                    // File extension check
                                    String fileExtn = fileName.substring(fileName.lastIndexOf('.') + 1, fileNameLength);
                                    if (mExtnHashmap.containsKey(fileExtn)) {
                                        int freqCount = mExtnHashmap.get(fileExtn);
                                        mExtnHashmap.put(fileExtn, freqCount + 1);
                                    } else {
                                        mExtnHashmap.put(fileExtn, 1);
                                    }

                                }
                            }else{
                                Log.e(TAG, "scanFiles: already cancelled");
                            }
                        }
                    }else{
                        Log.e(TAG, "Skipping symbolic link file " + root.getName());
                    }
                }else {
                    Log.e(TAG, "scanFiles: list is null");
                }
            }else{
                Log.e(TAG, "scanFiles: already cancelled");
            }
        }

        public boolean isSymLink(File file) throws IOException{
            boolean isSymLink = !(file.getCanonicalFile().equals(file.getAbsoluteFile()));
            return isSymLink;
        }

        private void validateFileSize(File newfile) {
            long fileSize = newfile.length();
            int indexToInsert = -1;

            for (int i = 0; i <  mBiggestFiles.length; i++) {
                if (mBiggestFiles[i] != null) {
                    if (fileSize > mBiggestFiles[i].mFileSize) {
                        indexToInsert = i;
                        break;
                    }else{
                        continue;
                    }
                }else{
                    indexToInsert = i;
                    break;
                }
            }

            if (indexToInsert != -1) {
                arrangeFileItems(newfile, indexToInsert);
            }
        }

        private void arrangeFileItems(File newFile, int index) {
            if (mBiggestFiles[index] != null) {
                for(int i = mBiggestFiles.length - 1; i >= index+1;i--){
                    mBiggestFiles[i] = mBiggestFiles[i - 1];
                }
            }
            mBiggestFiles[index] = new LargeFile();
            mBiggestFiles[index].mFilenName = newFile.getName();
            mBiggestFiles[index].mFileSize = newFile.length();
        }
    }
}
