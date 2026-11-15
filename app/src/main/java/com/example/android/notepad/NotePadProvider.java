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

package com.example.android.notepad;

import com.example.android.notepad.NotePad;

import android.content.ClipDescription;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.UriMatcher;
import android.content.ContentProvider.PipeDataWriter;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.LiveFolders;
import android.text.TextUtils;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;

/**
 * Provides access to a database of notes, notebooks, attachments and todos.
 */
public class NotePadProvider extends ContentProvider implements PipeDataWriter<Cursor> {
    // Used for debugging and logging
    private static final String TAG = "NotePadProvider";

    /**
     * The database that the provider uses as its underlying data store
     */
    private static final String DATABASE_NAME = "note_pad.db";

    /**
     * The database version. Version 3 adds notebooks, attachments and todos. Version 4 makes
     * todos standalone by detaching them from notes. Version 5 localises the default notebooks.
     */
    private static final int DATABASE_VERSION = 5;

    /**
     * A projection map used to select columns from the notes table
     */
    private static HashMap<String, String> sNotesProjectionMap;

    /**
     * A projection map used by the live folder
     */
    private static HashMap<String, String> sLiveFolderProjectionMap;

    private static HashMap<String, String> sNotebooksProjectionMap;
    private static HashMap<String, String> sAttachmentsProjectionMap;
    private static HashMap<String, String> sTodosProjectionMap;

    /**
     * Standard projection for the interesting columns of a normal note.
     */
    private static final String[] READ_NOTE_PROJECTION = new String[] {
            NotePad.Notes._ID,               // Projection position 0, the note's id
            NotePad.Notes.COLUMN_NAME_NOTE,  // Projection position 1, the note's content
            NotePad.Notes.COLUMN_NAME_TITLE, // Projection position 2, the note's title
    };
    private static final int READ_NOTE_NOTE_INDEX = 1;
    private static final int READ_NOTE_TITLE_INDEX = 2;

    /*
     * Constants used by the Uri matcher to choose an action based on the pattern
     * of the incoming URI
     */
    private static final int NOTES = 1;
    private static final int NOTE_ID = 2;
    private static final int LIVE_FOLDER_NOTES = 3;
    private static final int NOTEBOOKS = 4;
    private static final int NOTEBOOK_ID = 5;
    private static final int ATTACHMENTS = 6;
    private static final int ATTACHMENT_ID = 7;
    private static final int TODOS = 8;
    private static final int TODO_ID = 9;

    /**
     * A UriMatcher instance
     */
    private static final UriMatcher sUriMatcher;

    // Handle to a new DatabaseHelper.
    private DatabaseHelper mOpenHelper;


