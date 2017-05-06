package com.nonprofit.aananth.prms;

import android.content.DialogInterface;
import android.content.res.Configuration;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.String;
import java.util.Arrays;
import java.util.List;


public class MainActivity extends AppCompatActivity {

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    // Aananth added this
    public enum Mode {NORMAL, ADD_PAT, UPDATE_PAT}

    // Aananth added these member variables
    private int currLayout;
    private List doctors = Arrays.asList("Jegadish", "Rama");
    private EditText user, password, patname, patphone, patmail;
    private PatientDB patientdb;
    private RecyclerView mRecyclerView;
    private LinearLayoutManager mLinearLayoutManager;
    private PatRecyclerAdapter mAdapter;
    private List<Patient> patientList;
    private Patient mCurrPatient;
    private Mode mMode;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login);

        // Example of a call to a native method
        TextView tv = (TextView) findViewById(R.id.message_txt);
        tv.setText(stringFromJNI());

        // Aananth added these lines
        currLayout = R.layout.login;
        patientdb = new PatientDB(this);
        mMode = Mode.NORMAL;
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();

    public void onLogin(View view) {
        // get the user inputs
        user = (EditText)findViewById(R.id.user);
        password = (EditText)findViewById(R.id.password);

        // check if the credentials are valid. TODO: Implement a secure and better auth logic
        if (doctors.contains(user.getText().toString())) {
            if (password.getText().toString().contains("saami123")) {
                Log.d("Main Activity", "Successful login");
                currLayout = R.layout.patients;
                setContentView(currLayout);
            }
            else {
                Log.d("Main Activity", "Failure! Password: " + password.getText().toString());
            }
        }
        else {
            Log.d("Main Activity", "Failure! Username: " + user.getText().toString());
        }

        // remove these lines
        Log.d("Main Activity", "Successful login");
        currLayout = R.layout.patients;
        setContentView(currLayout);
        patientList = patientdb.GetPatientList(null);

        renderPatRecycleView(patientList);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.d("Main Activity", "Layout set to current layout");
        setContentView(currLayout);
    }

    // Added by Aananth: button handlers
    public void SearchPatNamePhone(View view) {
        EditText srchtxt;
        String srchstr;

        srchtxt = (EditText) findViewById(R.id.search_txt);

        Log.d("Main Activity", "Search patient records");
        currLayout = R.layout.patients;
        setContentView(currLayout);
        srchstr = srchtxt.getText().toString();
        patientList = patientdb.GetPatientList(srchstr);

        renderPatRecycleView(patientList);

        Button srchbtn = (Button) findViewById(R.id.search_btn);
        if (srchstr.length() > 0) {
            srchbtn.setText("Refresh");
        } else {
            srchbtn.setText("Search");
        }
    }

    public void AddNewPatient(View view) {
        currLayout = R.layout.add_edit;
        setContentView(currLayout);

        Button delbtn = (Button) findViewById(R.id.pat_del);
        delbtn.setVisibility(View.INVISIBLE);
        mMode = Mode.ADD_PAT;
    }

    public void SavePatientRecord(View view) {
        String gender;
        RadioButton patgender;

        patname = (EditText)findViewById(R.id.fullName);
        patphone = (EditText)findViewById(R.id.phoneNo);
        patmail = (EditText)findViewById(R.id.emailID);
        patgender = (RadioButton)findViewById(R.id.radioMale);

        if (patgender.isChecked())
            gender = "Male";
        else
            gender = "Female";

        if (mMode == Mode.ADD_PAT) {
            Patient pat = new Patient(patname.getText().toString(), patphone.getText().toString(),
                    patmail.getText().toString(), gender, "", "");
            patientdb.AddPatient(pat);
        }
        else if (mMode == Mode.UPDATE_PAT) {
            Patient pat = new Patient(patname.getText().toString(), patphone.getText().toString(),
                    patmail.getText().toString(), gender, mCurrPatient.Pid, mCurrPatient.Uid);
            patientdb.UpdatePatient(pat);
        }
        Log.d("Main Activity", "Saved patient records");
        currLayout = R.layout.patients;
        setContentView(currLayout);
        patientList = patientdb.GetPatientList(null);

        renderPatRecycleView(patientList);
        mMode = Mode.NORMAL;
    }

    public void CancelPatientRecordEdit(View view) {
        currLayout = R.layout.patients;
        setContentView(currLayout);
        patientList = patientdb.GetPatientList(null);

        renderPatRecycleView(patientList);
        mMode = Mode.NORMAL;
    }

    public void DeleteCurrPatient(View view) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Are you sure to delete this patient?");
        builder.setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // User clicked ok button
                        patientdb.DeletePatient(mCurrPatient);
                        currLayout = R.layout.patients;
                        setContentView(currLayout);

                        patientList = patientdb.GetPatientList(null);
                        renderPatRecycleView(patientList);
                    }
                });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // User cancelled the dialog
                        currLayout = R.layout.patients;
                        setContentView(currLayout);

                        patientList = patientdb.GetPatientList(null);
                        renderPatRecycleView(patientList);
                    }
                });

        AlertDialog dialog = builder.create();
        dialog.show();
        mMode = Mode.NORMAL;
    }

    // interface class
    public static abstract class ClickListener{
        public abstract void onClick(View view, int position);
        public abstract void onLongClick(View view, int position);
    }

    public void renderPatRecycleView(final List<Patient> patlist) {
        mRecyclerView = (RecyclerView) findViewById(R.id.pat_rv);
        if (mRecyclerView == null) {
            Log.d("Main Activity", "mRecylerView is null!!");
            return;
        }
        mLinearLayoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(mLinearLayoutManager);
        mAdapter = new PatRecyclerAdapter(patientList);
        mRecyclerView.addItemDecoration(new DividerItemDecorator(this, LinearLayoutManager.VERTICAL));
        mRecyclerView.setAdapter(mAdapter);

        mAdapter.notifyDataSetChanged();

        mRecyclerView.addOnItemTouchListener(new RecyclerTouchListener(this,
                mRecyclerView, new ClickListener() {
            @Override
            public void onClick(View view, final int position) {
                // show patient history view
                Toast.makeText(MainActivity.this, "Pos :"+position+" - "+patlist.get(position).Name,
                        Toast.LENGTH_LONG).show();
            }

            @Override
            public void onLongClick(View view, int position) {
                // edit patient data
                mMode = Mode.UPDATE_PAT;
                currLayout = R.layout.add_edit;
                setContentView(currLayout);

                mCurrPatient = patlist.get(position);
                patname = (EditText) findViewById(R.id.fullName);
                patname.setText(mCurrPatient.Name);
                patphone = (EditText) findViewById(R.id.phoneNo);
                patphone.setText(mCurrPatient.Phone);
                patmail = (EditText) findViewById(R.id.emailID);
                patmail.setText(mCurrPatient.Email);
                if (mCurrPatient.Gender == "Male") {
                    RadioButton gender = (RadioButton) findViewById(R.id.radioMale);
                    gender.setChecked(true);
                }
                else {
                    RadioButton gender = (RadioButton) findViewById(R.id.radioFemale);
                    gender.setChecked(true);
                }

                TextView title = (TextView) findViewById(R.id.add_edit_txt);
                title.setText("Edit Patient's Details");

                Button patsav = (Button) findViewById(R.id.pat_save);
                patsav.setText("Update");
                Button patback = (Button) findViewById(R.id.pat_back);
                patback.setText("Cancel");

            }
        }));
    }
}
