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

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.UriMatcher;
import android.content.res.Resources;
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
 * 提供对笔记、笔记本和待办数据库的访问。
 */
public class NotePadProvider extends ContentProvider {
    // 用于调试和日志输出
    private static final String TAG = "NotePadProvider";

    /**
     * 本提供者用作底层数据存储的数据库
     */
    private static final String DATABASE_NAME = "note_pad.db";

    /**
     * 数据库版本。版本 3 新增了笔记本和待办。版本 4 将待办与笔记解耦，
     * 使其独立存在。版本 5 将默认笔记本的名称和分类本地化。
     */
    private static final int DATABASE_VERSION = 6;

    /**
     * 用于从笔记表中选择列的投影映射
     */
    private static HashMap<String, String> sNotesProjectionMap;

    private static HashMap<String, String> sNotebooksProjectionMap;
    private static HashMap<String, String> sTodosProjectionMap;

    /*
     * Uri 匹配器使用的常量，用于根据传入 URI 的模式
     * 选择相应的操作
     */
    private static final int NOTES = 1;
    private static final int NOTE_ID = 2;
    private static final int NOTEBOOKS = 3;
    private static final int NOTEBOOK_ID = 4;
    private static final int TODOS = 5;
    private static final int TODO_ID = 6;

    /**
     * 一个 UriMatcher 实例
     */
    private static final UriMatcher sUriMatcher;

    // 指向新建 DatabaseHelper 的句柄。
    private DatabaseHelper mOpenHelper;