    /**
     * A block that instantiates and sets static objects
     */
    static {

        /*
         * Creates and initializes the URI matcher
         */
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

        sUriMatcher.addURI(NotePad.AUTHORITY, "notes", NOTES);
        sUriMatcher.addURI(NotePad.AUTHORITY, "notes/#", NOTE_ID);
        sUriMatcher.addURI(NotePad.AUTHORITY, "live_folders/notes", LIVE_FOLDER_NOTES);
        sUriMatcher.addURI(NotePad.AUTHORITY, "notebooks", NOTEBOOKS);
        sUriMatcher.addURI(NotePad.AUTHORITY, "notebooks/#", NOTEBOOK_ID);
        sUriMatcher.addURI(NotePad.AUTHORITY, "attachments", ATTACHMENTS);
        sUriMatcher.addURI(NotePad.AUTHORITY, "attachments/#", ATTACHMENT_ID);
        sUriMatcher.addURI(NotePad.AUTHORITY, "todos", TODOS);
        sUriMatcher.addURI(NotePad.AUTHORITY, "todos/#", TODO_ID);

        /*
         * Creates and initializes projection maps that return all columns
         */
        sNotesProjectionMap = buildProjectionMap(
                NotePad.Notes._ID,
                NotePad.Notes.COLUMN_NAME_TITLE,
                NotePad.Notes.COLUMN_NAME_NOTE,
                NotePad.Notes.COLUMN_NAME_CREATE_DATE,
                NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,
                NotePad.Notes.COLUMN_NAME_NOTEBOOK_ID);

        sNotebooksProjectionMap = buildProjectionMap(
                NotePad.Notebooks._ID,
                NotePad.Notebooks.COLUMN_NAME_NAME,
                NotePad.Notebooks.COLUMN_NAME_CATEGORY,
                NotePad.Notebooks.COLUMN_NAME_CREATE_DATE,
                NotePad.Notebooks.COLUMN_NAME_MODIFICATION_DATE);

        sAttachmentsProjectionMap = buildProjectionMap(
                NotePad.Attachments._ID,
                NotePad.Attachments.COLUMN_NAME_NOTE_ID,
                NotePad.Attachments.COLUMN_NAME_TYPE,
                NotePad.Attachments.COLUMN_NAME_PATH,
                NotePad.Attachments.COLUMN_NAME_CREATE_DATE);

        sTodosProjectionMap = buildProjectionMap(
                NotePad.Todos._ID,
                NotePad.Todos.COLUMN_NAME_NOTE_ID,
                NotePad.Todos.COLUMN_NAME_CONTENT,
                NotePad.Todos.COLUMN_NAME_DONE,
                NotePad.Todos.COLUMN_NAME_CREATE_DATE);

        /*
         * Creates and initializes a projection map for handling Live Folders
         */
        sLiveFolderProjectionMap = new HashMap<String, String>();
        sLiveFolderProjectionMap.put(LiveFolders._ID,
                NotePad.Notes._ID + " AS " + LiveFolders._ID);
        sLiveFolderProjectionMap.put(LiveFolders.NAME,
                NotePad.Notes.COLUMN_NAME_TITLE + " AS " + LiveFolders.NAME);
    }

    /**
     * Builds an identity projection map for the given columns.
     */
    private static HashMap<String, String> buildProjectionMap(String... columns) {
        HashMap<String, String> map = new HashMap<String, String>();
        for (String column : columns) {
            map.put(column, column);
        }
        return map;
    }

    /**
     * Returns the table name that corresponds to a UriMatcher match.
     */
    private static String tableFor(int match) {
        switch (match) {
            case NOTES:
            case NOTE_ID:
            case LIVE_FOLDER_NOTES:
                return NotePad.Notes.TABLE_NAME;
            case NOTEBOOKS:
            case NOTEBOOK_ID:
                return NotePad.Notebooks.TABLE_NAME;
            case ATTACHMENTS:
            case ATTACHMENT_ID:
                return NotePad.Attachments.TABLE_NAME;
            case TODOS:
            case TODO_ID:
                return NotePad.Todos.TABLE_NAME;
            default:
                throw new IllegalArgumentException("Unknown UriMatcher match " + match);
        }
    }

    private static String defaultSortOrderFor(int match) {
        switch (match) {
            case NOTES:
            case NOTE_ID:
            case LIVE_FOLDER_NOTES:
                return NotePad.Notes.DEFAULT_SORT_ORDER;
            case NOTEBOOKS:
            case NOTEBOOK_ID:
                return NotePad.Notebooks.DEFAULT_SORT_ORDER;
            case ATTACHMENTS:
            case ATTACHMENT_ID:
                return NotePad.Attachments.DEFAULT_SORT_ORDER;
            case TODOS:
            case TODO_ID:
                return NotePad.Todos.DEFAULT_SORT_ORDER;
            default:
                return null;
        }
    }

    private static boolean isItemUri(int match) {
        return match == NOTE_ID || match == NOTEBOOK_ID
                || match == ATTACHMENT_ID || match == TODO_ID;
    }

    private static Uri itemUriBaseFor(int match) {
        switch (match) {
            case NOTES:
                return NotePad.Notes.CONTENT_ID_URI_BASE;
            case NOTEBOOKS:
                return NotePad.Notebooks.CONTENT_ID_URI_BASE;
            case ATTACHMENTS:
                return NotePad.Attachments.CONTENT_ID_URI_BASE;
            case TODOS:
                return NotePad.Todos.CONTENT_ID_URI_BASE;
            default:
                throw new IllegalArgumentException("No item URI for match " + match);
        }
    }

