package com.example.android.notepad;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;

/**
 * 连接笔记列表与待办页面共用的悬浮底栏，并高亮当前打开的标签页。
 */
public final class BottomNav {

    private BottomNav() {
    }

    /**
     * 绑定指定 Activity 的底栏。
     *
     * @param activity       宿主 Activity
     * @param notesSelected  为 true 时高亮笔记标签，为 false 时高亮待办标签
     */
    public static void bind(Activity activity, boolean notesSelected) {
        View notes = activity.findViewById(R.id.nav_notes);
        View todos = activity.findViewById(R.id.nav_todos);
        if (notes == null || todos == null) {
            return;
        }

        final Activity host = activity;
        notes.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openNotes(host);
            }
        });
        todos.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openTodos(host);
            }
        });

        highlight(activity, notes, R.id.nav_notes_label, notesSelected);
        highlight(activity, todos, R.id.nav_todos_label, !notesSelected);
    }

    private static void openNotes(Activity activity) {
        if (activity instanceof NotebookList) {
            return;
        }
        // 复用已有的笔记簿列表，而不是在任务栈中再叠一个副本。
        Intent intent = new Intent(activity, NotebookList.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        activity.startActivity(intent);
    }

    private static void openTodos(Activity activity) {
        if (activity instanceof TodoList) {
            return;
        }
        activity.startActivity(new Intent(activity, TodoList.class));
    }

    private static void highlight(Activity activity, View item, int labelId, boolean selected) {
        item.setSelected(selected);
        item.setAlpha(selected ? 1f : 0.72f);
        TextView label = (TextView) item.findViewById(labelId);
        if (label != null) {
            label.setTextColor(activity.getResources().getColor(
                    selected ? R.color.nav_active_text : R.color.nav_inactive_text));
        }
    }
}
