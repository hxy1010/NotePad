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
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Standalone todo page. Todos are no longer tied to a single note: this screen lists every
 * todo, lets the user add new ones, tick them off and delete them with a long press.
 */
public class TodoList extends ListActivity {

    private static final String[] PROJECTION = new String[] {
            NotePad.Todos._ID,
            NotePad.Todos.COLUMN_NAME_CONTENT,
            NotePad.Todos.COLUMN_NAME_DONE,
            NotePad.Todos.COLUMN_NAME_CREATE_DATE,
    };

    private static final String SORT_ORDER =
            NotePad.Todos.COLUMN_NAME_DONE + " ASC, "
                    + NotePad.Todos.COLUMN_NAME_CREATE_DATE + " DESC";

    /** Formats the creation timestamp shown at the end of each row. */
    private final SimpleDateFormat mDateFormat = new SimpleDateFormat(
            "yyyy-MM-dd HH:mm", Locale.getDefault());

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
                showAddTodoDialog();
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
            showAddTodoDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showAddTodoDialog() {
        final EditText input = new EditText(this);
        input.setHint(R.string.todo_hint);

        new AlertDialog.Builder(this)
                .setTitle(R.string.todo_add_title)
                .setView(input)
                .setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String content = input.getText().toString().trim();
                        if (content.length() == 0) {
                            return;
                        }
                        ContentValues values = new ContentValues();
                        values.put(NotePad.Todos.COLUMN_NAME_CONTENT, content);
                        values.put(NotePad.Todos.COLUMN_NAME_DONE, 0);
                        getContentResolver().insert(NotePad.Todos.CONTENT_URI, values);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
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
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Cursor adapter that renders one glass checkbox row per todo. */
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
            TextView time = (TextView) view.findViewById(R.id.todo_time);

            final long todoId = cursor.getLong(cursor.getColumnIndex(NotePad.Todos._ID));
            String content = cursor.getString(
                    cursor.getColumnIndex(NotePad.Todos.COLUMN_NAME_CONTENT));
            boolean done = cursor.getInt(
                    cursor.getColumnIndex(NotePad.Todos.COLUMN_NAME_DONE)) != 0;
            long created = cursor.getLong(
                    cursor.getColumnIndex(NotePad.Todos.COLUMN_NAME_CREATE_DATE));

            time.setText(mDateFormat.format(new Date(created)));

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