    /**
    *
    * This class helps open, create, and upgrade the database file. Set to package visibility
    * for testing purposes.
    */
   static class DatabaseHelper extends SQLiteOpenHelper {

       DatabaseHelper(Context context) {
           // calls the super constructor, requesting the default cursor factory.
           super(context, DATABASE_NAME, null, DATABASE_VERSION);
       }

       /**
        * Creates the underlying database with the tables used by the NotePad application.
        */
       @Override
       public void onCreate(SQLiteDatabase db) {
           db.execSQL("CREATE TABLE " + NotePad.Notes.TABLE_NAME + " ("
                   + NotePad.Notes._ID + " INTEGER PRIMARY KEY,"
                   + NotePad.Notes.COLUMN_NAME_TITLE + " TEXT,"
                   + NotePad.Notes.COLUMN_NAME_NOTE + " TEXT,"
                   + NotePad.Notes.COLUMN_NAME_CREATE_DATE + " INTEGER,"
                   + NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE + " INTEGER,"
                   + NotePad.Notes.COLUMN_NAME_NOTEBOOK_ID + " INTEGER"
                   + ");");

           createAuxiliaryTables(db);

           // Every installation starts with one notebook so notes always have a home.
           insertNotebook(db, "我的笔记", "其他", System.currentTimeMillis());
       }

       private static void createAuxiliaryTables(SQLiteDatabase db) {
           db.execSQL("CREATE TABLE IF NOT EXISTS " + NotePad.Notebooks.TABLE_NAME + " ("
                   + NotePad.Notebooks._ID + " INTEGER PRIMARY KEY,"
                   + NotePad.Notebooks.COLUMN_NAME_NAME + " TEXT,"
                   + NotePad.Notebooks.COLUMN_NAME_CATEGORY + " TEXT,"
                   + NotePad.Notebooks.COLUMN_NAME_CREATE_DATE + " INTEGER,"
                   + NotePad.Notebooks.COLUMN_NAME_MODIFICATION_DATE + " INTEGER"
                   + ");");

           db.execSQL("CREATE TABLE IF NOT EXISTS " + NotePad.Attachments.TABLE_NAME + " ("
                   + NotePad.Attachments._ID + " INTEGER PRIMARY KEY,"
                   + NotePad.Attachments.COLUMN_NAME_NOTE_ID + " INTEGER,"
                   + NotePad.Attachments.COLUMN_NAME_TYPE + " TEXT,"
                   + NotePad.Attachments.COLUMN_NAME_PATH + " TEXT,"
                   + NotePad.Attachments.COLUMN_NAME_CREATE_DATE + " INTEGER"
                   + ");");

           db.execSQL("CREATE TABLE IF NOT EXISTS " + NotePad.Todos.TABLE_NAME + " ("
                   + NotePad.Todos._ID + " INTEGER PRIMARY KEY,"
                   + NotePad.Todos.COLUMN_NAME_NOTE_ID + " INTEGER,"
                   + NotePad.Todos.COLUMN_NAME_CONTENT + " TEXT,"
                   + NotePad.Todos.COLUMN_NAME_DONE + " INTEGER DEFAULT 0,"
                   + NotePad.Todos.COLUMN_NAME_CREATE_DATE + " INTEGER"
                   + ");");
       }

       private static long insertNotebook(SQLiteDatabase db, String name, String category,
               long now) {
           ContentValues values = new ContentValues();
           values.put(NotePad.Notebooks.COLUMN_NAME_NAME, name);
           values.put(NotePad.Notebooks.COLUMN_NAME_CATEGORY, category);
           values.put(NotePad.Notebooks.COLUMN_NAME_CREATE_DATE, now);
           values.put(NotePad.Notebooks.COLUMN_NAME_MODIFICATION_DATE, now);
           return db.insert(NotePad.Notebooks.TABLE_NAME, null, values);
       }

       /**
        * Upgrades older databases in place, keeping the user's existing notes.
        */
       @Override
       public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
           Log.w(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion);

