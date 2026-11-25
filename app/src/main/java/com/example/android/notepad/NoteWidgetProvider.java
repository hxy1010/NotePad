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

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.view.View;
import android.widget.RemoteViews;

import java.util.ArrayList;

/**
 * 主屏幕上的便签，列出待完成的待办。每一行都可以直接在桌面上勾选完成；
 * 点击其他区域会打开独立的待办页面 {@link TodoList}。
 */
public class NoteWidgetProvider extends AppWidgetProvider {

    /** 数据变化时由 {@link NotePadProvider} 发出，用于让小组件立即刷新。 */
    public static final String ACTION_NOTES_CHANGED =
            "com.example.android.notepad.ACTION_NOTES_CHANGED";

    /** 用户在小部件某一行勾选待办时发出。 */
    public static final String ACTION_TICK_TODO =
            "com.example.android.notepad.ACTION_TICK_TODO";

    /** 携带被勾选待办 id 的 Extra。 */
    public static final String EXTRA_TODO_ID = "todo_id";

    /** 小部件最多显示的待办行数。 */
    private static final int MAX_ROWS = 5;

    private static final int[] ROW_IDS = new int[] {
            R.id.widget_row_1, R.id.widget_row_2, R.id.widget_row_3,
            R.id.widget_row_4, R.id.widget_row_5,
    };

    private static final int[] TODO_TEXT_IDS = new int[] {
            R.id.widget_todo_1, R.id.widget_todo_2, R.id.widget_todo_3,
            R.id.widget_todo_4, R.id.widget_todo_5,
    };

    private static final String[] PROJECTION = new String[] {
            NotePad.Todos._ID,
            NotePad.Todos.COLUMN_NAME_CONTENT,
            NotePad.Todos.COLUMN_NAME_DONE,
    };

    private static final String SORT_ORDER =
            NotePad.Todos.COLUMN_NAME_DONE + " ASC, "
                    + NotePad.Todos.COLUMN_NAME_DEADLINE + " IS NULL, "
                    + NotePad.Todos.COLUMN_NAME_DEADLINE + " ASC, "
                    + NotePad.Todos.COLUMN_NAME_CREATE_DATE + " DESC";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        String action = intent.getAction();
        if (ACTION_TICK_TODO.equals(action)) {
            tickTodo(context, intent.getLongExtra(EXTRA_TODO_ID, -1));
            refreshAll(context);
        } else if (ACTION_NOTES_CHANGED.equals(action)) {
            refreshAll(context);
        }
    }

    /** 把被点击的待办标记为已完成。 */
    private static void tickTodo(Context context, long todoId) {
        if (todoId <= 0) {
            return;
        }
        ContentValues values = new ContentValues();
        values.put(NotePad.Todos.COLUMN_NAME_DONE, 1);
        context.getContentResolver().update(
                ContentUris.withAppendedId(NotePad.Todos.CONTENT_ID_URI_BASE, todoId),
                values, null, null);
    }

    private static void refreshAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(
                new ComponentName(context, NoteWidgetProvider.class));
        for (int id : ids) {
            updateWidget(context, manager, id);
        }
    }

    /** 加载待完成的待办，并用它们绘制一个小部件实例。 */
    private static void updateWidget(Context context, AppWidgetManager manager, int widgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_note);

        ArrayList<Long> pendingIds = new ArrayList<Long>();
        ArrayList<String> pendingTexts = new ArrayList<String>();
        int totalCount = 0;
        int doneCount = 0;
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(
                    NotePad.Todos.CONTENT_URI, PROJECTION, null, null, SORT_ORDER);

            while (cursor != null && cursor.moveToNext()) {
                totalCount++;
                if (cursor.getInt(2) != 0) {
                    doneCount++;
                    continue;
                }
                if (pendingIds.size() >= MAX_ROWS) {
                    continue;
                }
                String content = cursor.getString(1);
                content = content == null ? "" : content.trim().replaceAll("\\s+", " ");
                if (content.length() > 40) {
                    content = content.substring(0, 40) + "…";
                }
                pendingIds.add(cursor.getLong(0));
                pendingTexts.add(content);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        // 把每个可见行绑定到一条待办；整行本身就是勾选的目标。
        for (int i = 0; i < MAX_ROWS; i++) {
            if (i < pendingTexts.size()) {
                long todoId = pendingIds.get(i);
                views.setViewVisibility(ROW_IDS[i], View.VISIBLE);
                views.setTextViewText(TODO_TEXT_IDS[i], pendingTexts.get(i));

                Intent tick = new Intent(context, NoteWidgetProvider.class);
                tick.setAction(ACTION_TICK_TODO);
                tick.putExtra(EXTRA_TODO_ID, todoId);
                views.setOnClickPendingIntent(ROW_IDS[i], PendingIntent.getBroadcast(
                        context, (int) todoId, tick, PendingIntent.FLAG_UPDATE_CURRENT));
            } else {
                views.setViewVisibility(ROW_IDS[i], View.GONE);
            }
        }

        if (totalCount == 0) {
            views.setViewVisibility(R.id.widget_content, View.VISIBLE);
            views.setViewVisibility(R.id.widget_rows, View.GONE);
            views.setTextViewText(R.id.widget_title,
                    context.getString(R.string.widget_todo_empty_title));
            views.setTextViewText(R.id.widget_content,
                    context.getString(R.string.widget_todo_empty_content));
            views.setTextViewText(R.id.widget_time, "");
        } else {
            views.setTextViewText(R.id.widget_title,
                    context.getString(R.string.widget_title_todo));
            if (pendingTexts.isEmpty()) {
                views.setViewVisibility(R.id.widget_content, View.VISIBLE);
                views.setViewVisibility(R.id.widget_rows, View.GONE);
                views.setTextViewText(R.id.widget_content,
                        context.getString(R.string.widget_todo_all_done));
            } else {
                views.setViewVisibility(R.id.widget_content, View.GONE);
                views.setViewVisibility(R.id.widget_rows, View.VISIBLE);
            }
            views.setTextViewText(R.id.widget_time, context.getString(
                    R.string.widget_todo_count, totalCount, doneCount));
        }

        Intent open = new Intent(context, TodoList.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT);
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent);

        manager.updateAppWidget(widgetId, views);
    }
}
