package com.example.android.notepad;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CursorAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * 独立的待办页面。待办不再依附于单条笔记：此页面列出全部待办，用户可以新增待办、
 * 勾选完成，并通过长按删除。
 */
public class TodoList extends ListActivity {

    private static final String[] PROJECTION = new String[] {
            NotePad.Todos._ID,
            NotePad.Todos.COLUMN_NAME_CONTENT,
            NotePad.Todos.COLUMN_NAME_DONE,
            NotePad.Todos.COLUMN_NAME_CREATE_DATE,
            NotePad.Todos.COLUMN_NAME_DEADLINE,
    };

    /** 未完成的排在前面；同一状态下按截止日期升序，未设置截止日期的排在最后。 */
    private static final String SORT_ORDER =
            NotePad.Todos.COLUMN_NAME_DONE + " ASC, "
                    + NotePad.Todos.COLUMN_NAME_DEADLINE + " IS NULL, "
                    + NotePad.Todos.COLUMN_NAME_DEADLINE + " ASC, "
                    + NotePad.Todos.COLUMN_NAME_CREATE_DATE + " DESC";

    /** 格式化每行末尾显示的创建时间。 */
    private final SimpleDateFormat mDateFormat = new SimpleDateFormat(
            "yyyy-MM-dd HH:mm", Locale.getDefault());

    /** 格式化截止日期（只显示日期部分）。 */
    private final SimpleDateFormat mDeadlineFormat = new SimpleDateFormat(
            "yyyy-MM-dd", Locale.getDefault());

    private TodoAdapter mAdapter;
    private Cursor mCursor;