           if (oldVersion < 3) {
               createAuxiliaryTables(db);

               // Adds the notebook_id column to existing notes tables.
               try {
                   db.execSQL("ALTER TABLE " + NotePad.Notes.TABLE_NAME + " ADD COLUMN "
                           + NotePad.Notes.COLUMN_NAME_NOTEBOOK_ID + " INTEGER");
               } catch (SQLException e) {
                   Log.w(TAG, "notebook_id column already exists", e);
               }

               long defaultNotebookId = -1;
               Cursor cursor = db.rawQuery("SELECT " + NotePad.Notebooks._ID + " FROM "
                       + NotePad.Notebooks.TABLE_NAME + " ORDER BY " + NotePad.Notebooks._ID
                       + " LIMIT 1", null);
               try {
                   if (cursor.moveToFirst()) {
                       defaultNotebookId = cursor.getLong(0);
                   }
               } finally {
                   cursor.close();
               }

               if (defaultNotebookId < 0) {
                   defaultNotebookId = insertNotebook(db, "我的笔记", "其他",
                           System.currentTimeMillis());
               }

               // Moves notes created before notebooks existed into the default notebook.
               db.execSQL("UPDATE " + NotePad.Notes.TABLE_NAME + " SET "
                       + NotePad.Notes.COLUMN_NAME_NOTEBOOK_ID + " = " + defaultNotebookId
                       + " WHERE " + NotePad.Notes.COLUMN_NAME_NOTEBOOK_ID + " IS NULL");
           }

           if (oldVersion < 4) {
               // Todos now live on their own page instead of inside a note, so any todo that
               // was created from the old editor is detached from its note.
               db.execSQL("UPDATE " + NotePad.Todos.TABLE_NAME + " SET "
                       + NotePad.Todos.COLUMN_NAME_NOTE_ID + " = NULL");
           }

