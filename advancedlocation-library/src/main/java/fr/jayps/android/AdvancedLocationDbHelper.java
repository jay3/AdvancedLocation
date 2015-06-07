package fr.jayps.android;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import android.util.Log;

public class AdvancedLocationDbHelper extends SQLiteOpenHelper {

    private static final String TAG = "PB-AdvLocDbHelper";

    public static final int DATABASE_VERSION = 3;
    public static final String DATABASE_NAME = "AdvancedLocation.db";

    private static AdvancedLocationDbHelper sInstance;

    /* Inner class that defines the table contents */
    public static abstract class Location implements BaseColumns {
        public static final String TABLE_NAME = "location";
    }
    private static final String TEXT_TYPE = " TEXT";
    private static final String SQL_CREATE_ENTRIES =
            "CREATE TABLE " + Location.TABLE_NAME + " ("
                    + Location._ID + " INTEGER PRIMARY KEY"
                    + ", loca_time" + TEXT_TYPE
                    + ", loca_lat" + TEXT_TYPE
                    + ", loca_lon" + TEXT_TYPE
                    + ", loca_altitude" + TEXT_TYPE
                    + ", loca_gps_altitude" + TEXT_TYPE
                    + ", loca_pressure_altitude" + TEXT_TYPE
                    + ", loca_ascent" + TEXT_TYPE
                    + ", loca_accuracy" + TEXT_TYPE
                    + ", loca_hr" + TEXT_TYPE
                    + ", loca_cad" + TEXT_TYPE
                    + ", loca_comment" + TEXT_TYPE
            + " )";

    private static final String SQL_DELETE_ENTRIES =
            "DROP TABLE IF EXISTS " + Location.TABLE_NAME;


    public static synchronized AdvancedLocationDbHelper getInstance(Context context) {

        // Use the application context, which will ensure that you
        // don't accidentally leak an Activity's context.
        // See this article for more information: http://bit.ly/6LRzfx
        if (sInstance == null) {
            sInstance = new AdvancedLocationDbHelper(context.getApplicationContext());
        }
        return sInstance;
    }

    /**
     * Constructor should be private to prevent direct instantiation.
     * make call to static method "getInstance()" instead.
     */
    private AdvancedLocationDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }
    public void onCreate(SQLiteDatabase db) {
        //Log.d(TAG, SQL_CREATE_ENTRIES);
        db.execSQL(SQL_CREATE_ENTRIES);
    }
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (newVersion > oldVersion) {
            if (oldVersion < 2) {
                SQLExec(db, "ALTER TABLE location ADD COLUMN loca_ascent TEXT");
                SQLExec(db, "ALTER TABLE location ADD COLUMN loca_gps_altitude TEXT");
                SQLExec(db, "ALTER TABLE location ADD COLUMN loca_pressure_altitude TEXT");
            }
            if (oldVersion < 3) {
                SQLExec(db, "ALTER TABLE location ADD COLUMN loca_hr TEXT");
                SQLExec(db, "ALTER TABLE location ADD COLUMN loca_cad TEXT");
            }
        }
    }
    private void SQLExec(SQLiteDatabase db, String sql) {
        Log.d(TAG, sql);
        db.execSQL(sql);
    }
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }
}