    private final ContentObserver mObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            queryTodos();
        }
    };

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(ThemeUtils.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.todo_list);
        BottomNav.bind(this, false);

        findViewById(R.id.empty_new_todo).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTodoDialog(-1);
            }
        });

        getContentResolver().registerContentObserver(
                NotePad.Todos.CONTENT_URI, true, mObserver);
    }

    @Override
    protected void onResume() {
        super.onResume();
        queryTodos();
    }

    private void queryTodos() {
        Cursor cursor = getContentResolver().query(
                NotePad.Todos.CONTENT_URI, PROJECTION, null, null, SORT_ORDER);
        if (cursor == null) {
            return;
        }

        if (mAdapter == null) {
            mAdapter = new TodoAdapter(this, cursor);
            setListAdapter(mAdapter);
            mCursor = cursor;
        } else {
            mAdapter.changeCursor(cursor);
            mCursor = cursor;
        }
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
        inflater.inflate(R.menu.todo_options_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_add_todo) {
            showTodoDialog(-1);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** 点击某一行的空白区域时，打开编辑对话框。 */
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        if (id > 0) {
            showTodoDialog(id);
        }
    }

    /**
     * 弹出新建或编辑待办的对话框，todoId 小于等于 0 时表示新建。
     * 对话框中可以修改待办内容，并选择或清除截止日期。
     */
    private void showTodoDialog(final long todoId) {
        final boolean editing = todoId > 0;
        View contentView = getLayoutInflater().inflate(R.layout.todo_edit_dialog, null);
        final EditText input = (EditText) contentView.findViewById(R.id.todo_edit_content);
        final TextView deadlineView =
                (TextView) contentView.findViewById(R.id.todo_edit_deadline);
        final TextView clearView = (TextView) contentView.findViewById(R.id.todo_edit_clear);

        // deadline[0] 为 0 表示未设置截止日期。
        final long[] deadline = new long[] { 0 };
        if (editing) {
            Cursor cursor = getContentResolver().query(
                    ContentUris.withAppendedId(NotePad.Todos.CONTENT_ID_URI_BASE, todoId),
                    new String[] {
                            NotePad.Todos.COLUMN_NAME_CONTENT,
                            NotePad.Todos.COLUMN_NAME_DEADLINE,
                    },
                    null, null, null);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        String text = cursor.getString(0);
                        input.setText(text);
                        if (text != null) {
                            input.setSelection(text.length());
                        }
                        if (!cursor.isNull(1)) {
                            deadline[0] = cursor.getLong(1);
                        }
                    }
                } finally {
                    cursor.close();
                }
            }
        }

        updateDeadlineView(deadlineView, clearView, deadline[0]);

        deadlineView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDeadlinePicker(deadline, deadlineView, clearView);
            }
        });

        clearView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deadline[0] = 0;
                updateDeadlineView(deadlineView, clearView, 0);
            }
        });

        new AlertDialog.Builder(this)
                .setTitle(editing ? R.string.todo_edit_title : R.string.todo_add_title)
                .setView(contentView)
                .setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String content = input.getText().toString().trim();
                        if (content.length() == 0) {
                            Toast.makeText(TodoList.this, R.string.todo_content_empty,
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        ContentValues values = new ContentValues();
                        values.put(NotePad.Todos.COLUMN_NAME_CONTENT, content);
                        if (deadline[0] > 0) {
                            values.put(NotePad.Todos.COLUMN_NAME_DEADLINE, deadline[0]);
                        } else {
                            values.putNull(NotePad.Todos.COLUMN_NAME_DEADLINE);
                        }
                        if (editing) {
                            getContentResolver().update(
                                    ContentUris.withAppendedId(
                                            NotePad.Todos.CONTENT_ID_URI_BASE, todoId),
                                    values, null, null);
                        } else {
                            values.put(NotePad.Todos.COLUMN_NAME_DONE, 0);
                            getContentResolver().insert(NotePad.Todos.CONTENT_URI, values);
                        }
                    }
                })
                .setNegativeButton(R.string.button_cancel, null)
                .show();
    }

    /** 根据截止日期更新对话框中的文字与"清除"按钮的可见性。 */
    private void updateDeadlineView(TextView deadlineView, TextView clearView, long deadline) {
        if (deadline > 0) {
            deadlineView.setText(getString(R.string.todo_deadline_value,
                    mDeadlineFormat.format(new Date(deadline))));
            clearView.setVisibility(View.VISIBLE);
        } else {
            deadlineView.setText(R.string.todo_deadline_none);
            clearView.setVisibility(View.GONE);
        }
    }

    private void confirmDeleteTodo(final long todoId) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.menu_delete)
                .setMessage(R.string.delete_todo_message)
                .setPositiveButton(R.string.menu_delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        getContentResolver().delete(
                                ContentUris.withAppendedId(
                                        NotePad.Todos.CONTENT_ID_URI_BASE, todoId),
                                null, null);
                    }
                })
                .setNegativeButton(R.string.button_cancel, null)
                .show();
    }

    /**
     * 显示自绘的中文日期选择弹窗。系统的 DatePickerDialog 月份名称跟随系统语言，
     * 英文系统下会显示英文，因此这里使用三个滚轮自行拼出中文界面。
     */
    private void showDeadlinePicker(final long[] deadline,
            final TextView deadlineView, final TextView clearView) {
        View contentView = getLayoutInflater().inflate(R.layout.date_picker_dialog, null);
        final NumberPicker yearPicker =
                (NumberPicker) contentView.findViewById(R.id.picker_year);
        final NumberPicker monthPicker =
                (NumberPicker) contentView.findViewById(R.id.picker_month);
        final NumberPicker dayPicker =
                (NumberPicker) contentView.findViewById(R.id.picker_day);

        Calendar calendar = Calendar.getInstance();
        if (deadline[0] > 0) {
            calendar.setTimeInMillis(deadline[0]);
        }
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        yearPicker.setMinValue(year - 5);
        yearPicker.setMaxValue(year + 10);
        yearPicker.setValue(year);

        String[] months = new String[12];
        for (int i = 0; i < months.length; i++) {
            months[i] = getString(R.string.month_label, i + 1);
        }
        monthPicker.setMinValue(0);
        monthPicker.setMaxValue(11);
        monthPicker.setDisplayedValues(months);
        monthPicker.setValue(month);

        updateDayRange(yearPicker.getValue(), monthPicker.getValue(), dayPicker, day);

        NumberPicker.OnValueChangeListener listener = new NumberPicker.OnValueChangeListener() {
            @Override
            public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
                updateDayRange(yearPicker.getValue(), monthPicker.getValue(), dayPicker,
                        dayPicker.getValue());
            }
        };
        yearPicker.setOnValueChangedListener(listener);
        monthPicker.setOnValueChangedListener(listener);

        new AlertDialog.Builder(this)
                .setTitle(R.string.todo_deadline_pick_title)
                .setView(contentView)
                .setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // 截止时间取当天 23:59，便于按日期比较是否逾期。
                        Calendar picked = Calendar.getInstance();
                        picked.set(yearPicker.getValue(), monthPicker.getValue(),
                                dayPicker.getValue(), 23, 59, 59);
                        picked.set(Calendar.MILLISECOND, 0);
                        deadline[0] = picked.getTimeInMillis();
                        updateDeadlineView(deadlineView, clearView, deadline[0]);
                    }
                })
                .setNegativeButton(R.string.button_cancel, null)
                .show();
    }

    /** 按年月更新「日」滚轮的最大值，并尽量保留原先选中的日期。 */
    private void updateDayRange(int year, int month, NumberPicker dayPicker, int preferredDay) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, month);
        int maxDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
        int day = Math.min(Math.max(preferredDay, 1), maxDay);
        dayPicker.setDisplayedValues(null);
        dayPicker.setMinValue(1);
        dayPicker.setMaxValue(maxDay);
        dayPicker.setValue(day);
    }

    /** 为每条待办渲染一行毛玻璃复选框的 Cursor 适配器。 */
    private class TodoAdapter extends CursorAdapter {

        TodoAdapter(Context context, Cursor cursor) {
            super(context, cursor, 0);
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            return getLayoutInflater().inflate(R.layout.todo_item, parent, false);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            CheckBox checkBox = (CheckBox) view.findViewById(R.id.todo_check);
            TextView deadlineView = (TextView) view.findViewById(R.id.todo_deadline);

            final long todoId = cursor.getLong(cursor.getColumnIndex(NotePad.Todos._ID));
            String content = cursor.getString(
                    cursor.getColumnIndex(NotePad.Todos.COLUMN_NAME_CONTENT));
            boolean done = cursor.getInt(
                    cursor.getColumnIndex(NotePad.Todos.COLUMN_NAME_DONE)) != 0;
            long created = cursor.getLong(
                    cursor.getColumnIndex(NotePad.Todos.COLUMN_NAME_CREATE_DATE));
            int deadlineIndex = cursor.getColumnIndex(NotePad.Todos.COLUMN_NAME_DEADLINE);
            long deadline = cursor.isNull(deadlineIndex) ? 0 : cursor.getLong(deadlineIndex);

            if (deadline > 0) {
                boolean overdue = !done && deadline < System.currentTimeMillis();
                deadlineView.setText(getString(
                        overdue ? R.string.todo_deadline_overdue : R.string.todo_deadline_row,
                        mDeadlineFormat.format(new Date(deadline))));
                deadlineView.setTextColor(getResources().getColor(
                        overdue ? android.R.color.holo_red_dark : R.color.note_text_time));
            } else {
                // 未设置截止日期时仍显示创建时间，保留原有时间信息。
                deadlineView.setText(mDateFormat.format(new Date(created)));
                deadlineView.setTextColor(getResources().getColor(R.color.note_text_time));
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                checkBox.setButtonTintList(android.content.res.ColorStateList.valueOf(
                        getResources().getColor(R.color.note_accent)));
            }

            checkBox.setOnCheckedChangeListener(null);
            checkBox.setText(content);
            checkBox.setChecked(done);
            checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    ContentValues values = new ContentValues();
                    values.put(NotePad.Todos.COLUMN_NAME_DONE, isChecked ? 1 : 0);
                    getContentResolver().update(
                            ContentUris.withAppendedId(
                                    NotePad.Todos.CONTENT_ID_URI_BASE, todoId),
                            values, null, null);
                }
            });
            checkBox.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    confirmDeleteTodo(todoId);
                    return true;
                }
            });

            // 点击行的空白区域（非复选框、非图标）即可编辑待办。
            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showTodoDialog(todoId);
                }
            });

            View edit = view.findViewById(R.id.todo_edit);
            edit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showTodoDialog(todoId);
                }
            });

            View delete = view.findViewById(R.id.todo_delete);
            delete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    confirmDeleteTodo(todoId);
                }
            });
        }
    }
}
