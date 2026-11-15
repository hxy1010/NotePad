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
 * Home screen sticky note that lists the pending todos. Each row can be ticked off directly on
 * the launcher; tapping anywhere else opens the standalone todo page {@link TodoList}.
 */
public class NoteWidgetProvider extends AppWidgetProvider {

    /** Sent by {@link NotePadProvider} whenever data changes, so the widget refreshes at once. */
    public static final String ACTION_NOTES_CHANGED =
            "com.example.android.notepad.ACTION_NOTES_CHANGED";

    /** Sent by a widget row when the user ticks a todo off. */
    public static final String ACTION_TICK_TODO =
            "com.example.android.notepad.ACTION_TICK_TODO";

    /** Extra carrying the id of the todo that was ticked off. */
    public static final String EXTRA_TODO_ID = "todo_id";

    /** How many todo rows the widget shows at most. */
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

    /** Marks the tapped todo as done. */
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

    /** Loads the pending todos and paints one widget instance with them. */
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

        // Binds every visible row to one pending todo; the row itself is the tick target.
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
