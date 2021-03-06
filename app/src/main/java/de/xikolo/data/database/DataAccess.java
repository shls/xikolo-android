package de.xikolo.data.database;

import android.database.sqlite.SQLiteDatabase;

abstract class DataAccess {

    protected DatabaseHelper databaseHelper;

    public DataAccess(DatabaseHelper databaseHelper) {
        this.databaseHelper = databaseHelper;
    }

    protected SQLiteDatabase openDatabase() {
       return databaseHelper.openDatabase();
    }

    protected void closeDatabase() {
        databaseHelper.close();
    }

}
