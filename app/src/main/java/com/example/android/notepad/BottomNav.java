package com.example.android.notepad;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;

/**
 * Wires up the floating bottom bar shared by the notes list and the todo page, and highlights
 * the tab that is currently open.
 */
public final class BottomNav {

    private BottomNav() {
    }

    /**
     * Binds the bottom bar of the given activity.
     *
     * @param activity       the host activity
     * @param notesSelected  true to highlight the notes tab, false to highlight the todo tab
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
        // Reuses the existing notebook list instead of stacking another copy on the task.
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