    /**
     * 用于实例化并设置静态对象的代码块
     */
    static {

        /*
         * 创建并初始化 URI 匹配器
         */
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

        sUriMatcher.addURI(NotePad.AUTHORITY, "notes", NOTES);
        sUriMatcher.addURI(NotePad.AUTHORITY, "notes/#", NOTE_ID);
        sUriMatcher.addURI(NotePad.AUTHORITY, "notebooks", NOTEBOOKS);
        sUriMatcher.addURI(NotePad.AUTHORITY, "notebooks/#", NOTEBOOK_ID);
        sUriMatcher.addURI(NotePad.AUTHORITY, "todos", TODOS);
        sUriMatcher.addURI(NotePad.AUTHORITY, "todos/#", TODO_ID);

        /*
         * 创建并初始化返回所有列的投影映射
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

        sTodosProjectionMap = buildProjectionMap(
                NotePad.Todos._ID,
                NotePad.Todos.COLUMN_NAME_NOTE_ID,
                NotePad.Todos.COLUMN_NAME_CONTENT,
                NotePad.Todos.COLUMN_NAME_DONE,
                NotePad.Todos.COLUMN_NAME_CREATE_DATE,
                NotePad.Todos.COLUMN_NAME_DEADLINE);
    }

    /**
     * 为给定列构建恒等投影映射。
     */
    private static HashMap<String, String> buildProjectionMap(String... columns) {
        HashMap<String, String> map = new HashMap<String, String>();
        for (String column : columns) {
            map.put(column, column);
        }
        return map;
    }

    /**
     * 返回与 UriMatcher 匹配结果对应的表名。
     */
    private static String tableFor(int match) {
        switch (match) {
            case NOTES:
            case NOTE_ID:
                return NotePad.Notes.TABLE_NAME;
            case NOTEBOOKS:
            case NOTEBOOK_ID:
                return NotePad.Notebooks.TABLE_NAME;
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
                return NotePad.Notes.DEFAULT_SORT_ORDER;
            case NOTEBOOKS:
            case NOTEBOOK_ID:
                return NotePad.Notebooks.DEFAULT_SORT_ORDER;
            case TODOS:
            case TODO_ID:
                return NotePad.Todos.DEFAULT_SORT_ORDER;
            default:
                return null;
        }
    }

    private static boolean isItemUri(int match) {
        return match == NOTE_ID || match == NOTEBOOK_ID || match == TODO_ID;
    }

    private static Uri itemUriBaseFor(int match) {
        switch (match) {
            case NOTES:
                return NotePad.Notes.CONTENT_ID_URI_BASE;
            case NOTEBOOKS:
                return NotePad.Notebooks.CONTENT_ID_URI_BASE;
            case TODOS:
                return NotePad.Todos.CONTENT_ID_URI_BASE;
            default:
                throw new IllegalArgumentException("No item URI for match " + match);
        }
    }

    /**
    *
    * 本类用于打开、创建和升级数据库文件。为便于测试，
    * 其可见性设为包级。
    */
   static class DatabaseHelper extends SQLiteOpenHelper {

       DatabaseHelper(Context context) {
           // 调用父类构造函数，使用默认的游标工厂。
           super(context, DATABASE_NAME, null, DATABASE_VERSION);
       }

       /**
        * 创建 NotePad 应用所用表结构的底层数据库。
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

           // 每次安装都会创建一个笔记本，确保笔记始终有归属。
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

           db.execSQL("CREATE TABLE IF NOT EXISTS " + NotePad.Todos.TABLE_NAME + " ("
                   + NotePad.Todos._ID + " INTEGER PRIMARY KEY,"
                   + NotePad.Todos.COLUMN_NAME_NOTE_ID + " INTEGER,"
                   + NotePad.Todos.COLUMN_NAME_CONTENT + " TEXT,"
                   + NotePad.Todos.COLUMN_NAME_DONE + " INTEGER DEFAULT 0,"
                   + NotePad.Todos.COLUMN_NAME_CREATE_DATE + " INTEGER,"
                   + NotePad.Todos.COLUMN_NAME_DEADLINE + " INTEGER"
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
        * 就地升级旧版数据库，并保留用户已有的笔记。
        */
       @Override
       public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
           Log.w(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion);

           if (oldVersion < 3) {
               createAuxiliaryTables(db);

               // 为已有的笔记表添加 notebook_id 列。
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

               // 将笔记本功能出现之前创建的笔记归入默认笔记本。
               db.execSQL("UPDATE " + NotePad.Notes.TABLE_NAME + " SET "
                       + NotePad.Notes.COLUMN_NAME_NOTEBOOK_ID + " = " + defaultNotebookId
                       + " WHERE " + NotePad.Notes.COLUMN_NAME_NOTEBOOK_ID + " IS NULL");
           }

           if (oldVersion < 4) {
               // 待办现在位于独立页面而不再嵌在笔记内，因此旧版编辑器创建的
               // 待办会与其笔记解绑。
               db.execSQL("UPDATE " + NotePad.Todos.TABLE_NAME + " SET "
                       + NotePad.Todos.COLUMN_NAME_NOTE_ID + " = NULL");
           }

           if (oldVersion < 5) {
               // 界面现已改为中文，将旧版本创建的笔记本翻译为中文。
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

           if (oldVersion < 6) {
               // 待办新增截止日期，旧数据默认为未设置（NULL）。
               try {
                   db.execSQL("ALTER TABLE " + NotePad.Todos.TABLE_NAME + " ADD COLUMN "
                           + NotePad.Todos.COLUMN_NAME_DEADLINE + " INTEGER");
               } catch (SQLException e) {
                   Log.w(TAG, "deadline column already exists", e);
               }
           }
       }
   }

   /**
    * 通过创建新的 DatabaseHelper 来初始化提供者。
    */
   @Override
   public boolean onCreate() {
       mOpenHelper = new DatabaseHelper(getContext());
       return true;
   }

   /**
    * 查询数据库并返回包含结果的游标。
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
           case NOTEBOOKS:
           case NOTEBOOK_ID:
               qb.setProjectionMap(sNotebooksProjectionMap);
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
    * 返回指定 URI 的 MIME 数据类型。
    */
   @Override
   public String getType(Uri uri) {
       switch (sUriMatcher.match(uri)) {
           case NOTES:
               return NotePad.Notes.CONTENT_TYPE;
           case NOTE_ID:
               return NotePad.Notes.CONTENT_ITEM_TYPE;
           case NOTEBOOKS:
               return NotePad.Notebooks.CONTENT_TYPE;
           case NOTEBOOK_ID:
               return NotePad.Notebooks.CONTENT_ITEM_TYPE;
           case TODOS:
               return NotePad.Todos.CONTENT_TYPE;
           case TODO_ID:
               return NotePad.Todos.CONTENT_ITEM_TYPE;
           default:
               throw new IllegalArgumentException("Unknown URI " + uri);
       }
    }

    /**
     * 向数据库中插入新行，并为传入映射中未包含的列设置默认值。
     */
    @Override
    public Uri insert(Uri uri, ContentValues initialValues) {

        int match = sUriMatcher.match(uri);
        if (match != NOTES && match != NOTEBOOKS && match != TODOS) {
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
     * 从数据库中删除记录。删除笔记时会同时删除其待办；
     * 删除笔记本时会删除其中的所有笔记。
     */
    @Override
    public int delete(Uri uri, String where, String[] whereArgs) {

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int match = sUriMatcher.match(uri);
        String table = tableFor(match);

        String id = isItemUri(match) ? uri.getLastPathSegment() : null;

        if (match == NOTE_ID) {
            // 删除该笔记的子记录。
            db.delete(NotePad.Todos.TABLE_NAME,
                    NotePad.Todos.COLUMN_NAME_NOTE_ID + "=" + id, null);
        } else if (match == NOTEBOOK_ID) {
            // 删除该笔记本下每条笔记的子记录。
            String noteIds = "SELECT " + NotePad.Notes._ID + " FROM " + NotePad.Notes.TABLE_NAME
                    + " WHERE " + NotePad.Notes.COLUMN_NAME_NOTEBOOK_ID + "=" + id;
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

        // 删除笔记本也会改变笔记目录。
        if (match == NOTEBOOK_ID) {
            getContext().getContentResolver().notifyChange(NotePad.Notes.CONTENT_URI, null);
        }

        notifyWidget();
        return count;
    }

    /**
     * 更新数据库中的记录。
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

    /** 笔记发生变化后刷新主屏上的所有便签。 */
    private void notifyWidget() {
        Intent intent = new Intent(NoteWidgetProvider.ACTION_NOTES_CHANGED);
        intent.setPackage(getContext().getPackageName());
        getContext().sendBroadcast(intent);
    }

    /**
     * 测试包可调用此方法获取 NotePadProvider 底层数据库的句柄。
     */
    DatabaseHelper getOpenHelperForTest() {
        return mOpenHelper;
    }
}
