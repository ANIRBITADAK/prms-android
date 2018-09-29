package com.nonprofit.aananth.prms;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.FileChannel;

import static android.provider.AlarmClock.EXTRA_MESSAGE;
import static com.nonprofit.aananth.prms.MainActivity.CREATE_FILE;
import static com.nonprofit.aananth.prms.MainActivity.OPEN_FILE;
import static com.nonprofit.aananth.prms.MainActivity.PACKAGE_NAME;

import static com.nonprofit.aananth.prms.PatientDB.MAIN_DATABASE;


public class DbMgmtActivity extends AppCompatActivity {
    private String TAG = "PRMS-DbMgmtActivity";
    private String BACKUPFILE = "prms-backup.db";
    private EditText dbActMessageText;
    private Boolean activityStarted = false;
    static private Boolean dbOperationActive = false;

    private DoctorDB doctorDB;
    private PatientDB patientDB;
    private TreatmentDB treatmentDB;

    //private Handler importDB_Handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_db_mgmt);

        // Establish connections to databases
        patientDB = new PatientDB(this);
        treatmentDB = new TreatmentDB(this);
        doctorDB = new DoctorDB(this);

    }


    public void onResume() {
        super.onResume();

        if (activityStarted) {
            return;
        }
        else {
            activityStarted = true;
        }

        // Get the Intent that started this activity and extract the string
        Intent intent = getIntent();
        String str = intent.getStringExtra(EXTRA_MESSAGE);
        dbActMessageText = (EditText) findViewById(R.id.dbActMessage);

        if (str.equals("import database")) {
            dbActMessageText.setText("Importing Database ...");
            int action = (int) intent.getSerializableExtra("action");
            createFileOpenDialog(action);
        }
        else if (str.equals("export database")) {
            dbActMessageText.setText("Exporting Database ...");
            int action = (int) intent.getSerializableExtra("action");
            createFileOpenDialog(action);
        }
        else if (str.equals("backup database")) {
            dbActMessageText.setText("Backing up database...");
            doDatabaseBackup(BACKUPFILE, true);
            finish();
        }
        else if (str.equals("find duplicates")) {
            dbActMessageText.setText("Finding duplicates in Patient Database ...");
        }
        else {
            Log.d(TAG, "onCreate: Error illegal EXTRA_MESSAGE");
        }
    }


    // D A T A B A S E   E X P O R T   /   I M P O R T   O P E R A T I O N S
    public void doDatabaseBackup(String backupfile, boolean force) {
        String backuppath = getBackupFilePath(backupfile);

        // save backups if any database failed
        if (patientDB.isDbChanged() || treatmentDB.isDbChanged() || doctorDB.isDbChanged() || force) {
            Log.d(TAG, "onPause: creating " + backuppath);

            // backup databases and notify the event to all DB interface classes
            this.exportDB(backuppath);
            patientDB.DbSaved();
            treatmentDB.DbSaved();
            doctorDB.DbSaved();
            Toast.makeText(getBaseContext(), "Database backed up to " + backupfile,
                    Toast.LENGTH_SHORT).show();
        }
    }

    // invoked from menu to import or export database
    private void createFileOpenDialog(int action) {
        Intent intent = new Intent()
                .setType("*/*");
        String msg;

        if (action == CREATE_FILE) {
            intent.setAction(Intent.ACTION_CREATE_DOCUMENT);
            msg = "Create file";
        }
        else if (action == OPEN_FILE){
            intent.setAction(Intent.ACTION_GET_CONTENT);
            msg = "Select a file";
        }
        else {
            Log.d(TAG, "Illegal action passed to createFileOpenDialog()");
            return;
        }

        startActivityForResult(Intent.createChooser(intent, msg), action);
    }

    private String convertUriToFilePath(Uri uri) {
        String[] projection = {MediaStore.MediaColumns.DISPLAY_NAME};
        String path = null;

        ContentResolver cr = getApplicationContext().getContentResolver();
        Cursor metaCursor = cr.query(uri, projection, null, null, null);
        if (metaCursor != null) {
            try {
                if (metaCursor.moveToFirst()) {
                    metaCursor.moveToFirst();
                    path = metaCursor.getString(0);
                    Log.d(TAG, "convertUriToFilePath(): path = "+path);
                }
            } finally {
                metaCursor.close();
            }
        }
        else {
            Log.d(TAG, "convertUriToFilePath(): metaCursor is null!!");
        }

        //TODO: Add designs to find full path, till then use "Download" dir
        return "/Download/"+path;
    }

    static public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    private String getBackupFilePath(String filename) {
        String storagepath = Environment.getExternalStorageDirectory().toString();
        String outpath = storagepath + "/Download/" + filename;

        File file = new File(outpath);
        if (!file.exists()) {
            try {
                if (file.createNewFile()) {
                    OutputStream fo = new FileOutputStream(file);
                    byte data[] = {'d', 'u', 'm', 'm', 'y'};
                    fo.write(data);
                    fo.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return outpath;
    }

    /*
    private Date getFileDate(String fpath) {
        File file = new File(fpath);
        Date lastModDate = new Date(file.lastModified());

        Log.d(TAG, "File " + fpath + " modified date: " + lastModDate.toString());
        return lastModDate;
    }
    */

    // This function copies the MAIN_DATABASE used by this app to external storage path
    private void exportDB(String outpath) {
        try {
            File sd = Environment.getExternalStorageDirectory();
            File data = Environment.getDataDirectory();

            if (isExternalStorageWritable()) {
                String  appDBpath = "/data/" + PACKAGE_NAME
                        + "/databases/" + MAIN_DATABASE;
                File in_file = new File(data, appDBpath);
                File out_file = new File(sd, outpath);
                if (!out_file.exists()) {
                    out_file = new File(outpath);
                }
                if (out_file.exists()) {
                    out_file.delete();
                }

                FileChannel src = new FileInputStream(in_file).getChannel();
                FileChannel dst = new FileOutputStream(out_file).getChannel();
                Log.i(TAG, "Exporting "+ src.size()+ " bytes from "+appDBpath+ " to "+ outpath);
                dst.transferFrom(src, 0, src.size());
                src.close();
                dst.close();
            }
            else {
                Log.d(TAG, "exportDB(): Can't write into external storage!");
            }
        } catch (Exception e) {
            Log.d(TAG, "Got exception! "+ e.toString());
        }
        Toast.makeText(getBaseContext(), "Export database completed!",
                Toast.LENGTH_SHORT).show();
    }

    // This function merges databases
    public void importDB(String inpath) {
        // first backup the current data
        doDatabaseBackup("prms-last-backup.db", true);
        Toast.makeText(getBaseContext(), "Merging databases is happening in background",
                Toast.LENGTH_SHORT).show();
        final String inPath = inpath;

        dbOperationActive = true;
        runUiUpdateThread();
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                // merging starts from Patients so that their treatment and doctors are merged
                final String merged_filepath = patientDB.mergeDB(inPath, treatmentDB);

                // closes databases so that the user will be forced to re-login to re-init databases
                treatmentDB.close();
                patientDB.close();
                doctorDB.close();

                try {
                    File data  = Environment.getDataDirectory();

                    if (isExternalStorageWritable()) {
                        String  appDBpath = "/data/" + PACKAGE_NAME + "/databases/" + MAIN_DATABASE;
                        File out_file = new File(data, appDBpath);
                        File in_file = new File(merged_filepath);

                        FileChannel dst = new FileOutputStream(out_file).getChannel();
                        FileChannel src = new FileInputStream(in_file).getChannel();
                        Log.i(TAG, "Importing "+ src.size()+ " bytes from "+merged_filepath+
                                " to "+ appDBpath );
                        dst.transferFrom(src, 0, src.size());
                        src.close();
                        dst.close();
                        in_file.delete(); // delete temp file
                        doDatabaseBackup(BACKUPFILE, true);
                    }
                    else {
                        Log.d(TAG, "importDB(): Can't write into external storage!");
                    }
                } catch (Exception e) {
                    Log.d(TAG, "importDB(): Got exception! "+ e.toString());
                }

                Log.d(TAG, "importDB(): Finished import+merge; restarting MainActivity!");
                // Restart the app to read the new imported Database
                Intent i = getBaseContext().getPackageManager()
                        .getLaunchIntentForPackage( getBaseContext().getPackageName() );
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                dbOperationActive = false;
                finish();
                startActivity(i);
            }
        });
    }

    private void runUiUpdateThread() {
        new Thread() {
            public void run() {
                while (dbOperationActive) {
                    try {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                String str = patientDB.getStatusString();
                                dbActMessageText.setText(str);
                            }
                        });
                        Thread.sleep(250);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }.start();
    }


    // Activity Results
    @Override
    public void onActivityResult(int requestCode, int resultCode,
                                 Intent resultData) {
        super.onActivityResult(requestCode, resultCode, resultData);

        String path = null;
        Uri uri = null;
        // The ACTION_OPEN_DOCUMENT intent was sent with the request code
        // READ_REQUEST_CODE. If the request code seen here doesn't match, it's the
        // response to some other intent, and the code below shouldn't run at all.
        switch (requestCode) {
            case CREATE_FILE:
                if (resultCode == Activity.RESULT_OK) {
                    // The document selected by the user won't be returned in the intent.
                    // Instead, a URI to that document will be contained in the return intent
                    // provided to this method as a parameter.
                    // Pull that URI using resultData.getData().
                    if (resultData != null) {
                        uri = resultData.getData();
                        path = convertUriToFilePath(uri);
                        Log.i(TAG, "Create file: " + path);
                        exportDB(path);
                        finish();
                    }
                }
                break;

            case OPEN_FILE:
                if (resultCode == Activity.RESULT_OK) {
                    if (resultData != null) {
                        uri = resultData.getData();
                        path = convertUriToFilePath(uri);
                        Log.i(TAG, "Open file: " + path);
                        importDB(path);
                    }
                }
                break;
        }
    }
}