           if (oldVersion < 5) {
               // The interface is Chinese now; translate the notebooks created by older builds.
               db.execSQL("UPDATE " + NotePad.Notebooks.TABLE_NAME + " SET "
                       + NotePad.Notebooks.COLUMN_NAME_CATEGORY + " = '学习' WHERE "
                       + NotePad.Notebooks.COLUMN_NAME_CATEGORY + " = 'Study'");
               db.execSQL("UPDATE " + NotePad.Notebooks.TABLE_NAME + " SET "
                       + NotePad.Notebooks.COLUMN_NAME_CATEGORY + " = '工作' WHERE "
                       + NotePad.Notebooks.COLUMN_NAME_CATEGORY + " = 'Work'");
               db.execSQL("UPDATE " + NotePad.Notebooks.TABLE_NAME + " SET "
                       + NotePad.Notebooks.COLUMN_NAME_CATEGORY + " = '生活' WHERE "
                       + NotePad.Notebooks.COLUMN_NAME_CATEGORY + " = 'Life'");
               db.execSQL("UPDATE " + NotePad.Notebooks.TABLE_NAME + " SET "
                       + NotePad.Notebooks.COLUMN_NAME_CATEGORY + " = '其他' WHERE "
                       + NotePad.Notebooks.COLUMN_NAME_CATEGORY + " = 'Other'");
               db.execSQL("UPDATE " + NotePad.Notebooks.TABLE_NAME + " SET "
                       + NotePad.Notebooks.COLUMN_NAME_NAME + " = '我的笔记' WHERE "
                       + NotePad.Notebooks.COLUMN_NAME_NAME + " = 'My notes'");
           }
       }
   }

   /**
    * Initializes the provider by creating a new DatabaseHelper.
    */
   @Override
   public boolean onCreate() {
       mOpenHelper = new DatabaseHelper(getContext());
       return true;
   }

   /**
    * Queries the database and returns a cursor containing the results.
    */
   @Override
   public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
           String sortOrder) {

       int match = sUriMatcher.match(uri);

       SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
       qb.setTables(tableFor(match));

       switch (match) {
           case NOTES:
           case NOTE_ID:
               qb.setProjectionMap(sNotesProjectionMap);
               break;
           case LIVE_FOLDER_NOTES:
               qb.setProjectionMap(sLiveFolderProjectionMap);
               break;
           case NOTEBOOKS:
           case NOTEBOOK_ID:
               qb.setProjectionMap(sNotebooksProjectionMap);
               break;
           case ATTACHMENTS:
           case ATTACHMENT_ID:
               qb.setProjectionMap(sAttachmentsProjectionMap);
               break;
           case TODOS:
           case TODO_ID:
               qb.setProjectionMap(sTodosProjectionMap);
               break;
           default:
               throw new IllegalArgumentException("Unknown URI " + uri);
       }

       if (isItemUri(match)) {
           qb.appendWhere(NotePad.Notes._ID + "=" + uri.getLastPathSegment());
       }

       String orderBy;
       if (TextUtils.isEmpty(sortOrder)) {
           orderBy = defaultSortOrderFor(match);
       } else {
           orderBy = sortOrder;
       }

       SQLiteDatabase db = mOpenHelper.getReadableDatabase();

       Cursor c = qb.query(
           db,
           projection,
           selection,
           selectionArgs,
           null,
           null,
           orderBy
       );

       c.setNotificationUri(getContext().getContentResolver(), uri);
       return c;
   }

   /**
    * Returns the MIME data type of the URI given as a parameter.
    */
   @Override
   public String getType(Uri uri) {
       switch (sUriMatcher.match(uri)) {
           case NOTES:
           case LIVE_FOLDER_NOTES:
               return NotePad.Notes.CONTENT_TYPE;
           case NOTE_ID:
               return NotePad.Notes.CONTENT_ITEM_TYPE;
           case NOTEBOOKS:
               return NotePad.Notebooks.CONTENT_TYPE;
           case NOTEBOOK_ID:
               return NotePad.Notebooks.CONTENT_ITEM_TYPE;
           case ATTACHMENTS:
               return NotePad.Attachments.CONTENT_TYPE;
           case ATTACHMENT_ID:
               return NotePad.Attachments.CONTENT_ITEM_TYPE;
           case TODOS:
               return NotePad.Todos.CONTENT_TYPE;
           case TODO_ID:
               return NotePad.Todos.CONTENT_ITEM_TYPE;
           default:
               throw new IllegalArgumentException("Unknown URI " + uri);
       }
    }

//BEGIN_INCLUDE(stream)
    /**
     * This describes the MIME types that are supported for opening a note
     * URI as a stream.
     */
    static ClipDescription NOTE_STREAM_TYPES = new ClipDescription(null,
            new String[] { ClipDescription.MIMETYPE_TEXT_PLAIN });

    /**
     * Returns the types of available data streams.  URIs to specific notes are supported.
     * The application can convert such a note to a plain text stream.
     */
    @Override
    public String[] getStreamTypes(Uri uri, String mimeTypeFilter) {
        switch (sUriMatcher.match(uri)) {
            case NOTES:
            case LIVE_FOLDER_NOTES:
                return null;
            case NOTE_ID:
                return NOTE_STREAM_TYPES.filterMimeTypes(mimeTypeFilter);
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
            }
    }


    /**
     * Returns a stream of data for each supported stream type. This method does a query on the
     * incoming URI, then uses
     * {@link android.content.ContentProvider#openPipeHelper(Uri, String, Bundle, Object,
     * PipeDataWriter)} to start another thread in which to convert the data into a stream.
     */
    @Override
    public AssetFileDescriptor openTypedAssetFile(Uri uri, String mimeTypeFilter, Bundle opts)
            throws FileNotFoundException {

        String[] mimeTypes = getStreamTypes(uri, mimeTypeFilter);

        if (mimeTypes != null) {
            Cursor c = query(
                    uri,
                    READ_NOTE_PROJECTION,
                    null,
                    null,
                    null
            );

            if (c == null || !c.moveToFirst()) {
                if (c != null) {
                    c.close();
                }
                throw new FileNotFoundException("Unable to query " + uri);
            }

            return new AssetFileDescriptor(
                    openPipeHelper(uri, mimeTypes[0], opts, c, this), 0,
                    AssetFileDescriptor.UNKNOWN_LENGTH);
        }

        return super.openTypedAssetFile(uri, mimeTypeFilter, opts);
    }

    /**
     * Implementation of {@link android.content.ContentProvider.PipeDataWriter}
     * to perform the actual work of converting the data in one of cursors to a
     * stream of data for the client to read.
     */
    @Override
    public void writeDataToPipe(ParcelFileDescriptor output, Uri uri, String mimeType,
            Bundle opts, Cursor c) {
        FileOutputStream fout = new FileOutputStream(output.getFileDescriptor());
        PrintWriter pw = null;
        try {
            pw = new PrintWriter(new OutputStreamWriter(fout, "UTF-8"));
            pw.println(c.getString(READ_NOTE_TITLE_INDEX));
            pw.println("");
            pw.println(c.getString(READ_NOTE_NOTE_INDEX));
        } catch (UnsupportedEncodingException e) {
            Log.w(TAG, "Ooops", e);
        } finally {
            c.close();
            if (pw != null) {
                pw.flush();
            }
            try {
                fout.close();
            } catch (IOException e) {
            }
        }
    }
