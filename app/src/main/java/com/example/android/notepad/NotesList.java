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

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


/**
 * 显示便签列表。如果传入的 Intent 中提供了 {@link Uri}，则显示该 URI 对应的便签，
 * 否则默认显示 {@link NotePadProvider} 中的内容。
 *
 * 注意：本 Activity 中的 provider 操作都在 UI 线程上执行。
 * 这不是好做法，这里只是为了代码更易读。真实应用应使用
 * {@link android.content.AsyncQueryHandler} 或 {@link android.os.AsyncTask}
 * 在单独的线程上异步执行这些操作。
 */
public class NotesList extends ListActivity {

    // 用于日志记录和调试
    private static final String TAG = "NotesList";

    /**
     * cursor 适配器所需的列
     */
    private static final String[] PROJECTION = new String[] {
            NotePad.Notes._ID, // 0
            NotePad.Notes.COLUMN_NAME_TITLE, // 1
            NotePad.Notes.COLUMN_NAME_NOTE, // 2
            NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, // 3
    };

    /** 标题列的索引 */
    private static final int COLUMN_INDEX_TITLE = 1;

    /** 列表项布局中要显示的 cursor 列 */
    private static final String[] FROM_COLUMNS = new String[] {
            NotePad.Notes.COLUMN_NAME_TITLE,
            NotePad.Notes.COLUMN_NAME_NOTE,
            NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,
    };

    /** {@link R.layout#noteslist_item} 中用于显示 FROM_COLUMNS 的视图 */
    private static final int[] TO_VIEWS = new int[] {
            R.id.note_title,
            R.id.note_preview,
            R.id.note_time,
    };

    /** Intent extra：所显示便签所属笔记本的 ID */
    public static final String EXTRA_NOTEBOOK_ID = "notebook_id";

    /** Intent extra：所显示便签所属笔记本的名称 */
    public static final String EXTRA_NOTEBOOK_NAME = "notebook_name";

    /** Intent extra：是否只显示未归类的小记 */
    public static final String EXTRA_UNCLASSIFIED = "unclassified";

    /** 正在显示的笔记本；当 Activity 列出所有笔记本的便签时为 -1 */
    private long mNotebookId = -1;

    /** 是否只显示未归类的小记（不属于任何笔记本） */
    private boolean mUnclassifiedOnly;

    /** 为便签列表提供数据 */
    private SimpleCursorAdapter mAdapter;

    /** mAdapter 当前显示的 cursor，以便在 onDestroy() 中关闭 */
    private Cursor mCursor;

    /** 正在显示的便签目录的 URI */
    private Uri mContentUri;

    /** 当前用于过滤列表的搜索关键词，未搜索时为 "" */
    private String mKeyword = "";

    /** 与主题切换器共用的偏好设置 */
    private static final String PREF_NAME = "notepad_prefs";
    private static final String PREF_SORT_ORDER = "note_sort_order";

    /** 排序对话框中提供的排序方式 */
    private static final String[] SORT_ORDERS = new String[] {
            NotePad.Notes.COLUMN_NAME_TITLE + " COLLATE NOCASE DESC",
            NotePad.Notes.COLUMN_NAME_TITLE + " COLLATE NOCASE ASC",
            NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE + " DESC",
            NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE + " ASC",
    };

    /** 排序对话框中显示的标签，与 SORT_ORDERS 一一对应 */
    private static final int[] SORT_LABELS = new int[] {
            R.string.sort_title_desc,
            R.string.sort_title_asc,
            R.string.sort_modified_desc,
            R.string.sort_modified_asc,
    };

    /** 默认排序方式在 SORT_ORDERS 中的下标：修改时间降序 */
    private static final int DEFAULT_SORT_INDEX = 2;

    /** 当前选中的排序方式 */
    private String mSortOrder = SORT_ORDERS[DEFAULT_SORT_INDEX];

