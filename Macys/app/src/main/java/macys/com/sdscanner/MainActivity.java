package macys.com.sdscanner;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;

/**
 * Created by ravi
 */
public class MainActivity extends Activity implements View.OnClickListener, ScannerService.ScanUpdateListener{

    public static final String TAG = "SDScanner";

    private static final int PERMISSIONS_REQUEST_READ_STORAGE = 1;

    private ScannerService mBoundService;
    private boolean mIsBound;


    // UI
    private Button mStartSDScanButton;
    private Button mStopSDScanButton;
    private TextView mScanProgressTextView;
    private TextView mScanResultsTextView;

    private boolean mIsScanComplete;
    private StringBuilder mScanResultsBuilder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        setContentView(R.layout.activity_main);

        mStartSDScanButton = (Button)findViewById(R.id.start_scan);
        mStopSDScanButton = (Button)findViewById(R.id.stop_scan);

        mScanProgressTextView = (TextView)findViewById(R.id.scan_progress_tview);
        mScanResultsTextView = (TextView)findViewById(R.id.scan_result_tview);

        mStartSDScanButton.setOnClickListener(this);
        mStopSDScanButton.setOnClickListener(this);

        updateUI(true);

        startService(new Intent(MainActivity.this, ScannerService.class));
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");

        bindScannerService();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.d(TAG, "onConfigurationChanged");
    }

    @Override
    public void onClick(View v) {
        int resId = v.getId();
        switch(resId){
            case R.id.start_scan:
                verifyPermissions();
                break;
            case R.id.stop_scan:
                stopSDScan();
                break;
        }
    }

    private void verifyPermissions(){
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {

                showAlert(this, "Permission needed", "To read external storage, please provide permission to READ EXTERNAL STORAGE", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ActivityCompat.requestPermissions(MainActivity.this,
                                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                                PERMISSIONS_REQUEST_READ_STORAGE);
                    }
                });
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        PERMISSIONS_REQUEST_READ_STORAGE);
            }
        }else{
            startSDScan();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_READ_STORAGE: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    Log.d(TAG, "onRequestPermissionsResult: PERMISSION_GRANTED");
                    Toast.makeText(MainActivity.this, "Ready to SCAN external storage", Toast.LENGTH_SHORT).show();
                } else {
                    Log.d(TAG, "onRequestPermissionsResult: Permission denied");

                    showAlert(this, "Permission Failure", "For External storage scan permission is must", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    });
                }
                break;
            }

            default:
        }
    }

    private void updateUI(boolean enable){
        mStartSDScanButton.setEnabled(enable);
        mStopSDScanButton.setEnabled(!enable);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.d(TAG, "onCreateOptionsMenu");
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        Log.d(TAG, "onPrepareOptionsMenu");
        menu.findItem(R.id.menu_share_scan_results).setEnabled(mIsScanComplete);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.d(TAG, "onOptionsItemSelected");
        switch (item.getItemId()) {
            case R.id.menu_share_scan_results:
                shareScanResults();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void shareScanResults() {
        Intent sharingIntent = new Intent(Intent.ACTION_SEND);
        sharingIntent.setType("text/plain");
        sharingIntent.putExtra(Intent.EXTRA_SUBJECT, "SD Scan Results");
        sharingIntent.putExtra(Intent.EXTRA_TEXT, mScanResultsBuilder.toString());
        startActivity(Intent.createChooser(sharingIntent, "Share Scan Results"));
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop");
        boolean isFinishing = isFinishing();
        if(isFinishing){
            stopSDScan();
        }
        unBindScannerService();
        if(isFinishing){
            stopService();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            mBoundService = ((ScannerService.ScannerBinder)service).getService();
        }
        public void onServiceDisconnected(ComponentName className) {
            mBoundService = null;
        }
    };

    private void bindScannerService() {
        bindService(new Intent(MainActivity.this,
                ScannerService.class), mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
    }

    private void unBindScannerService() {
        if (mIsBound) {
            unbindService(mConnection);
            mIsBound = false;
        }
    }

    private void startSDScan(){
        if(mIsBound){
            updateUI(false);
            mScanProgressTextView.setText("Scan Started.");
            mScanResultsTextView.setText("");

            mBoundService.startSDScan(this);
        }else{
            Log.e(TAG, "stopSDScan: not bound yet");
        }
    }

    private void stopSDScan(){
        if(mIsBound){
            updateUI(true);
            mIsScanComplete = false;
            invalidateOptionsMenu();

            mBoundService.stopSDScan();
            mScanProgressTextView.setText("Scan Cancelled.");
        }else{
            Log.e(TAG, "stopSDScan: not bound yet");
        }
    }

    private void stopService(){
        stopService(new Intent(MainActivity.this, ScannerService.class));
    }

    private static void showAlert(Context context, CharSequence title, String message, DialogInterface.OnClickListener listener){
        AlertDialog alertDialog = new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, listener)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();

    }

    @Override
    public void onScanFile(File fileScanned) {
        Log.d(TAG, "onScanFile");
        if(null != fileScanned) {
            mScanProgressTextView.setText("Scanning... \n");
            mScanProgressTextView.append("File: " + fileScanned.getName());
        }
    }

    @Override
    public void onScanComplete(long avgFileSize, ScannerService.LargeFile[] biggestFiles, ArrayList<ScannerService.FileFrequency> frequentFiles) {
        Log.d(TAG, "onScanComplete");
        this.mIsScanComplete = true;
        invalidateOptionsMenu();
        updateUI(true);

        mScanProgressTextView.setText("Scan Complete.");

        mScanResultsBuilder = new StringBuilder();
        mScanResultsBuilder.append("--------------------------------------\n");
        mScanResultsBuilder.append("Avg. File Size: ");
        mScanResultsBuilder.append(avgFileSize + " Bytes.\n");
        mScanResultsBuilder.append("--------------------------------------\n");

        mScanResultsBuilder.append("\n");

        mScanResultsBuilder.append("--------------------------------------\n");
        mScanResultsBuilder.append("10 Biggest Files:\n");
        mScanResultsBuilder.append("--------------------------------------\n");
        for(int i = 0;i< biggestFiles.length;i++){
            if(biggestFiles[i] != null){
                mScanResultsBuilder.append("[" + (i+1) + "] " + biggestFiles[i].mFilenName);
                mScanResultsBuilder.append("\n");
                mScanResultsBuilder.append("(" + biggestFiles[i].mFileSize + " Bytes)");
                mScanResultsBuilder.append("\n");
            }
        }

        mScanResultsBuilder.append("\n");

        mScanResultsBuilder.append("--------------------------------------\n");
        mScanResultsBuilder.append("5 Frequent File Extensions:\n");
        mScanResultsBuilder.append("--------------------------------------\n");
        for(ScannerService.FileFrequency fileFrequency : frequentFiles){
            mScanResultsBuilder.append(fileFrequency.mFileExtension + " -> " + fileFrequency.mFreuency + " times.");
            mScanResultsBuilder.append("\n");
        }

        mScanResultsTextView.setText(mScanResultsBuilder.toString());
    }

    @Override
    public void onScanError(ScannerService.ScanError error) {
        switch (error){
            case SD_NOT_MOUNTED:
                mScanProgressTextView.setText("External Storage not mounted to scan!!");
                updateUI(true);
                break;                                                                                                                          
            default:
        }
    }
}
