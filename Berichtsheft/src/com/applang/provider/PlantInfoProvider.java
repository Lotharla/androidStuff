/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.applang.provider;

import com.applang.provider.PlantInfo.Plants;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import java.util.HashMap;

/**
 * Provides access to a database of plants.
 */
public class PlantInfoProvider extends ContentProvider {

    private static final String TAG = "PlantInfoProvider";

    private static final String DATABASE_NAME = "plant_info.db";
    private static final int DATABASE_VERSION = 1;
    private static final String PLANTS_TABLE_NAME = "plants";

    private static HashMap<String, String> sPlantsProjectionMap;

    private static final int PLANTS = 1;
    private static final int PLANT_ID = 2;

    private static final UriMatcher sUriMatcher;

    /**
     * This class helps open, create, and upgrade the database file.
     */
    private static class DatabaseHelper extends SQLiteOpenHelper {

        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + PLANTS_TABLE_NAME + " ("
                    + Plants._ID + " INTEGER PRIMARY KEY,"
                    + Plants.NAME + " TEXT,"
                    + Plants.FAMILY + " TEXT,"
                    + Plants.BOTNAME + " TEXT,"
                    + Plants.GROUP + " TEXT"
                    + ");");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
                    + newVersion + ", which will destroy all old data");
            db.execSQL("DROP TABLE IF EXISTS plants");
            onCreate(db);
        }
    }

    private DatabaseHelper mOpenHelper;

    @Override
    public boolean onCreate() {
        mOpenHelper = new DatabaseHelper(getContext());
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

        switch (sUriMatcher.match(uri)) {
        case PLANTS:
            qb.setTables(PLANTS_TABLE_NAME);
            qb.setProjectionMap(sPlantsProjectionMap);
            break;

        case PLANT_ID:
            qb.setTables(PLANTS_TABLE_NAME);
            qb.setProjectionMap(sPlantsProjectionMap);
            qb.appendWhere(Plants._ID + "=" + uri.getPathSegments().get(1));
            break;

        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        // If no sort order is specified use the default
        String orderBy;
        if (TextUtils.isEmpty(sortOrder)) {
            orderBy = Plants.DEFAULT_SORT_ORDER;
        } else {
            orderBy = sortOrder;
        }

        // Get the database and run the query
        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        Cursor c = qb.query(db, projection, selection, selectionArgs, null, null, orderBy);

        // Tell the cursor what uri to watch, so it knows when its source data changes
        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }

    @Override
    public String getType(Uri uri) {
        switch (sUriMatcher.match(uri)) {
        case PLANTS:
            return Plants.CONTENT_TYPE;

        case PLANT_ID:
            return Plants.CONTENT_ITEM_TYPE;

        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues initialValues) {
        // Validate the requested uri
        if (sUriMatcher.match(uri) != PLANTS) {
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        ContentValues values;
        if (initialValues != null) {
            values = new ContentValues(initialValues);
        } else {
            values = new ContentValues();
        }

        if (values.containsKey(Plants.NAME) == false) {
            values.put(Plants.NAME, "");
        }

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        long rowId = db.insert(PLANTS_TABLE_NAME, Plants.NAME, values);
        if (rowId > 0) {
            Uri plantUri = ContentUris.withAppendedId(Plants.CONTENT_URI, rowId);
            getContext().getContentResolver().notifyChange(plantUri, null);
            return plantUri;
        }

        throw new SQLException("Failed to insert row into " + uri);
    }

    @Override
    public int delete(Uri uri, String where, String[] whereArgs) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count;
        switch (sUriMatcher.match(uri)) {
        case PLANTS:
            count = db.delete(PLANTS_TABLE_NAME, where, whereArgs);
            break;

        case PLANT_ID:
            String plantId = uri.getPathSegments().get(1);
            count = db.delete(PLANTS_TABLE_NAME, Plants._ID + "=" + plantId
                    + (!TextUtils.isEmpty(where) ? " AND (" + where + ')' : ""), whereArgs);
            break;

        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    @Override
    public int update(Uri uri, ContentValues values, String where, String[] whereArgs) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count;
        switch (sUriMatcher.match(uri)) {
        case PLANTS:
            count = db.update(PLANTS_TABLE_NAME, values, where, whereArgs);
            break;

        case PLANT_ID:
            String plantId = uri.getPathSegments().get(1);
            count = db.update(PLANTS_TABLE_NAME, values, Plants._ID + "=" + plantId
                    + (!TextUtils.isEmpty(where) ? " AND (" + where + ')' : ""), whereArgs);
            break;

        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    static {
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(PlantInfo.AUTHORITY, "plants", PLANTS);
        sUriMatcher.addURI(PlantInfo.AUTHORITY, "plants/#", PLANT_ID);

        sPlantsProjectionMap = new HashMap<String, String>();
        sPlantsProjectionMap.put(Plants._ID, Plants._ID);
        sPlantsProjectionMap.put(Plants.NAME, Plants.NAME);
        sPlantsProjectionMap.put(Plants.FAMILY, Plants.FAMILY);
        sPlantsProjectionMap.put(Plants.BOTNAME, Plants.BOTNAME);
        sPlantsProjectionMap.put(Plants.BOTFAMILY, Plants.BOTFAMILY);
    }
}