    /**
     * 每当便签被插入、更新或删除时重新查询列表，使列表与 provider 保持同步。
     */
    private final ContentObserver mObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            queryNotes(mKeyword);
        }
    };

    /** 格式化列表中显示的修改时间 */
    private final SimpleDateFormat mDateFormat =
            new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(ThemeUtils.wrap(newBase));
    }

    /**
     * Android 从头启动此 Activity 时会调用 onCreate。
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 用户无需按住功能键即可使用菜单快捷键。
        setDefaultKeyMode(DEFAULT_KEYS_SHORTCUT);

        /* 如果启动此 Activity 的 Intent 中没有提供数据，则说明此 Activity 是在
         * intent filter 匹配到 MAIN action 时启动的。此时应使用默认的 provider URI。
         */
        // 获取启动此 Activity 的 intent。
        Intent intent = getIntent();

        // 如果 Intent 中没有关联数据，则将数据设为默认 URI，该 URI 访问便签列表。
        if (intent.getData() == null) {
            intent.setData(NotePad.Notes.CONTENT_URI);
        }
        mContentUri = intent.getData();

        // 仅显示所请求笔记本的便签。
        mNotebookId = intent.getLongExtra(EXTRA_NOTEBOOK_ID, -1);
        mUnclassifiedOnly = intent.getBooleanExtra(EXTRA_UNCLASSIFIED, false);
        String notebookName = intent.getStringExtra(EXTRA_NOTEBOOK_NAME);
        if (notebookName != null) {
            setTitle(notebookName);
        } else if (mUnclassifiedOnly) {
            // 从首页「最近小记」的「全部」进入时，只列出未归类的小记。
            setTitle(R.string.title_all_quick_notes);
        } else if (mNotebookId <= 0) {
            // 未指定笔记簿时显示全部笔记，包括未归类的小记。
            setTitle(R.string.title_all_notes);
        }

        // 填充包含搜索框、便签列表和空状态视图的布局。
        setContentView(R.layout.notes_list);
        BottomNav.bind(this, true);

        // 空状态提供一个真实按钮，使提示与操作相对应。
        findViewById(R.id.empty_new_note).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addNote();
            }
        });

        /*
         * 设置 ListView 上下文菜单激活时的回调。监听器被设为该 Activity，
         * 效果是为 ListView 中的项启用上下文菜单，且上下文菜单由 NotesList 中的
         * 方法来处理。
         */
        getListView().setOnCreateContextMenuListener(this);

        // 用户输入标题或内容关键词时过滤列表。
        EditText searchInput = (EditText) findViewById(R.id.search_input);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                mKeyword = s.toString();
                queryNotes(mKeyword);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        // 监听 provider，使便签被新增、编辑或删除时列表自动刷新。
        getContentResolver().registerContentObserver(mContentUri, true, mObserver);

        // 恢复用户上次选择的排序方式。
        mSortOrder = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
                .getString(PREF_SORT_ORDER, SORT_ORDERS[DEFAULT_SORT_INDEX]);

        // 在用户开始搜索前显示所有便签。
        queryNotes(null);
    }

    /**
     * 查询便签 provider 并更新列表。如果 keyword 不为空，则只显示标题或内容
     * 包含该关键词的便签。
     *
     * @param keyword 要搜索的文本；传 null 则显示所有便签。
     */
    private void queryNotes(String keyword) {
        String selection = null;
        String[] selectionArgs = null;

        if (keyword != null) {
            String trimmed = keyword.trim();
            if (trimmed.length() > 0) {
                selection = "(" + NotePad.Notes.COLUMN_NAME_TITLE + " LIKE ? OR "
                        + NotePad.Notes.COLUMN_NAME_NOTE + " LIKE ?)";
                String like = "%" + trimmed + "%";
                selectionArgs = new String[] { like, like };
            }
        }

        // 将列表限制为从笔记本列表打开的那个笔记本。
        if (mNotebookId > 0) {
            String notebookSelection = NotePad.Notes.COLUMN_NAME_NOTEBOOK_ID + "=" + mNotebookId;
            selection = selection == null ? notebookSelection : notebookSelection + " AND " + selection;
        } else if (mUnclassifiedOnly) {
            // 「全部小记」只显示未归类的小记。
            String unclassifiedSelection = NotePad.Notes.COLUMN_NAME_NOTEBOOK_ID + " IS NULL";
            selection = selection == null ? unclassifiedSelection : unclassifiedSelection + " AND " + selection;
        }

        Cursor cursor = getContentResolver().query(
                mContentUri,
                PROJECTION,
                selection,
                selectionArgs,
                mSortOrder
        );

        if (cursor == null) {
            Log.e(TAG, "Failed to query notes at " + mContentUri);
            return;
        }

        if (mAdapter == null) {
            // 为 ListView 创建后台适配器。
            mAdapter = new SimpleCursorAdapter(
                    this,                       // ListView 所用的 Context
                    R.layout.noteslist_item,    // 指向列表项的 XML
                    cursor,                     // 从中获取条目的 cursor
                    FROM_COLUMNS,
                    TO_VIEWS
            );

            // 格式化便签预览和修改时间。
            mAdapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
                @Override
                public boolean setViewValue(View view, Cursor c, int columnIndex) {
                    String columnName = c.getColumnName(columnIndex);

                    if (NotePad.Notes.COLUMN_NAME_TITLE.equals(columnName)) {
                        // 没有标题时用正文开头代替，方便辨认笔记。
                        String title = c.getString(columnIndex);
                        String note = c.getString(
                                c.getColumnIndex(NotePad.Notes.COLUMN_NAME_NOTE));
                        ((TextView) view).setText(displayTitle(title, note));
                        return true;
                    } else if (NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE.equals(columnName)) {
                        long modified = c.getLong(columnIndex);
                        ((TextView) view).setText(mDateFormat.format(new Date(modified)));

                        // 把该行的垃圾桶图标连接到删除对话框。
                        final long noteId = c.getLong(
                                c.getColumnIndex(NotePad.Notes._ID));
                        String title = c.getString(
                                c.getColumnIndex(NotePad.Notes.COLUMN_NAME_TITLE));
                        String note = c.getString(
                                c.getColumnIndex(NotePad.Notes.COLUMN_NAME_NOTE));
                        final String label = displayTitle(title, note);
                        View row = (View) view.getParent();
                        if (row != null) {
                            View delete = row.findViewById(R.id.note_delete);
                            if (delete != null) {
                                delete.setOnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        confirmDeleteNote(noteId, label);
                                    }
                                });
                            }
                        }
                        return true;
                    } else if (NotePad.Notes.COLUMN_NAME_NOTE.equals(columnName)) {
                        String note = c.getString(columnIndex);
                        if (note != null) {
                            // 保持预览只占一行。
                            note = note.replace('\n', ' ').trim();
                        }
                        ((TextView) view).setText(note);
                        return true;
                    }
                    return false;
                }
            });

            // 把 ListView 的适配器设为刚创建的 cursor 适配器。
            setListAdapter(mAdapter);
            mCursor = cursor;
        } else {
            // 关闭先前的 cursor 并显示新结果。
            mAdapter.changeCursor(cursor);
            mCursor = cursor;
        }

        // 更新空状态视图，说明列表为空的原因。
        TextView emptyView = (TextView) findViewById(R.id.empty_hint);
        if (emptyView != null) {
            boolean searching = keyword != null && keyword.trim().length() > 0;
            emptyView.setText(searching ? R.string.search_no_result : R.string.list_empty);
        }
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

    @Override
    protected void onDestroy() {
        getContentResolver().unregisterContentObserver(mObserver);
        if (mCursor != null) {
            mCursor.close();
            mCursor = null;
        }
        super.onDestroy();
    }

    /**
     * 用户首次点击设备的 Menu 按钮时调用。Android 会传入一个已填充
     * 菜单项的 Menu 对象。
     *
     * 设置一个菜单，提供 Insert 选项以及适用于此 Activity 的一批替代操作。
     * 其他想要处理便签的应用，可以在 Android 中通过提供包含 ALTERNATIVE 类别和
     * mimeTYpe NotePad.Notes.CONTENT_TYPE 的 intent filter 来“注册”自己。
     * 如果它们这样做了，onCreateOptionsMenu() 中的代码会把包含该 intent filter
     * 的 Activity 加入其选项列表。实际效果是，菜单会向用户提供其他可以处理
     * 便签的应用。
     * @param menu Menu 对象，应向其中添加菜单项。
     * @return 始终返回 true。菜单应当显示。
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // 从 XML 资源填充菜单
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list_options_menu, menu);

        // 生成可对整个列表执行的额外操作。在常规安装中，这里
        // 不会找到额外操作，但这允许其他应用用自己的操作
        // 扩展我们的菜单。
        Intent intent = new Intent(null, getIntent().getData());
        intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
        menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0,
                new ComponentName(this, NotesList.class), null, intent, 0, null);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        // 获取当前显示的便签数量。
        final boolean haveItems = getListAdapter().getCount() > 0;

        // 如果列表中有便签（这意味着选中了其中一条），则需要生成
        // 可对当前选中项执行的操作。这些操作将由我们自己的具体操作
        // 以及找到的所有扩展操作组合而成。
        if (haveItems) {

            // 这是被选中的项。
            Uri uri = ContentUris.withAppendedId(getIntent().getData(), getSelectedItemId());

            // 创建一个只含一个元素的 Intent 数组。用于根据选中的菜单项
            // 发送 Intent。
            Intent[] specifics = new Intent[1];

            // 把数组中的 Intent 设为对选中便签 URI 执行 EDIT 操作。
            specifics[0] = new Intent(Intent.ACTION_EDIT, uri);

            // 创建一个只含一个元素的菜单项数组。其中将包含 EDIT 选项。
            MenuItem[] items = new MenuItem[1];

            // 使用选中便签的 URI 创建一个没有指定 action 的 Intent。
            Intent intent = new Intent(null, uri);

            /* 向 Intent 添加 ALTERNATIVE 类别，以便签 ID URI 作为其数据。
             * 这样就把该 Intent 准备为菜单中分组替代选项的容器。
             */
            intent.addCategory(Intent.CATEGORY_ALTERNATIVE);

            /*
             * 向菜单添加替代选项
             */
            menu.addIntentOptions(
                Menu.CATEGORY_ALTERNATIVE,  // 把 Intent 作为选项添加到 alternatives 分组中。
                Menu.NONE,                  // 不需要唯一的菜单项 ID。
                Menu.NONE,                  // 替代选项无需排序。
                null,                       // 调用方的名称不从分组中排除。
                specifics,                  // 这些具体选项必须排在最前面。
                intent,                     // 这些 Intent 对象映射到 specifics 中的选项。
                Menu.NONE,                  // 不需要标志位。
                items                       // 由 specifics 到 Intent 的映射生成的菜单项
            );
                // 如果存在 Edit 菜单项，则为其添加快捷键。
                if (items[0] != null) {

                    // 把 Edit 菜单项的快捷键设为数字 "1"、字母 "e"
                    items[0].setShortcut('1', 'e');
                }
            } else {
                // 如果列表为空，则从菜单中移除已有的替代操作
                menu.removeGroup(Menu.CATEGORY_ALTERNATIVE);
            }

        // 显示菜单
        return true;
    }

    /**
     * 用户从菜单中选择一个选项、但列表中没有选中任何项时调用此方法。
     * 如果选的是 INSERT，则发送一个 action 为 ACTION_INSERT 的新 Intent，
     * 传入 Intent 中的数据会被放入这个新 Intent。实际效果是触发 NotePad 应用中的
     * NoteEditor activity。
     *
     * 如果选中的不是 INSERT，则很可能是来自其他应用的替代选项。此时调用
     * 父类方法来处理该项。
     * @param item 用户选中的菜单项
     * @return 如果选中的是 INSERT 菜单项则返回 true；否则返回调用父类方法的结果。
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_add) {
            /*
             * 使用 Intent 启动新的 Activity。该 Activity 的 intent filter 必须包含
             * action ACTION_INSERT。没有设置类别，因此默认按 DEFAULT 处理。
             * 实际效果是启动 NotePad 中的 NoteEditor Activity。
             */
            addNote();
            return true;
        } else if (item.getItemId() == R.id.menu_sort) {
            showSortDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** 让用户选择便签的排序方式，并记住该选择。 */
    private void showSortDialog() {
        int checked = 0;
        for (int i = 0; i < SORT_ORDERS.length; i++) {
            if (SORT_ORDERS[i].equals(mSortOrder)) {
                checked = i;
            }
        }

        CharSequence[] labels = new CharSequence[SORT_LABELS.length];
        for (int i = 0; i < SORT_LABELS.length; i++) {
            labels[i] = getString(SORT_LABELS[i]);
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.sort_title)
                .setSingleChoiceItems(labels, checked, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mSortOrder = SORT_ORDERS[which];
                        getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit()
                                .putString(PREF_SORT_ORDER, mSortOrder).apply();
                        queryNotes(mKeyword);
                        dialog.dismiss();
                    }
                })
                .show();
    }

    /** 在当前显示的笔记本中打开一条空便签。 */
    private void addNote() {
        Intent addIntent = new Intent(Intent.ACTION_INSERT, getIntent().getData());
        addIntent.putExtra(EXTRA_NOTEBOOK_ID, mNotebookId);
        startActivity(addIntent);
    }

    /** 请求确认，然后从列表中删除该便签。 */
    private void confirmDeleteNote(final long noteId, String label) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_note_title)
                .setMessage(getString(R.string.delete_note_message, label))
                .setPositiveButton(R.string.menu_delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        getContentResolver().delete(
                                ContentUris.withAppendedId(mContentUri, noteId), null, null);
                    }
                })
                .setNegativeButton(R.string.button_cancel, null)
                .show();
    }

    /**
     * 用户在列表中上下文点击（右键）某条便签时调用。NotesList 把自己的 ListView
     * 注册为上下文菜单的处理者（这在 onCreate() 中完成）。
     *
     * 可用的选项只有 OPEN 和 DELETE。
     *
     * 上下文点击等同于长按。
     *
     * @param menu 应向其添加菜单项的 ContexMenu 对象。
     * @param view 正在为其构建上下文菜单的 View。
     * @param menuInfo 与 view 关联的数据。
     * @throws ClassCastException
     */
    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {

        // 来自菜单项的数据。
        AdapterView.AdapterContextMenuInfo info;

        // 尝试获取 ListView 中被长按项的位置。
        try {
            // 把传入的数据对象转换为 AdapterView 对象对应的类型。
            info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        } catch (ClassCastException e) {
            // 如果菜单对象无法转换，则记录错误。
            Log.e(TAG, "bad menuInfo", e);
            return;
        }

        /*
         * 获取与所选位置处项关联的数据。getItem() 返回 ListView 的后台适配器
         * 为该条目关联的内容。在 NotesList 中，适配器把便签的所有数据都关联到了
         * 其列表项上。因此 getItem() 会把这些数据作为 Cursor 返回。
         */
        Cursor cursor = (Cursor) getListAdapter().getItem(info.position);

        // 如果 cursor 为空，则说明适配器因某种原因无法从 provider 获取数据，
        // 此时向调用方返回 null。
        if (cursor == null) {
            // 所请求的项因某种原因不可用，不做任何处理
            return;
        }

        // 从 XML 资源填充菜单
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list_context_menu, menu);

        // 把菜单标题设为所选便签的标题。
        menu.setHeaderTitle(cursor.getString(COLUMN_INDEX_TITLE));

        // 追加其他可以对本便签执行操作的 Activity 的菜单项。
        // 这会在系统中查询实现了我们数据 ALTERNATIVE_ACTION 的
        // Activity，并为每个找到的 Activity 添加一个菜单项。
        Intent intent = new Intent(null, Uri.withAppendedPath(getIntent().getData(), 
                                        Integer.toString((int) info.id) ));
        intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0,
                new ComponentName(this, NotesList.class), null, intent, 0, null);
    }

    /**
     * 用户从上下文菜单中选择一项时调用（参见 onCreateContextMenu()）。实际处理的
     * 菜单项只有 DELETE。其他项都是替代选项，应进行默认处理。
     *
     * @param item 被选中的菜单项
     * @return 如果菜单项是 DELETE 且无需默认处理则返回 true；否则返回 false，
     * 从而触发该菜单项的默认处理。
     * @throws ClassCastException
     */
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        // 来自菜单项的数据。
        AdapterView.AdapterContextMenuInfo info;

        /*
         * 从菜单项中获取额外信息。长按 Notes 列表中的便签时会弹出上下文菜单。
         * 菜单的菜单项会自动获得与被长按便签关联的数据，这些数据来自为列表
         * 提供后端的 provider。
         *
         * 便签数据通过 ContextMenuInfo 对象传给上下文菜单的创建例程。
         *
         * 点击某个上下文菜单项时，同样的数据会连同便签 ID 一起通过 item 参数
         * 传给 onContextItemSelected()。
         */
        try {
            // 把 item 中的数据对象转换为 AdapterView 对象对应的类型。
            info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        } catch (ClassCastException e) {

            // 如果对象无法转换，则记录错误
            Log.e(TAG, "bad menuInfo", e);

            // 触发该菜单项的默认处理。
            return false;
        }
        // 把所选便签的 ID 追加到传入 Intent 携带的 URI 后面。
        Uri noteUri = ContentUris.withAppendedId(getIntent().getData(), info.id);

        /*
         * 获取菜单项的 ID，并将其与已知的 action 进行比较。
         */
        int id = item.getItemId();
        if (id == R.id.context_open) {
            // 启动 Activity 以查看/编辑当前选中的项
            startActivity(new Intent(Intent.ACTION_EDIT, noteUri));
            return true;
        } else if (id == R.id.context_delete) {
            // 通过传入便签 ID 格式的 URI，从 provider 中删除该便签。
            // 关于在 UI 线程上执行 provider 操作的说明，请参见开头的注意事项。
            getContentResolver().delete(
                    noteUri,  // provider 的 URI
                    null,     // 不需要 where 子句，因为只传入了
                    // 单个便签 ID。
                    null      // 没有使用 where 子句，因此不需要 where 参数。
            );

            // 返回调用方，跳过后续处理。
            return true;
        }
        return super.onContextItemSelected(item);
    }

    /**
     * 用户点击显示列表中的某条便签时调用此方法。
     *
     * 本方法处理 PICK（从 provider 获取数据）或 GET_CONTENT（获取或创建数据）
     * 这类传入 action。如果传入的 action 是 EDIT，本方法会发送一个新 Intent
     * 来启动 NoteEditor。
     * @param l 包含被点击项的 ListView
     * @param v 单个项的 View
     * @param position v 在所显示列表中的位置
     * @param id 被点击项的行 ID
     */
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {

        // 由传入 URI 和行 ID 构造新的 URI
        Uri uri = ContentUris.withAppendedId(getIntent().getData(), id);

        // 获取传入 Intent 的 action
        String action = getIntent().getAction();

        // 处理便签数据请求
        if (Intent.ACTION_PICK.equals(action) || Intent.ACTION_GET_CONTENT.equals(action)) {

            // 设置要返回给调用此 Activity 的组件的结果。该结果包含
            // 新的 URI
            setResult(RESULT_OK, new Intent().setData(uri));
        } else {

            // 发送 Intent 以启动能处理 ACTION_EDIT 的 Activity。该 Intent
            // 的数据是便签 ID URI。实际效果是调用 NoteEdit。
            startActivity(new Intent(Intent.ACTION_EDIT, uri));
        }
    }
}
