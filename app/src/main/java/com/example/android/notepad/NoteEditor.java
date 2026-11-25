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

import static com.example.android.notepad.R.*;

import android.app.Activity;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.EditText;

/**
 * 本 Activity 负责“编辑”便签，其中的编辑是指响应 {@link Intent#ACTION_VIEW}
 * （请求查看数据）、编辑便签 {@link Intent#ACTION_EDIT}，或新建便签
 * {@link Intent#ACTION_INSERT}。
 *
 * 注意：本 Activity 中的 provider 操作都在 UI 线程上执行。
 * 这不是好做法，这里只是为了代码更易读。真实应用应使用
 * {@link android.content.AsyncQueryHandler} 或 {@link android.os.AsyncTask}
 * 在单独的线程上异步执行这些操作。
 */
public class NoteEditor extends Activity {
    // 用于日志记录和调试
    private static final String TAG = "NoteEditor";

    /*
     * 创建投影，返回便签 ID 和便签内容。
     */
    private static final String[] PROJECTION =
        new String[] {
            NotePad.Notes._ID,
            NotePad.Notes.COLUMN_NAME_TITLE,
            NotePad.Notes.COLUMN_NAME_NOTE
    };

    // Activity 已保存状态对应的键
    private static final String ORIGINAL_CONTENT = "origContent";

    // 本 Activity 可由多个 action 启动。每个 action 用一个“状态”常量表示
    private static final int STATE_EDIT = 0;
    private static final int STATE_INSERT = 1;

    // 全局可变变量
    private int mState;
    private Uri mUri;
    private Cursor mCursor;
    private EditText mText;
    private EditText mTitle;
    private String mOriginalContent;

    /**
     * 定义自定义的 EditText 视图，在显示的每一行文字之间绘制横线。
     */
    public static class LinedEditText extends EditText {
        private Rect mRect;
        private Paint mPaint;

        // 该构造函数由 LayoutInflater 调用
        public LinedEditText(Context context, AttributeSet attrs) {
            super(context, attrs);

            // 创建 Rect 和 Paint 对象，并设置 Paint 对象的样式与颜色。
            mRect = new Rect();
            mPaint = new Paint();
            mPaint.setStyle(Paint.Style.STROKE);
            // 一条浅色细线，跟随日间/夜间配色。
            mPaint.setColor(context.getResources().getColor(R.color.editor_line));
        }

        /**
         * 绘制 LinedEditText 对象时会调用此方法
         * @param canvas 绘制背景所用的画布。
         */
        @Override
        protected void onDraw(Canvas canvas) {

            // 获取视图中文本的行数。
            int count = getLineCount();

            // 获取全局的 Rect 和 Paint 对象
            Rect r = mRect;
            Paint paint = mPaint;

            /*
             * 为 EditText 中的每一行文字在矩形内绘制一条线
             */
            for (int i = 0; i < count; i++) {

                // 获取当前行文字的基线坐标
                int baseline = getLineBounds(i, r);

                /*
                 * 在背景上从矩形左侧到右侧绘制一条线，位置在基线下方一个 dip 处，
                 * 具体细节由“paint”对象决定。
                 */
                canvas.drawLine(r.left, baseline + 1, r.right, baseline + 1, paint);
            }

            // 最后调用父类方法完成收尾
            super.onDraw(canvas);
        }
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(ThemeUtils.wrap(newBase));
    }

    /**
     * Activity 首次启动时由 Android 调用。它根据传入的 Intent 判断需要哪种编辑操作，
     * 然后执行该操作。
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /*
         * 创建一个 Intent，用于把 Activity 的结果回传给调用方。
         */
        final Intent intent = getIntent();

        /*
         *  根据传入 Intent 指定的 action，为编辑操作做相应准备。
         */

        // 获取触发本 Activity intent filter 的 action
        final String action = intent.getAction();

