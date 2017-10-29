package com.nonprofit.aananth.prms;

import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatatypeMismatchException;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import static com.nonprofit.aananth.prms.PatientDB.DATABASE_VERSION;
import static com.nonprofit.aananth.prms.PatientDB.PATIENT_LIST;
import static com.nonprofit.aananth.prms.PatientDB.MAIN_DATABASE;

/**
 * Created by aananth on 06/05/17.
 */

public class TreatmentDB extends SQLiteOpenHelper {
    public static final String TREAT_ID = "tid";
    private boolean mDbChanged = false;

    private String TAG = "PRMS-TreatmentDB";


    // Database creation sql statement
    public TreatmentDB(Context context) {
        super(context, MAIN_DATABASE, null, DATABASE_VERSION);
    }


    @Override
    public void onCreate(SQLiteDatabase database) {
        //database.execSQL(DATABASE_CREATE);
    }


    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        //Log.w(TreatmentDB.class.getName(),
        //        "Upgrading database from version " + oldVersion + " to "
        //                + newVersion + ", which will destroy all old data");
        //db.execSQL("DROP TABLE IF EXISTS " + PATIENT_LIST);
        //onCreate(db);
    }


    public void createTreatmentTableIfNotExist(SQLiteDatabase db, String tablename) {
        String query;

        query = "CREATE TABLE IF NOT EXISTS " + tablename +
                " ( " + TREAT_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, date VARCHAR(40), " +
                "complaint VARCHAR(2048), prescription VARCHAR(2048), doctor VARCHAR(100) )";

        Log.d(TAG, query);
        db.execSQL(query);
    }


    public void AddTreatment(Treatment treat) {
        SQLiteDatabase db = this.getWritableDatabase();

        AddTreatmentToDB(db, treat);
        db.close();
    }


    public void AddTreatmentToDB(SQLiteDatabase db, Treatment treat) {
        String tablename;

        tablename = treat.patient.Uid;
        createTreatmentTableIfNotExist(db, tablename);

        String query = "INSERT INTO " + tablename + " (date, complaint, prescription, doctor) VALUES ('" +
                treat.date + "', '" + treat.complaint + "', '" + treat.prescription + "', '" +
                treat.doctor + "')";

        Log.d(TAG, query + " on " + db.getPath());
        db.execSQL(query);

        mDbChanged = true;
    }


    public void UpdateTreatment(Treatment treat) {
        SQLiteDatabase db = this.getWritableDatabase();
        String tablename = treat.patient.Uid;

        String query = "UPDATE " + tablename + " SET date = '" + treat.date +
                "', complaint = '" + treat.complaint + "', prescription = '" + treat.prescription +
                "' WHERE " + TREAT_ID + " = '" + treat.tid + "';";

        Log.d(TAG, query);
        db.execSQL(query);
        mDbChanged = true;
        db.close();
    }


    public void DeleteTreatment(Treatment treat) {
        SQLiteDatabase db = this.getWritableDatabase();
        String tablename = treat.patient.Uid;

        String query = "DELETE FROM " + tablename + " WHERE " + TREAT_ID + " = '" + treat.tid + "';";

        Log.d(TAG, query);
        db.execSQL(query);
        mDbChanged = true;
        db.close();
    }


    public List<Treatment> GetTreatmentList(Patient pat, ListOrder order) {
        SQLiteDatabase db = this.getWritableDatabase();
        List<Treatment> list = GetTreatmentListFromDB(db, pat, order);

        db.close();
        return list;
    }


    public List<Treatment> GetTreatmentListFromDB(SQLiteDatabase db, Patient pat, ListOrder order) {
        List<Treatment> treatmentList = new ArrayList<>();
        String tid, date, complaint, prescription, doctor, query, desc;
        Cursor res = null;
        Treatment treat;
        String tablename;

        Log.d(TAG, "GetTreatmentListFromdDB(): dbname = " + db.getPath());
        tablename = pat.Uid;

        if (order == ListOrder.REVERSE)
            desc = " DESC";
        else
            desc = "";

        // check if treatment table exists in the current input database
        query = "select DISTINCT tbl_name from sqlite_master where tbl_name = '" + tablename + "'";
        res = db.rawQuery(query, null);
        if ((res == null) || (res.getCount() <= 0)) {
            Log.d(TAG, "Treatment table " + tablename + " doesn't exist for " + pat.Name + "!");
            treat = new Treatment(pat, "0", "Empty", "Empty", "");
            treatmentList.add(treat);

            res.close();
            return treatmentList;
        }
        res.close();

        // read all treatments for this patient from this input database
        query = "SELECT * FROM " + tablename + " ORDER BY " + TREAT_ID + desc;
        res = db.rawQuery(query, null);

        res.moveToFirst();
        if (res.getCount() <= 0) {
            Log.d(TAG, "No treatment found for this patient: " + pat.Name + "!");
            treat = new Treatment(pat, "0", "Empty", "Empty", "");
            treatmentList.add(treat);

            res.close();
            return treatmentList;
        }

        Log.d(TAG, "Number of treatments = " + res.getCount());
        while (!res.isAfterLast()) {
            date = res.getString(res.getColumnIndex("date"));
            complaint = res.getString(res.getColumnIndex("complaint"));
            prescription = res.getString(res.getColumnIndex("prescription"));
            doctor = res.getString(res.getColumnIndex("doctor"));
            tid = res.getString(res.getColumnIndex(TREAT_ID));


            //Log.d(TAG, "Date = "+ date + ", Complaint = " + complaint + ", Prescription = " + prescription);
            treat = new Treatment(pat, tid, complaint, prescription, doctor);
            treat.date = date;
            treatmentList.add(treat);

            res.moveToNext();
        }

        res.close();
        return treatmentList;
    }


    private boolean isTreatmentExist(Treatment inTrt, List<Treatment> trtList) {
        for (Treatment trt : trtList) {
            Log.d(TAG, trt.date + "==" + inTrt.date + " && " + trt.complaint + "==" + inTrt.complaint);
            if (trt.date.equals(inTrt.date) && trt.complaint.equals(inTrt.complaint)) {
                return true;
            }
        }
        return false;
    }


    public int mergeTreatmentsToDB(Patient pat, SQLiteDatabase sdb, SQLiteDatabase ddb) {
        List<Treatment> srcList; // Treatment list from source database
        List<Treatment> dstList; // Treatment list from destination database

        Log.d(TAG, "Entering mergeTreatments()");
        Log.d(TAG, sdb.getPath());
        srcList = GetTreatmentListFromDB(sdb, pat, ListOrder.ASCENDING);
        Log.d(TAG, ddb.getPath());
        dstList = GetTreatmentListFromDB(ddb, pat, ListOrder.ASCENDING);

        // Check for redundancy and add imported records to destination database
        int count = 0;
        for (Treatment trt : srcList) {
            if (trt.patient.Name.equals("Empty"))
                continue;
            if (!isTreatmentExist(trt, dstList)) {
                AddTreatmentToDB(ddb, trt);
                dstList.add(trt);
                count++;
            }
        }

        return count;
    }


    public boolean isDbChanged() {
        Log.d(TAG, "mDbChanged = " + mDbChanged);
        return mDbChanged;
    }


    public void DbSaved() {
        mDbChanged = false;
    }
}
