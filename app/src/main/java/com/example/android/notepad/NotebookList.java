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

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SimpleCursorAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 列出所有笔记簿。每个笔记簿归类多条笔记并带有分类。点击笔记簿会打开其中的笔记；
 * 长按笔记簿可以重命名或删除。
 */
public class NotebookList extends ListActivity {

    private static final String[] PROJECTION = new String[] {
            NotePad.Notebooks._ID,
            NotePad.Notebooks.COLUMN_NAME_NAME,
            NotePad.Notebooks.COLUMN_NAME_CATEGORY,
            NotePad.Notebooks.COLUMN_NAME_MODIFICATION_DATE,
    };

    private static final int MENU_RENAME = Menu.FIRST;
    private static final int MENU_DELETE = Menu.FIRST + 1;

    private static final String STATE_CATEGORY_FILTER = "category_filter";

    /** 首页「最近笔记」最多展示的条数。 */
    private static final int RECENT_NOTE_LIMIT = 3;

    /** 「最近笔记」区块所需的列。 */
    private static final String[] RECENT_PROJECTION = new String[] {
            NotePad.Notes._ID,
            NotePad.Notes.COLUMN_NAME_TITLE,
            NotePad.Notes.COLUMN_NAME_NOTE,
            NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,
    };

    private SimpleCursorAdapter mAdapter;
    private Cursor mCursor;

    /** 「最近笔记」行的容器与空状态提示。 */
    private LinearLayout mRecentContainer;
    private TextView mRecentEmpty;