        // 如果是编辑操作：
        if (Intent.ACTION_EDIT.equals(action)) {

            // 将 Activity 状态设为 EDIT，并获取待编辑数据的 URI。
            mState = STATE_EDIT;
            mUri = intent.getData();

            // 如果是插入操作：
        } else if (Intent.ACTION_INSERT.equals(action)) {

            // 将 Activity 状态设为 INSERT，获取便签的通用 URI，并在 provider 中
            // 针对所打开的笔记本插入一条空记录。
            mState = STATE_INSERT;
            ContentValues insertValues = new ContentValues();
            long notebookId = intent.getLongExtra(NotesList.EXTRA_NOTEBOOK_ID, -1);
            if (notebookId > 0) {
                insertValues.put(NotePad.Notes.COLUMN_NAME_NOTEBOOK_ID, notebookId);
            }
            mUri = getContentResolver().insert(intent.getData(), insertValues);

            /*
             * 如果插入新便签失败，则关闭此 Activity。发起方如果请求了结果，
             * 会收到 RESULT_CANCELED，同时记录插入失败日志。
             */
            if (mUri == null) {

                // 写入日志标识、消息和失败的 URI。
                Log.e(TAG, "Failed to insert new note into " + getIntent().getData());

                // 关闭 Activity。
                finish();
                return;
            }

            // 由于新条目已创建，这里设置要返回的结果
            // 设置要返回的结果。
            setResult(RESULT_OK, (new Intent()).setAction(mUri.toString()));

        // 如果 action 不是 EDIT 或 INSERT：
        } else {

            // 记录“无法识别的 action”错误，结束 Activity，并向发起方返回 RESULT_CANCELED。
            Log.e(TAG, "Unknown action, exiting");
            finish();
            return;
        }

        /*
         * 使用触发 Intent 中传入的 URI，从 provider 获取便签数据。
         * 注意：此操作在 UI 线程上执行，会阻塞该线程直到查询完成。在示例应用中，
         * 由于面对的是基于本地数据库的简单 provider，阻塞只是瞬间的；但在真实应用中
         * 应使用 android.content.AsyncQueryHandler 或 android.os.AsyncTask。
         */
        mCursor = managedQuery(
            mUri,         // 用于从 provider 获取多条便签的 URI。
            PROJECTION,   // 投影，返回每条便签的 ID 和内容。
            null,         // 没有 "where" 子句选择条件。
            null,         // 没有 "where" 子句选择值。
            null          // 使用默认排序（修改时间降序）
        );

        // 设置此 Activity 的布局。参见 res/layout/note_editor.xml
        setContentView(R.layout.note_editor);

        // 获取布局中标题和正文输入框的引用。
        mTitle = (EditText) findViewById(R.id.note_title);
        mText = (EditText) findViewById(R.id.note);