//END_INCLUDE(stream)

    /**
     * Inserts a new row into the database, setting up default values for any columns that are not
     * included in the incoming map.
     */
    @Override
    public Uri insert(Uri uri, ContentValues initialValues) {

        int match = sUriMatcher.match(uri);
        if (match != NOTES && match != NOTEBOOKS && match != ATTACHMENTS && match != TODOS) {
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        ContentValues values;
        if (initialValues != null) {
            values = new ContentValues(initialValues);
        } else {
            values = new ContentValues();
        }

        Long now = Long.valueOf(System.currentTimeMillis());

        switch (match) {
            case NOTES:
                if (values.containsKey(NotePad.Notes.COLUMN_NAME_CREATE_DATE) == false) {
                    values.put(NotePad.Notes.COLUMN_NAME_CREATE_DATE, now);
                }
                if (values.containsKey(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE) == false) {
                    values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, now);
                }
                if (values.containsKey(NotePad.Notes.COLUMN_NAME_TITLE) == false) {
                    Resources r = Resources.getSystem();
                    values.put(NotePad.Notes.COLUMN_NAME_TITLE,
                            r.getString(android.R.string.untitled));
                }
                if (values.containsKey(NotePad.Notes.COLUMN_NAME_NOTE) == false) {
                    values.put(NotePad.Notes.COLUMN_NAME_NOTE, "");
                }
                break;

            case NOTEBOOKS:
                if (values.containsKey(NotePad.Notebooks.COLUMN_NAME_CREATE_DATE) == false) {
                    values.put(NotePad.Notebooks.COLUMN_NAME_CREATE_DATE, now);
                }
                if (values.containsKey(NotePad.Notebooks.COLUMN_NAME_MODIFICATION_DATE) == false) {
                    values.put(NotePad.Notebooks.COLUMN_NAME_MODIFICATION_DATE, now);
                }
                if (values.containsKey(NotePad.Notebooks.COLUMN_NAME_NAME) == false) {
                    values.put(NotePad.Notebooks.COLUMN_NAME_NAME, "笔记本");
                }
                if (values.containsKey(NotePad.Notebooks.COLUMN_NAME_CATEGORY) == false) {
                    values.put(NotePad.Notebooks.COLUMN_NAME_CATEGORY, "其他");
                }
                break;

            case ATTACHMENTS:
                if (values.containsKey(NotePad.Attachments.COLUMN_NAME_CREATE_DATE) == false) {
                    values.put(NotePad.Attachments.COLUMN_NAME_CREATE_DATE, now);
                }
                break;

            case TODOS:
                if (values.containsKey(NotePad.Todos.COLUMN_NAME_CREATE_DATE) == false) {
                    values.put(NotePad.Todos.COLUMN_NAME_CREATE_DATE, now);
                }
                if (values.containsKey(NotePad.Todos.COLUMN_NAME_DONE) == false) {
                    values.put(NotePad.Todos.COLUMN_NAME_DONE, 0);
                }
                break;
        }

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();

        long rowId = db.insert(
            tableFor(match),
            null,
            values
        );

        if (rowId > 0) {
            Uri itemUri = ContentUris.withAppendedId(itemUriBaseFor(match), rowId);

            getContext().getContentResolver().notifyChange(uri, null);
            getContext().getContentResolver().notifyChange(itemUri, null);
            notifyWidget();
            return itemUri;
        }

        throw new SQLException("Failed to insert row into " + uri);
    }

    /**
     * Deletes records from the database. Deleting a note also deletes its attachments and todos;
     * deleting a notebook deletes every note it contains.
     */
    @Override
    public int delete(Uri uri, String where, String[] whereArgs) {

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int match = sUriMatcher.match(uri);
        String table = tableFor(match);

        String id = isItemUri(match) ? uri.getLastPathSegment() : null;

        if (match == NOTE_ID) {
            // Removes the child rows of the deleted note.
            db.delete(NotePad.Attachments.TABLE_NAME,
                    NotePad.Attachments.COLUMN_NAME_NOTE_ID + "=" + id, null);
            db.delete(NotePad.Todos.TABLE_NAME,
                    NotePad.Todos.COLUMN_NAME_NOTE_ID + "=" + id, null);
        } else if (match == NOTEBOOK_ID) {
            // Removes the child rows of every note in the deleted notebook.
            String noteIds = "SELECT " + NotePad.Notes._ID + " FROM " + NotePad.Notes.TABLE_NAME
                    + " WHERE " + NotePad.Notes.COLUMN_NAME_NOTEBOOK_ID + "=" + id;
            db.delete(NotePad.Attachments.TABLE_NAME,
                    NotePad.Attachments.COLUMN_NAME_NOTE_ID + " IN (" + noteIds + ")", null);
            db.delete(NotePad.Todos.TABLE_NAME,
                    NotePad.Todos.COLUMN_NAME_NOTE_ID + " IN (" + noteIds + ")", null);
            db.delete(NotePad.Notes.TABLE_NAME,
                    NotePad.Notes.COLUMN_NAME_NOTEBOOK_ID + "=" + id, null);
        }

        String finalWhere = null;
        if (id != null) {
            finalWhere = NotePad.Notes._ID + " = " + id;
        }
        if (where != null) {
            finalWhere = finalWhere == null ? where : finalWhere + " AND " + where;
        }

        int count = db.delete(table, finalWhere, whereArgs);

        getContext().getContentResolver().notifyChange(uri, null);

        // A notebook deletion also changes the notes directory.
        if (match == NOTEBOOK_ID) {
            getContext().getContentResolver().notifyChange(NotePad.Notes.CONTENT_URI, null);
        }

        notifyWidget();
        return count;
    }

    /**
     * Updates records in the database.
     */
    @Override
    public int update(Uri uri, ContentValues values, String where, String[] whereArgs) {

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int match = sUriMatcher.match(uri);
        String table = tableFor(match);

        String id = isItemUri(match) ? uri.getLastPathSegment() : null;

        String finalWhere = null;
        if (id != null) {
            finalWhere = NotePad.Notes._ID + " = " + id;
        }
        if (where != null) {
            finalWhere = finalWhere == null ? where : finalWhere + " AND " + where;
        }

        int count = db.update(
            table,
            values,
            finalWhere,
            whereArgs
        );

        getContext().getContentResolver().notifyChange(uri, null);

        notifyWidget();
        return count;
    }

    /** Refreshes every home screen sticky note after the notes changed. */
    private void notifyWidget() {
        Intent intent = new Intent(NoteWidgetProvider.ACTION_NOTES_CHANGED);
        intent.setPackage(getContext().getPackageName());
        getContext().sendBroadcast(intent);
    }

    /**
     * A test package can call this to get a handle to the database underlying NotePadProvider.
     */
    DatabaseHelper getOpenHelperForTest() {
        return mOpenHelper;
    }
}