    /** 格式化「最近笔记」中显示的修改时间。 */
    private final SimpleDateFormat mRecentFormat =
            new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());

    /** 当前选中的笔记簿分类；{@code null} 表示显示全部分类。 */
    private String mCategoryFilter;

    /** 笔记簿或其笔记发生变化时刷新列表。 */
    private final ContentObserver mObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            queryNotebooks();
            queryRecentNotes();
        }
    };

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(ThemeUtils.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            mCategoryFilter = savedInstanceState.getString(STATE_CATEGORY_FILTER);
        }

        setContentView(R.layout.notebook_list);
        getListView().setOnCreateContextMenuListener(this);
        BottomNav.bind(this, true);

        findViewById(R.id.empty_new_notebook).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showNotebookDialog(-1, null, null);
            }
        });

        mRecentContainer = (LinearLayout) findViewById(R.id.recent_notes_container);
        mRecentEmpty = (TextView) findViewById(R.id.recent_notes_empty);

        // 「全部」进入只包含未归类小记的列表，与上方的最近小记保持一致。
        findViewById(R.id.recent_all).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(NotebookList.this, NotesList.class);
                intent.setData(NotePad.Notes.CONTENT_URI);
                intent.putExtra(NotesList.EXTRA_UNCLASSIFIED, true);
                startActivity(intent);
            }
        });

        // 直接新建一条未归类的小记，无需先创建笔记簿。
        findViewById(R.id.recent_note_add).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_INSERT, NotePad.Notes.CONTENT_URI);
                intent.setClass(NotebookList.this, NoteEditor.class);
                startActivity(intent);
            }
        });

        getContentResolver().registerContentObserver(
                NotePad.Notebooks.CONTENT_URI, true, mObserver);
        // 副标题中显示的笔记数量也会随笔记变化而更新。
        getContentResolver().registerContentObserver(
                NotePad.Notes.CONTENT_URI, true, mObserver);
    }

    @Override
    protected void onResume() {
        super.onResume();
        queryNotebooks();
        queryRecentNotes();
        updateFilterSubtitle();
    }

    /**
     * 查询最近的未归类小记并填充首页区块。笔记本内的笔记不会出现在这里，
     * 最新的排在最前，最多显示 {@link #RECENT_NOTE_LIMIT} 条。
     */
    private void queryRecentNotes() {
        if (mRecentContainer == null) {
            return;
        }

        Cursor cursor = getContentResolver().query(
                NotePad.Notes.CONTENT_URI,
                RECENT_PROJECTION,
                NotePad.Notes.COLUMN_NAME_NOTEBOOK_ID + " IS NULL",
                null,
                NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE + " DESC");
        if (cursor == null) {
            return;
        }

        mRecentContainer.removeAllViews();
        int count = Math.min(cursor.getCount(), RECENT_NOTE_LIMIT);
        mRecentEmpty.setVisibility(count == 0 ? View.VISIBLE : View.GONE);

        for (int i = 0; i < count; i++) {
            cursor.moveToPosition(i);

            final long noteId = cursor.getLong(
                    cursor.getColumnIndex(NotePad.Notes._ID));
            String title = cursor.getString(
                    cursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_TITLE));
            String note = cursor.getString(
                    cursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_NOTE));
            long modified = cursor.getLong(
                    cursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE));

            View row = getLayoutInflater().inflate(
                    R.layout.recent_note_item, mRecentContainer, false);
            TextView titleView = (TextView) row.findViewById(R.id.recent_note_title);
            titleView.setText(displayTitle(title, note));
            ((TextView) row.findViewById(R.id.recent_note_time))
                    .setText(mRecentFormat.format(new Date(modified)));
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(Intent.ACTION_EDIT,
                            ContentUris.withAppendedId(NotePad.Notes.CONTENT_URI, noteId));
                    intent.setClass(NotebookList.this, NoteEditor.class);
                    startActivity(intent);
                }
            });
            mRecentContainer.addView(row);
        }

        cursor.close();
    }

    /** 有标题时显示标题；没有标题时用正文开头代替，最多保留 20 个字。 */
    private String displayTitle(String title, String note) {
        if (!TextUtils.isEmpty(title)) {
            return title;
        }
        if (TextUtils.isEmpty(note)) {
            return getString(R.string.note_untitled);
        }
        String text = note.replace('\n', ' ').trim();
        if (text.length() > 20) {
            text = text.substring(0, 20) + "…";
        }
        return text;
    }

    /** 在标题下方显示当前分类，让筛选状态始终可见。 */
    private void updateFilterSubtitle() {
        ActionBar bar = getActionBar();
        if (bar != null) {
            bar.setSubtitle(mCategoryFilter == null
                    ? null
                    : getString(R.string.filter_title_with, mCategoryFilter));
        }
    }

    private void queryNotebooks() {
        String selection = null;
        String[] selectionArgs = null;
        if (mCategoryFilter != null) {
            selection = NotePad.Notebooks.COLUMN_NAME_CATEGORY + "=?";
            selectionArgs = new String[] { mCategoryFilter };
        }

        Cursor cursor = getContentResolver().query(
                NotePad.Notebooks.CONTENT_URI,
                PROJECTION,
                selection,
                selectionArgs,
                NotePad.Notebooks.DEFAULT_SORT_ORDER);

        if (cursor == null) {
            return;
        }

        if (mAdapter == null) {
            mAdapter = new SimpleCursorAdapter(
                    this,
                    R.layout.notebooklist_item,
                    cursor,
                    new String[] {
                            NotePad.Notebooks.COLUMN_NAME_NAME,
                            NotePad.Notebooks.COLUMN_NAME_CATEGORY,
                    },
                    new int[] { R.id.notebook_name, R.id.notebook_subtitle });

            mAdapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
                @Override
                public boolean setViewValue(View view, Cursor c, int columnIndex) {
                    if (NotePad.Notebooks.COLUMN_NAME_CATEGORY.equals(c.getColumnName(columnIndex))) {
                        long notebookId = c.getLong(c.getColumnIndex(NotePad.Notebooks._ID));
                        String category = c.getString(columnIndex);
                        int count = countNotes(notebookId);
                        ((TextView) view).setText(
                                getString(R.string.notebook_subtitle, category, count));

                        // 左侧徽标使用类别色与类别首字，让笔记簿和笔记一眼可分。
                        View row = view;
                        while (row != null && row.getId() != R.id.notebook_row) {
                            android.view.ViewParent parent = row.getParent();
                            row = parent instanceof View ? (View) parent : null;
                        }
                        if (row != null) {
                            TextView badge = (TextView) row.findViewById(R.id.notebook_badge);
                            badge.setText(TextUtils.isEmpty(category)
                                    ? "" : category.substring(0, 1));
                            badge.setBackground(createBadgeBackground(category));
                        }
                        return true;
                    }
                    return false;
                }
            });

            setListAdapter(mAdapter);
            mCursor = cursor;
        } else {
            mAdapter.changeCursor(cursor);
            mCursor = cursor;
        }
    }

    /** 返回指定笔记簿下的笔记数量。 */
    private int countNotes(long notebookId) {
        Cursor cursor = getContentResolver().query(
                NotePad.Notes.CONTENT_URI,
                new String[] { NotePad.Notes._ID },
                NotePad.Notes.COLUMN_NAME_NOTEBOOK_ID + "=?",
                new String[] { String.valueOf(notebookId) },
                null);
        if (cursor == null) {
            return 0;
        }
        try {
            return cursor.getCount();
        } finally {
            cursor.close();
        }
    }

    /** 根据笔记簿类别生成圆角徽标背景，颜色与类别一一对应。 */
    private GradientDrawable createBadgeBackground(String category) {
        String[] categories = getResources().getStringArray(R.array.notebook_categories);
        int[] colorResIds = new int[] {
                R.color.category_study,
                R.color.category_work,
                R.color.category_life,
                R.color.category_other,
        };

        int colorResId = R.color.category_other;
        for (int i = 0; i < categories.length && i < colorResIds.length; i++) {
            if (categories[i].equals(category)) {
                colorResId = colorResIds[i];
                break;
            }
        }

        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius(getResources().getDisplayMetrics().density * 12f);
        background.setColor(getResources().getColor(colorResId));
        return background;
    }

    @Override
    protected void onDestroy() {
        getContentResolver().unregisterContentObserver(mObserver);
        if (mCursor != null) {
            mCursor.close();
            mCursor = null;
        }
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.notebook_options_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem filter = menu.findItem(R.id.menu_filter);
        if (filter != null) {
            filter.setTitle(getString(R.string.filter_title_with,
                    mCategoryFilter == null ? getString(R.string.filter_all) : mCategoryFilter));
        }

        MenuItem nightMode = menu.findItem(R.id.menu_night_mode);
        if (nightMode != null) {
            nightMode.setTitle(ThemeUtils.isNightMode(this)
                    ? R.string.menu_day_mode
                    : R.string.menu_night_mode);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_filter) {
            showFilterDialog();
            return true;
        } else if (id == R.id.menu_add_notebook) {
            showNotebookDialog(-1, null, null);
            return true;
        } else if (id == R.id.menu_night_mode) {
            ThemeUtils.setNightMode(this, !ThemeUtils.isNightMode(this));
            recreate();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onListItemClick(android.widget.ListView l, View v, int position, long id) {
        Cursor cursor = (Cursor) getListAdapter().getItem(position);
        if (cursor == null) {
            return;
        }
        long notebookId = cursor.getLong(
                cursor.getColumnIndex(NotePad.Notebooks._ID));
        String name = cursor.getString(
                cursor.getColumnIndex(NotePad.Notebooks.COLUMN_NAME_NAME));

        Intent intent = new Intent(this, NotesList.class);
        intent.setData(NotePad.Notes.CONTENT_URI);
        intent.putExtra(NotesList.EXTRA_NOTEBOOK_ID, notebookId);
        intent.putExtra(NotesList.EXTRA_NOTEBOOK_NAME, name);
        startActivity(intent);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        android.widget.AdapterView.AdapterContextMenuInfo info =
                (android.widget.AdapterView.AdapterContextMenuInfo) menuInfo;
        Cursor cursor = (Cursor) getListAdapter().getItem(info.position);
        if (cursor == null) {
            return;
        }

        menu.setHeaderTitle(cursor.getString(
                cursor.getColumnIndex(NotePad.Notebooks.COLUMN_NAME_NAME)));
        menu.add(0, MENU_RENAME, 0, R.string.menu_rename);
        menu.add(0, MENU_DELETE, 1, R.string.menu_delete);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        android.widget.AdapterView.AdapterContextMenuInfo info =
                (android.widget.AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        Cursor cursor = (Cursor) getListAdapter().getItem(info.position);
        if (cursor == null) {
            return false;
        }

        final long notebookId = cursor.getLong(
                cursor.getColumnIndex(NotePad.Notebooks._ID));
        final String name = cursor.getString(
                cursor.getColumnIndex(NotePad.Notebooks.COLUMN_NAME_NAME));
        final String category = cursor.getString(
                cursor.getColumnIndex(NotePad.Notebooks.COLUMN_NAME_CATEGORY));

        int itemId = item.getItemId();
        if (itemId == MENU_RENAME) {
            showNotebookDialog(notebookId, name, category);
            return true;
        } else if (itemId == MENU_DELETE) {
            confirmDeleteNotebook(notebookId, name);
            return true;
        }

        return super.onContextItemSelected(item);
    }

    /** 先请求确认，然后删除该笔记簿及其所有笔记。 */
    private void confirmDeleteNotebook(final long notebookId, String name) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_notebook_title)
                .setMessage(getString(R.string.delete_notebook_message, name))
                .setPositiveButton(R.string.menu_delete,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                getContentResolver().delete(
                                        ContentUris.withAppendedId(
                                                NotePad.Notebooks.CONTENT_ID_URI_BASE,
                                                notebookId),
                                        null, null);
                            }
                        })
                .setNegativeButton(R.string.button_cancel, null)
                .show();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_CATEGORY_FILTER, mCategoryFilter);
    }

    /** 让用户选择只显示某一分类的笔记簿，或显示全部。 */
    private void showFilterDialog() {
        final String[] categories = getResources().getStringArray(R.array.notebook_categories);
        final CharSequence[] labels = new CharSequence[categories.length + 1];
        labels[0] = getString(R.string.filter_all);
        int checked = 0;
        for (int i = 0; i < categories.length; i++) {
            labels[i + 1] = categories[i];
            if (categories[i].equals(mCategoryFilter)) {
                checked = i + 1;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.filter_title)
                .setSingleChoiceItems(labels, checked, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mCategoryFilter = which == 0 ? null : categories[which - 1];
                        queryNotebooks();
                        updateFilterSubtitle();
                        dialog.dismiss();
                    }
                })
                .show();
    }

    /**
     * 显示新建/重命名对话框。传入小于零的 notebookId 表示新建笔记簿。
     */
    private void showNotebookDialog(final long notebookId, String name, String category) {
        View view = getLayoutInflater().inflate(R.layout.notebook_dialog, null);
        final EditText nameInput = (EditText) view.findViewById(R.id.notebook_name_input);
        final Spinner categorySpinner =
                (Spinner) view.findViewById(R.id.notebook_category_spinner);

        ArrayAdapter<CharSequence> categoryAdapter = ArrayAdapter.createFromResource(
                this, R.array.notebook_categories, android.R.layout.simple_spinner_item);
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(categoryAdapter);

        if (name != null) {
            nameInput.setText(name);
            nameInput.setSelection(name.length());
        }
        if (category != null) {
            String[] categories = getResources().getStringArray(R.array.notebook_categories);
            for (int i = 0; i < categories.length; i++) {
                if (categories[i].equals(category)) {
                    categorySpinner.setSelection(i);
                    break;
                }
            }
        }

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(notebookId < 0 ? R.string.menu_new_notebook : R.string.menu_rename)
                .setView(view)
                .setPositiveButton(R.string.button_ok, null)
                .setNegativeButton(R.string.button_cancel, null)
                .create();

        dialog.show();

        // 名称为空时进行校验，而不是直接关闭对话框。
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String newName = nameInput.getText().toString().trim();
                        if (TextUtils.isEmpty(newName)) {
                            Toast.makeText(NotebookList.this,
                                    R.string.notebook_name_required, Toast.LENGTH_SHORT).show();
                            return;
                        }

                        String newCategory = categorySpinner.getSelectedItem().toString();
                        ContentValues values = new ContentValues();
                        values.put(NotePad.Notebooks.COLUMN_NAME_NAME, newName);
                        values.put(NotePad.Notebooks.COLUMN_NAME_CATEGORY, newCategory);
                        values.put(NotePad.Notebooks.COLUMN_NAME_MODIFICATION_DATE,
                                System.currentTimeMillis());

                        Uri uri = ContentUris.withAppendedId(
                                NotePad.Notebooks.CONTENT_ID_URI_BASE, notebookId);
                        if (notebookId < 0) {
                            getContentResolver().insert(NotePad.Notebooks.CONTENT_URI, values);
                        } else {
                            getContentResolver().update(uri, values, null, null);
                        }
                        dialog.dismiss();
                    }
                });
    }
}