        /*
         * 如果此 Activity 之前曾停止过，其状态会被写入已保存实例状态中的
         * ORIGINAL_CONTENT 位置。这里取出该状态。
         */
        if (savedInstanceState != null) {
            mOriginalContent = savedInstanceState.getString(ORIGINAL_CONTENT);
        }
    }

    /**
     * Activity 即将进入前台时调用。这发生在 Activity 到达任务栈顶部时，
     * 或它首次启动时。
     *
     * 移动到列表中的第一条便签，根据用户选择的 action 设置合适的标题，
     * 把便签内容放入 TextView，并将原始文本备份保存。
     */
    @Override
    protected void onResume() {
        super.onResume();

        /*
         * 对于任何运行中的进程，onCreate() 总在 onResume 之前执行，因此 mCursor
         * 已经初始化。这里检查它不为 null，因为它本应始终包含数据。
         */
        if (mCursor != null) {
            // 重新查询，以防暂停期间有内容发生变化（例如标题）
            mCursor.requery();

            // 此界面暂停期间，该便签可能已从列表中被删除。
            if (mCursor.getCount() == 0) {
                finish();
                return;
            }

            /* 移动到第一条记录。首次访问 Cursor 中的数据前，务必先调用 moveToFirst()。
             * 使用 Cursor 的语义是：创建时其内部索引指向第一条记录之前的“位置”。
             */
            mCursor.moveToFirst();

            // 根据当前 Activity 状态修改 Activity 的窗口标题。
            if (mState == STATE_EDIT) {
                // 将 Activity 标题设置为包含便签标题
                int colTitleIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_TITLE);
                String title = mCursor.getString(colTitleIndex);
                Resources res = getResources();
                String text = String.format(res.getString(R.string.title_edit), title);
                setTitle(text);
            // 对于插入操作，将标题设为“新建”
            } else if (mState == STATE_INSERT) {
                setTitle(getText(R.string.title_create));
            }

            /*
             * onResume() 可能在 Activity 失去焦点（被暂停）后被调用。
             * Activity 暂停时用户正在编辑或新建便签。Activity 应重新显示之前
             * 获取的文本，但不应移动光标，以便用户继续编辑或输入。
             */

            // 从 Cursor 取出便签文本放入 TextView，但不改变
            // 文本光标的位置。
            int colNoteIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_NOTE);
            String note = mCursor.getString(colNoteIndex);
            mText.setTextKeepState(note);

            // 取出标题并显示在标题输入框中。
            int colTitleIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_TITLE);
            String title = mCursor.getString(colTitleIndex);
            mTitle.setTextKeepState(title == null ? "" : title);

            // 保存原始便签文本，以便用户撤销更改。
            if (mOriginalContent == null) {
                mOriginalContent = note;
            }

        /*
         * 出了问题。Cursor 本应始终包含数据。在便签中报告错误。
         */
        } else {
            setTitle(getText(R.string.error_title));
            mText.setText(getText(R.string.error_message));
        }
    }

    /**
     * Activity 在正常运行中失去焦点，随后被销毁时会调用此方法。
     * Activity 有机会保存自身状态，以便系统恢复。
     *
     * 注意此方法并不是 Activity 生命周期的常规部分。如果用户只是离开
     * Activity，不会调用它。
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // 把原始文本保存起来，这样即使 Activity 在暂停时被销毁也仍有备份。
        outState.putString(ORIGINAL_CONTENT, mOriginalContent);
    }

    /**
     * Activity 失去焦点时调用。
     *
     * 对于编辑信息的 Activity，onPause() 可能是唯一保存更改的地方。
     * Android 应用模型的前提是“保存”和“退出”并非必需的操作。用户离开 Activity 时，
     * 不应再返回其中完成工作。离开这一动作本身应保存所有内容，并使 Activity
     * 处于 Android 可随时销毁的状态。
     *
     * 如果用户没有做任何修改，则删除或清空该便签，否则把用户的工作写入 provider。
     */
    @Override
    protected void onPause() {
        super.onPause();

        /*
         * 检查查询操作是否失败（参见 onCreate()）。除非查询因异常或错误而失败，
         * 否则即使没有返回任何记录，Cursor 对象也会存在。
         *
         */
        if (mCursor != null) {

            // 获取当前便签文本和标题。
            String text = mText.getText().toString();
            String title = mTitle.getText().toString();
            int length = text.length();

            /*
             * 如果 Activity 正在结束且当前便签既没有标题也没有正文，则向调用方返回
             * CANCELED，并删除该便签。即使该便签此前处于编辑状态也这样做，因为假设
             * 用户是想“清空”（删除）该便签。
             */
            if (isFinishing() && (length == 0) && (title.length() == 0)) {
                setResult(RESULT_CANCELED);
                deleteNote();

                /*
                 * 把编辑内容写入 provider。如果取出了已有便签进入编辑器，*或*新插入了
                 * 便签，就说明该便签被编辑过。后一种情况下，onCreate() 已向 provider
                 * 插入了一条空的便签，而正在被编辑的正是这条新便签。
                 */
            } else if (mState == STATE_EDIT) {
                // 创建用于存放各列新值的映射
                updateNote(text, title);
            } else if (mState == STATE_INSERT) {
                // 标题和正文都为空时先不写入，避免产生一条空笔记。
                if (length > 0 || title.length() > 0) {
                    // 没有填写标题时，根据正文自动生成。
                    updateNote(text, title.length() == 0 ? null : title);
                    mState = STATE_EDIT;
                }
          }
        }
    }

    /**
     * 用户首次点击设备的 Menu 按钮时调用此方法。Android 会传入一个已填充
     * 菜单项的 Menu 对象。
     *
     * 构建编辑和插入用的菜单，并加入那些为处理本应用 MIME 类型而自行注册的
     * 替代操作。
     *
     * @param menu 应向其添加菜单项的 Menu 对象。
     * @return 返回 true 以显示菜单。
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // 从 XML 资源填充菜单
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.editor_options_menu, menu);

        // 仅为已保存的便签添加额外的菜单项
        if (mState == STATE_EDIT) {
            // 追加其他可以对本便签执行操作的 Activity 的菜单项。
            // 这会在系统中查询实现了我们数据 ALTERNATIVE_ACTION 的
            // Activity，并为每个找到的 Activity 添加一个菜单项。
            Intent intent = new Intent(null, mUri);
            intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
            menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0,
                    new ComponentName(this, NoteEditor.class), null, intent, 0, null);
        }

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // 检查便签是否已更改，以启用或禁用撤销选项
        int colNoteIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_NOTE);
        String savedNote = mCursor.getString(colNoteIndex);
        String currentNote = mText.getText().toString();
        if (savedNote.equals(currentNote)) {
            menu.findItem(R.id.menu_revert).setVisible(false);
        } else {
            menu.findItem(R.id.menu_revert).setVisible(true);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    /**
     * 菜单项被选中时调用。Android 会传入选中的项。本方法中的 switch 语句
     * 会调用相应方法执行用户选择的操作。
     *
     * @param item 被选中的 MenuItem
     * @return 返回 true 表示该项已处理完毕，无需进一步处理；返回 false 表示
     * 按 MenuItem 对象中的指示继续后续处理。
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // 处理所有可能的菜单操作。
        int id = item.getItemId();
        if(id== R.id.menu_save) {
            String text = mText.getText().toString();
            String title = mTitle.getText().toString();
            updateNote(text, title.length() == 0 && mState == STATE_INSERT ? null : title);
            finish();
        } else if (id == R.id.menu_delete) {
            deleteNote();
            finish();
        } else if (id == R.id.menu_revert) {
            cancelNote();
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * 用参数提供的文本和标题替换当前便签内容。
     * @param text 要使用的新便签内容。
     * @param title 要使用的新便签标题
     */
    private final void updateNote(String text, String title) {

        // 创建用于存放待更新值的映射。
        ContentValues values = new ContentValues();
        values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, System.currentTimeMillis());

        // 如果操作是插入新便签，则为其生成初始标题。
        if (mState == STATE_INSERT) {

            // 如果参数中没有提供标题，则根据便签文本生成一个。
            if (title == null) {
  
                // 获取便签长度
                int length = text.length();

                // 通过截取文本来设置标题，长度为 31 个字符或便签字符数加一，取较小者。
                title = text.substring(0, Math.min(30, length));
  
                // 如果结果长度超过 30 个字符，则去掉末尾的空格
                if (length > 30) {
                    int lastSpace = title.lastIndexOf(' ');
                    if (lastSpace > 0) {
                        title = title.substring(0, lastSpace);
                    }
                }

                // 当便签没有正文时，保留一个可读的标题。
                if (title.length() == 0) {
                    title = getString(android.R.string.untitled);
                }
            }
            // 在 values 映射中设置标题的值
            values.put(NotePad.Notes.COLUMN_NAME_TITLE, title);
        } else if (title != null) {
            // 在 values 映射中设置标题的值
            values.put(NotePad.Notes.COLUMN_NAME_TITLE, title);
        }

        // 把期望的便签文本放入映射。
        values.put(NotePad.Notes.COLUMN_NAME_NOTE, text);

        /*
         * 用映射中的新值更新 provider。ListView 会自动更新。provider 通过把查询
         * Cursor 对象的通知 URI 设置为传入的 URI 来实现这一点。因此当该 URI 的
         * Cursor 发生变化时，ContentResolver 会自动收到通知，界面随之更新。
         * 注意：此操作在 UI 线程上执行，会阻塞该线程直到更新完成。在示例应用中，
         * 由于面对的是基于本地数据库的简单 provider，阻塞只是瞬间的；但在真实应用中
         * 应使用 android.content.AsyncQueryHandler 或 android.os.AsyncTask。
         */
        getContentResolver().update(
                mUri,    // 要更新的记录的 URI。
                values,  // 列名及其要应用的新值组成的映射。
                null,    // 没有使用选择条件，因此不需要 where 列。
                null     // 没有使用 where 列，因此不需要 where 参数。
            );


    }

    /**
     * 此辅助方法取消对便签所做的修改。如果便签是新建的则删除它，
     * 否则恢复为便签的原始文本
     */
    private final void cancelNote() {
        if (mCursor != null) {
            if (mState == STATE_EDIT) {
                // 将原始便签文本放回数据库
                mCursor.close();
                mCursor = null;
                ContentValues values = new ContentValues();
                values.put(NotePad.Notes.COLUMN_NAME_NOTE, mOriginalContent);
                getContentResolver().update(mUri, values, null, null);
            } else if (mState == STATE_INSERT) {
                // 我们插入的是一条空便签，务必将其删除
                deleteNote();
            }
        }
        setResult(RESULT_CANCELED);
        finish();
    }

    /**
     * 负责删除便签，直接删除对应条目。
     */
    private final void deleteNote() {
        if (mCursor != null) {
            mCursor.close();
            mCursor = null;
            getContentResolver().delete(mUri, null, null);
            mText.setText("");
        }
    }
}
