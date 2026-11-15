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

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * This Activity handles "editing" a note, where editing is responding to
 * {@link Intent#ACTION_VIEW} (request to view data), edit a note
 * {@link Intent#ACTION_EDIT}, create a note {@link Intent#ACTION_INSERT}, or
 * create a new note from the current contents of the clipboard {@link Intent#ACTION_PASTE}.
 *
 * NOTE: Notice that the provider operations in this Activity are taking place on the UI thread.
 * This is not a good practice. It is only done here to make the code more readable. A real
 * application should use the {@link android.content.AsyncQueryHandler}
 * or {@link android.os.AsyncTask} object to perform operations asynchronously on a separate thread.
 */
public class NoteEditor extends Activity {
    // For logging and debugging purposes
    private static final String TAG = "NoteEditor";

    /*
     * Creates a projection that returns the note ID and the note contents.
     */
    private static final String[] PROJECTION =
        new String[] {
            NotePad.Notes._ID,
            NotePad.Notes.COLUMN_NAME_TITLE,
            NotePad.Notes.COLUMN_NAME_NOTE
    };

    // A label for the saved state of the activity
    private static final String ORIGINAL_CONTENT = "origContent";

    // This Activity can be started by more than one action. Each action is represented
    // as a "state" constant
    private static final int STATE_EDIT = 0;
    private static final int STATE_INSERT = 1;

    /** Request codes for the photo picker and the microphone permission request */
    private static final int REQUEST_PICK_IMAGE = 10;
    private static final int REQUEST_RECORD_PERMISSION = 11;

    /** Directory, inside the app's private files, that holds photos and recordings */
    private static final String ATTACHMENT_DIR = "attachments";

    // Global mutable variables
    private int mState;
    private Uri mUri;
    private Cursor mCursor;
    private EditText mText;
    private String mOriginalContent;

    private LinearLayout mPhotoContainer;
    private LinearLayout mAudioContainer;
    private TextView mPhotoSection;
    private TextView mAudioSection;
    private Button mRecordButton;

    private MediaRecorder mRecorder;
    private File mRecordingFile;
    private MediaPlayer mPlayer;
    /** The audio row that is currently playing, if any. */
    private TextView mPlayingAudioView;

    /**
     * Defines a custom EditText View that draws lines between each line of text that is displayed.
     */
    public static class LinedEditText extends EditText {
        private Rect mRect;
        private Paint mPaint;

        // This constructor is used by LayoutInflater
        public LinedEditText(Context context, AttributeSet attrs) {
            super(context, attrs);

            // Creates a Rect and a Paint object, and sets the style and color of the Paint object.
            mRect = new Rect();
            mPaint = new Paint();
            mPaint.setStyle(Paint.Style.STROKE);
            // A faint line that follows the day or night palette.
            mPaint.setColor(context.getResources().getColor(R.color.editor_line));
        }

        /**
         * This is called to draw the LinedEditText object
         * @param canvas The canvas on which the background is drawn.
         */
        @Override
        protected void onDraw(Canvas canvas) {

            // Gets the number of lines of text in the View.
            int count = getLineCount();

            // Gets the global Rect and Paint objects
            Rect r = mRect;
            Paint paint = mPaint;

            /*
             * Draws one line in the rectangle for every line of text in the EditText
             */
            for (int i = 0; i < count; i++) {

                // Gets the baseline coordinates for the current line of text
                int baseline = getLineBounds(i, r);

                /*
                 * Draws a line in the background from the left of the rectangle to the right,
                 * at a vertical position one dip below the baseline, using the "paint" object
                 * for details.
                 */
                canvas.drawLine(r.left, baseline + 1, r.right, baseline + 1, paint);
            }

            // Finishes up by calling the parent method
            super.onDraw(canvas);
        }
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(ThemeUtils.wrap(newBase));
    }

    /**
     * This method is called by Android when the Activity is first started. From the incoming
     * Intent, it determines what kind of editing is desired, and then does it.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /*
         * Creates an Intent to use when the Activity object's result is sent back to the
         * caller.
         */
        final Intent intent = getIntent();

        /*
         *  Sets up for the edit, based on the action specified for the incoming Intent.
         */

        // Gets the action that triggered the intent filter for this Activity
        final String action = intent.getAction();

        // For an edit action:
        if (Intent.ACTION_EDIT.equals(action)) {

            // Sets the Activity state to EDIT, and gets the URI for the data to be edited.
            mState = STATE_EDIT;
            mUri = intent.getData();

            // For an insert or paste action:
        } else if (Intent.ACTION_INSERT.equals(action)
                || Intent.ACTION_PASTE.equals(action)) {

            // Sets the Activity state to INSERT, gets the general note URI, and inserts an
            // empty record in the provider, in the notebook that was opened.
            mState = STATE_INSERT;
            ContentValues insertValues = new ContentValues();
            long notebookId = intent.getLongExtra(NotesList.EXTRA_NOTEBOOK_ID, -1);
            if (notebookId > 0) {
                insertValues.put(NotePad.Notes.COLUMN_NAME_NOTEBOOK_ID, notebookId);
            }
            mUri = getContentResolver().insert(intent.getData(), insertValues);

            /*
             * If the attempt to insert the new note fails, shuts down this Activity. The
             * originating Activity receives back RESULT_CANCELED if it requested a result.
             * Logs that the insert failed.
             */
            if (mUri == null) {

                // Writes the log identifier, a message, and the URI that failed.
                Log.e(TAG, "Failed to insert new note into " + getIntent().getData());

                // Closes the activity.
                finish();
                return;
            }

            // Since the new entry was created, this sets the result to be returned
            // set the result to be returned.
            setResult(RESULT_OK, (new Intent()).setAction(mUri.toString()));

        // If the action was other than EDIT or INSERT:
        } else {

            // Logs an error that the action was not understood, finishes the Activity, and
            // returns RESULT_CANCELED to an originating Activity.
            Log.e(TAG, "Unknown action, exiting");
            finish();
            return;
        }

        /*
         * Using the URI passed in with the triggering Intent, gets the note or notes in
         * the provider.
         * Note: This is being done on the UI thread. It will block the thread until the query
         * completes. In a sample app, going against a simple provider based on a local database,
         * the block will be momentary, but in a real app you should use
         * android.content.AsyncQueryHandler or android.os.AsyncTask.
         */
        mCursor = managedQuery(
            mUri,         // The URI that gets multiple notes from the provider.
            PROJECTION,   // A projection that returns the note ID and note content for each note.
            null,         // No "where" clause selection criteria.
            null,         // No "where" clause selection values.
            null          // Use the default sort order (modification date, descending)
        );

        // For a paste, initializes the data from clipboard.
        // (Must be done after mCursor is initialized.)
        if (Intent.ACTION_PASTE.equals(action)) {
            // Does the paste
            performPaste();
            // Switches the state to EDIT so the title can be modified.
            mState = STATE_EDIT;
        }

        // Sets the layout for this Activity. See res/layout/note_editor.xml
        setContentView(R.layout.note_editor);

        // Gets a handle to the EditText in the the layout.
        mText = (EditText) findViewById(R.id.note);

        // Wires up the photo and audio controls.
        setUpAttachmentViews();

        /*
         * If this Activity had stopped previously, its state was written the ORIGINAL_CONTENT
         * location in the saved Instance state. This gets the state.
         */
        if (savedInstanceState != null) {
            mOriginalContent = savedInstanceState.getString(ORIGINAL_CONTENT);
        }
    }

    /**
     * This method is called when the Activity is about to come to the foreground. This happens
     * when the Activity comes to the top of the task stack, OR when it is first starting.
     *
     * Moves to the first note in the list, sets an appropriate title for the action chosen by
     * the user, puts the note contents into the TextView, and saves the original text as a
     * backup.
     */
    @Override
    protected void onResume() {
        super.onResume();

        /*
         * mCursor is initialized, since onCreate() always precedes onResume for any running
         * process. This tests that it's not null, since it should always contain data.
         */
        if (mCursor != null) {
            // Requery in case something changed while paused (such as the title)
            mCursor.requery();

            // The note may have been deleted from the list while this screen was paused.
            if (mCursor.getCount() == 0) {
                finish();
                return;
            }

            /* Moves to the first record. Always call moveToFirst() before accessing data in
             * a Cursor for the first time. The semantics of using a Cursor are that when it is
             * created, its internal index is pointing to a "place" immediately before the first
             * record.
             */
            mCursor.moveToFirst();

            // Modifies the window title for the Activity according to the current Activity state.
            if (mState == STATE_EDIT) {
                // Set the title of the Activity to include the note title
                int colTitleIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_TITLE);
                String title = mCursor.getString(colTitleIndex);
                Resources res = getResources();
                String text = String.format(res.getString(R.string.title_edit), title);
                setTitle(text);
            // Sets the title to "create" for inserts
            } else if (mState == STATE_INSERT) {
                setTitle(getText(R.string.title_create));
            }

            /*
             * onResume() may have been called after the Activity lost focus (was paused).
             * The user was either editing or creating a note when the Activity paused.
             * The Activity should re-display the text that had been retrieved previously, but
             * it should not move the cursor. This helps the user to continue editing or entering.
             */

            // Gets the note text from the Cursor and puts it in the TextView, but doesn't change
            // the text cursor's position.
            int colNoteIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_NOTE);
            String note = mCursor.getString(colNoteIndex);
            mText.setTextKeepState(note);

            // Stores the original note text, to allow the user to revert changes.
            if (mOriginalContent == null) {
                mOriginalContent = note;
            }

        /*
         * Something is wrong. The Cursor should always contain data. Report an error in the
         * note.
         */
        } else {
            setTitle(getText(R.string.error_title));
            mText.setText(getText(R.string.error_message));
        }

        // Displays the photos and recordings that belong to this note.
        refreshAttachments();
    }

    /**
     * This method is called when an Activity loses focus during its normal operation, and is then
     * later on killed. The Activity has a chance to save its state so that the system can restore
     * it.
     *
     * Notice that this method isn't a normal part of the Activity lifecycle. It won't be called
     * if the user simply navigates away from the Activity.
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // Save away the original text, so we still have it if the activity
        // needs to be killed while paused.
        outState.putString(ORIGINAL_CONTENT, mOriginalContent);
    }

    /**
     * This method is called when the Activity loses focus.
     *
     * For Activity objects that edit information, onPause() may be the one place where changes are
     * saved. The Android application model is predicated on the idea that "save" and "exit" aren't
     * required actions. When users navigate away from an Activity, they shouldn't have to go back
     * to it to complete their work. The act of going away should save everything and leave the
     * Activity in a state where Android can destroy it if necessary.
     *
     * If the user hasn't done anything, then this deletes or clears out the note, otherwise it
     * writes the user's work to the provider.
     */
    @Override
    protected void onPause() {
        super.onPause();

        // Stops any in-progress recording and releases the audio player.
        if (mRecorder != null) {
            stopRecording();
        }
        if (mPlayer != null) {
            mPlayer.release();
            mPlayer = null;
        }

        /*
         * Tests to see that the query operation didn't fail (see onCreate()). The Cursor object
         * will exist, even if no records were returned, unless the query failed because of some
         * exception or error.
         *
         */
        if (mCursor != null) {

            // Get the current note text.
            String text = mText.getText().toString();
            int length = text.length();

            /*
             * If the Activity is in the midst of finishing and there is no text in the current
             * note, returns a result of CANCELED to the caller, and deletes the note. This is done
             * even if the note was being edited, the assumption being that the user wanted to
             * "clear out" (delete) the note. Notes that only contain attachments are kept.
             */
            if (isFinishing() && (length == 0) && !hasExtras()) {
                setResult(RESULT_CANCELED);
                deleteNote();

                /*
                 * Writes the edits to the provider. The note has been edited if an existing note was
                 * retrieved into the editor *or* if a new note was inserted. In the latter case,
                 * onCreate() inserted a new empty note into the provider, and it is this new note
                 * that is being edited.
                 */
            } else if (mState == STATE_EDIT) {
                // Creates a map to contain the new values for the columns
                updateNote(text, null);
            } else if (mState == STATE_INSERT) {
                updateNote(text, text);
                mState = STATE_EDIT;
          }
        }
    }

    /**
     * This method is called when the user clicks the device's Menu button the first time for
     * this Activity. Android passes in a Menu object that is populated with items.
     *
     * Builds the menus for editing and inserting, and adds in alternative actions that
     * registered themselves to handle the MIME types for this application.
     *
     * @param menu A Menu object to which items should be added.
     * @return True to display the menu.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate menu from XML resource
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.editor_options_menu, menu);

        // Only add extra menu items for a saved note 
        if (mState == STATE_EDIT) {
            // Append to the
            // menu items for any other activities that can do stuff with it
            // as well.  This does a query on the system for any activities that
            // implement the ALTERNATIVE_ACTION for our data, adding a menu item
            // for each one that is found.
            Intent intent = new Intent(null, mUri);
            intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
            menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0,
                    new ComponentName(this, NoteEditor.class), null, intent, 0, null);
        }

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // Check if note has changed and enable/disable the revert option
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
     * This method is called when a menu item is selected. Android passes in the selected item.
     * The switch statement in this method calls the appropriate method to perform the action the
     * user chose.
     *
     * @param item The selected MenuItem
     * @return True to indicate that the item was processed, and no further work is necessary. False
     * to proceed to further processing as indicated in the MenuItem object.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle all of the possible menu actions.
        int id = item.getItemId();
        if(id== R.id.menu_save) {
            String text = mText.getText().toString();
            updateNote(text, null);
            finish();
        } else if (id == R.id.menu_delete) {
            deleteNote();
            finish();
        } else if (id == R.id.menu_revert) {
            cancelNote();
        }
        return super.onOptionsItemSelected(item);
    }

//BEGIN_INCLUDE(paste)
    /**
     * A helper method that replaces the note's data with the contents of the clipboard.
     */
    private final void performPaste() {

        // Gets a handle to the Clipboard Manager
        ClipboardManager clipboard = (ClipboardManager)
                getSystemService(Context.CLIPBOARD_SERVICE);

        // Gets a content resolver instance
        ContentResolver cr = getContentResolver();

        // Gets the clipboard data from the clipboard
        ClipData clip = clipboard.getPrimaryClip();
        if (clip != null) {

            String text=null;
            String title=null;

            // Gets the first item from the clipboard data
            ClipData.Item item = clip.getItemAt(0);

            // Tries to get the item's contents as a URI pointing to a note
            Uri uri = item.getUri();

            // Tests to see that the item actually is an URI, and that the URI
            // is a content URI pointing to a provider whose MIME type is the same
            // as the MIME type supported by the Note pad provider.
            if (uri != null && NotePad.Notes.CONTENT_ITEM_TYPE.equals(cr.getType(uri))) {

                // The clipboard holds a reference to data with a note MIME type. This copies it.
                Cursor orig = cr.query(
                        uri,            // URI for the content provider
                        PROJECTION,     // Get the columns referred to in the projection
                        null,           // No selection variables
                        null,           // No selection variables, so no criteria are needed
                        null            // Use the default sort order
                );

                // If the Cursor is not null, and it contains at least one record
                // (moveToFirst() returns true), then this gets the note data from it.
                if (orig != null) {
                    if (orig.moveToFirst()) {
                        int colNoteIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_NOTE);
                        int colTitleIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_TITLE);
                        text = orig.getString(colNoteIndex);
                        title = orig.getString(colTitleIndex);
                    }

                    // Closes the cursor.
                    orig.close();
                }
            }

            // If the contents of the clipboard wasn't a reference to a note, then
            // this converts whatever it is to text.
            if (text == null) {
                text = item.coerceToText(this).toString();
            }

            // Updates the current note with the retrieved title and text.
            updateNote(text, title);
        }
    }
//END_INCLUDE(paste)

    /**
     * Replaces the current note contents with the text and title provided as arguments.
     * @param text The new note contents to use.
     * @param title The new note title to use
     */
    private final void updateNote(String text, String title) {

        // Sets up a map to contain values to be updated in the provider.
        ContentValues values = new ContentValues();
        values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, System.currentTimeMillis());

        // If the action is to insert a new note, this creates an initial title for it.
        if (mState == STATE_INSERT) {

            // If no title was provided as an argument, create one from the note text.
            if (title == null) {
  
                // Get the note's length
                int length = text.length();

                // Sets the title by getting a substring of the text that is 31 characters long
                // or the number of characters in the note plus one, whichever is smaller.
                title = text.substring(0, Math.min(30, length));
  
                // If the resulting length is more than 30 characters, chops off any
                // trailing spaces
                if (length > 30) {
                    int lastSpace = title.lastIndexOf(' ');
                    if (lastSpace > 0) {
                        title = title.substring(0, lastSpace);
                    }
                }

                // Keeps a readable title when the note only contains extras.
                if (TextUtils.isEmpty(title)) {
                    title = getString(android.R.string.untitled);
                }
            }
            // In the values map, sets the value of the title
            values.put(NotePad.Notes.COLUMN_NAME_TITLE, title);
        } else if (title != null) {
            // In the values map, sets the value of the title
            values.put(NotePad.Notes.COLUMN_NAME_TITLE, title);
        }

        // This puts the desired notes text into the map.
        values.put(NotePad.Notes.COLUMN_NAME_NOTE, text);

        /*
         * Updates the provider with the new values in the map. The ListView is updated
         * automatically. The provider sets this up by setting the notification URI for
         * query Cursor objects to the incoming URI. The content resolver is thus
         * automatically notified when the Cursor for the URI changes, and the UI is
         * updated.
         * Note: This is being done on the UI thread. It will block the thread until the
         * update completes. In a sample app, going against a simple provider based on a
         * local database, the block will be momentary, but in a real app you should use
         * android.content.AsyncQueryHandler or android.os.AsyncTask.
         */
        getContentResolver().update(
                mUri,    // The URI for the record to update.
                values,  // The map of column names and new values to apply to them.
                null,    // No selection criteria are used, so no where columns are necessary.
                null     // No where columns are used, so no where arguments are necessary.
            );


    }

    /**
     * This helper method cancels the work done on a note.  It deletes the note if it was
     * newly created, or reverts to the original text of the note i
     */
    private final void cancelNote() {
        if (mCursor != null) {
            if (mState == STATE_EDIT) {
                // Put the original note text back into the database
                mCursor.close();
                mCursor = null;
                ContentValues values = new ContentValues();
                values.put(NotePad.Notes.COLUMN_NAME_NOTE, mOriginalContent);
                getContentResolver().update(mUri, values, null, null);
            } else if (mState == STATE_INSERT) {
                // We inserted an empty note, make sure to delete it
                deleteNote();
            }
        }
        setResult(RESULT_CANCELED);
        finish();
    }

    /**
     * Take care of deleting a note.  Simply deletes the entry.
     */
    private final void deleteNote() {
        if (mCursor != null) {
            mCursor.close();
            mCursor = null;
            getContentResolver().delete(mUri, null, null);
            mText.setText("");
        }
    }

    /*********************** Attachments (photos and audio) ***********************/

    /**
     * Finds the attachment views and wires up the two editor buttons.
     */
    private void setUpAttachmentViews() {
        mPhotoContainer = (LinearLayout) findViewById(R.id.photo_container);
        mAudioContainer = (LinearLayout) findViewById(R.id.audio_container);
        mPhotoSection = (TextView) findViewById(R.id.section_photos);
        mAudioSection = (TextView) findViewById(R.id.section_audio);
        mRecordButton = (Button) findViewById(R.id.btn_record_audio);

        findViewById(R.id.btn_add_photo).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickPhoto();
            }
        });

        mRecordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleRecording();
            }
        });
    }

    /** Refreshes the attachment views. */
    private void refreshExtras() {
        refreshAttachments();
    }

    /** ID of the note currently being edited. */
    private long getNoteId() {
        return ContentUris.parseId(mUri);
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private void refreshAttachments() {
        stopPlayback();
        mPhotoContainer.removeAllViews();
        mAudioContainer.removeAllViews();
        if (mUri == null) {
            return;
        }

        Cursor cursor = getContentResolver().query(
                NotePad.Attachments.CONTENT_URI,
                new String[] {
                        NotePad.Attachments._ID,
                        NotePad.Attachments.COLUMN_NAME_TYPE,
                        NotePad.Attachments.COLUMN_NAME_PATH,
                },
                NotePad.Attachments.COLUMN_NAME_NOTE_ID + "=?",
                new String[] { String.valueOf(getNoteId()) },
                NotePad.Attachments.DEFAULT_SORT_ORDER);

        if (cursor == null) {
            return;
        }

        try {
            while (cursor.moveToNext()) {
                long attachmentId = cursor.getLong(0);
                String type = cursor.getString(1);
                String path = cursor.getString(2);
                if (path == null) {
                    continue;
                }
                File file = new File(path);
                if (!file.exists()) {
                    continue;
                }
                if (NotePad.Attachments.TYPE_IMAGE.equals(type)) {
                    addPhotoView(attachmentId, file);
                } else if (NotePad.Attachments.TYPE_AUDIO.equals(type)) {
                    addAudioView(attachmentId, file);
                }
            }
        } finally {
            cursor.close();
        }

        mPhotoSection.setVisibility(
                mPhotoContainer.getChildCount() > 0 ? View.VISIBLE : View.GONE);
        mAudioSection.setVisibility(
                mAudioContainer.getChildCount() > 0 ? View.VISIBLE : View.GONE);
    }

    private void addPhotoView(final long attachmentId, final File file) {
        int size = dp(88);

        // The thumbnail and its delete badge share one square cell.
        FrameLayout cell = new FrameLayout(this);
        LinearLayout.LayoutParams cellParams = new LinearLayout.LayoutParams(size, size);
        cellParams.rightMargin = dp(10);
        cell.setLayoutParams(cellParams);

        ImageView image = new ImageView(this);
        image.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        image.setBackgroundResource(R.drawable.bg_photo_frame);
        image.setPadding(dp(4), dp(4), dp(4), dp(4));
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);

        Bitmap bitmap = decodeThumbnail(file, dp(80));
        if (bitmap != null) {
            image.setImageBitmap(bitmap);
        }

        image.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPhoto(file);
            }
        });

        image.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                confirmDeleteAttachment(attachmentId, file);
                return true;
            }
        });

        cell.addView(image);

        ImageView delete = new ImageView(this);
        FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(
                dp(24), dp(24), android.view.Gravity.TOP | android.view.Gravity.RIGHT);
        delete.setLayoutParams(badgeParams);
        delete.setImageResource(R.drawable.ic_cute_close);
        delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmDeleteAttachment(attachmentId, file);
            }
        });
        cell.addView(delete);

        mPhotoContainer.addView(cell);
    }

    /** Shows a photo enlarged in a dialog, since the file lives in app-private storage. */
    private void showPhoto(File file) {
        ImageView image = new ImageView(this);
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Bitmap bitmap = decodeThumbnail(file, dp(800));
        if (bitmap != null) {
            image.setImageBitmap(bitmap);
        }

        new AlertDialog.Builder(this)
                .setView(image)
                .setPositiveButton(R.string.button_ok, null)
                .show();
    }

    private void addAudioView(final long attachmentId, final File file) {
        // The row holds the playable name plus a visible delete button.
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(8);
        row.setLayoutParams(params);
        row.setBackgroundResource(R.drawable.bg_attachment_row);
        row.setPadding(dp(12), dp(10), dp(10), dp(10));

        TextView audio = new TextView(this);
        audio.setText(getString(R.string.audio_label, file.getName()));
        audio.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_cute_play, 0, 0, 0);
        audio.setCompoundDrawablePadding(dp(10));
        audio.setTextColor(getResources().getColor(R.color.note_text_primary));
        audio.setTextSize(15);
        audio.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        audio.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playAudio(file, audio);
            }
        });

        audio.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                confirmDeleteAttachment(attachmentId, file);
                return true;
            }
        });

        row.addView(audio);

        ImageView delete = new ImageView(this);
        delete.setLayoutParams(new LinearLayout.LayoutParams(dp(34), dp(34)));
        delete.setPadding(dp(6), dp(6), dp(6), dp(6));
        delete.setImageResource(R.drawable.ic_cute_delete);
        delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmDeleteAttachment(attachmentId, file);
            }
        });
        row.addView(delete);

        mAudioContainer.addView(row);
    }

    /** Decodes a small bitmap so photo thumbnails do not exhaust memory. */
    private Bitmap decodeThumbnail(File file, int targetSize) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);

        int sampleSize = 1;
        if (bounds.outWidth > 0) {
            while (bounds.outWidth / sampleSize > targetSize * 2) {
                sampleSize *= 2;
            }
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    private void playAudio(final File file, final TextView view) {
        stopPlayback();

        if (!file.exists()) {
            Toast.makeText(this, R.string.audio_missing, Toast.LENGTH_SHORT).show();
            return;
        }

        final MediaPlayer player = new MediaPlayer();
        try {
            player.setDataSource(file.getAbsolutePath());
            player.prepare();
            player.start();
            mPlayer = player;
        } catch (IOException e) {
            Log.e(TAG, "Failed to play " + file, e);
            player.release();
            Toast.makeText(this, R.string.attachment_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        // Shows that playback started, so tapping a recording has visible feedback.
        final CharSequence label = view.getText();
        view.setTag(label);
        view.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_cute_pause, 0, 0, 0);
        view.setText(getString(R.string.audio_playing, label));
        mPlayingAudioView = view;

        player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                resetAudioView(view);
                if (mPlayer == mp) {
                    mPlayer = null;
                }
                mp.release();
            }
        });

        player.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                Log.e(TAG, "Playback error " + what + "/" + extra + " for " + file);
                Toast.makeText(NoteEditor.this, R.string.audio_broken, Toast.LENGTH_SHORT).show();
                return false;
            }
        });
    }

    /** Stops whatever recording is playing and restores its row to the idle look. */
    private void stopPlayback() {
        if (mPlayer != null) {
            mPlayer.release();
            mPlayer = null;
        }
        if (mPlayingAudioView != null) {
            resetAudioView(mPlayingAudioView);
        }
    }

    private void resetAudioView(TextView view) {
        if (view == null) {
            return;
        }
        Object label = view.getTag();
        if (label != null) {
            view.setText((CharSequence) label);
        }
        view.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_cute_play, 0, 0, 0);
        if (mPlayingAudioView == view) {
            mPlayingAudioView = null;
        }
    }

    private void confirmDeleteAttachment(final long attachmentId, final File file) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_attachment_title)
                .setMessage(R.string.delete_attachment_message)
                .setPositiveButton(R.string.menu_delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        getContentResolver().delete(
                                ContentUris.withAppendedId(
                                        NotePad.Attachments.CONTENT_ID_URI_BASE, attachmentId),
                                null, null);
                        file.delete();
                        refreshAttachments();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void pickPhoto() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        try {
            startActivityForResult(intent, REQUEST_PICK_IMAGE);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.attachment_failed, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_PICK_IMAGE) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                copyPhotoIntoNote(data.getData());
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /** Copies the chosen picture into app storage and records it as an attachment. */
    private void copyPhotoIntoNote(Uri source) {
        InputStream input = null;
        FileOutputStream output = null;
        File target = null;

        try {
            File dir = new File(getFilesDir(), ATTACHMENT_DIR);
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IOException("Unable to create " + dir);
            }
            target = new File(dir, "img_" + System.currentTimeMillis() + ".jpg");

            input = getContentResolver().openInputStream(source);
            if (input == null) {
                throw new IOException("Unable to open " + source);
            }
            output = new FileOutputStream(target);

            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            output.flush();

            addAttachment(NotePad.Attachments.TYPE_IMAGE, target.getAbsolutePath());
            refreshExtras();
        } catch (IOException e) {
            Log.e(TAG, "Failed to add photo", e);
            if (target != null) {
                target.delete();
            }
            Toast.makeText(this, R.string.attachment_failed, Toast.LENGTH_SHORT).show();
        } finally {
            closeQuietly(input);
            closeQuietly(output);
        }
    }

    private void closeQuietly(java.io.Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                Log.w(TAG, "Failed to close stream", e);
            }
        }
    }

    private void addAttachment(String type, String path) {
        if (mUri == null) {
            return;
        }
        ContentValues values = new ContentValues();
        values.put(NotePad.Attachments.COLUMN_NAME_NOTE_ID, getNoteId());
        values.put(NotePad.Attachments.COLUMN_NAME_TYPE, type);
        values.put(NotePad.Attachments.COLUMN_NAME_PATH, path);
        getContentResolver().insert(NotePad.Attachments.CONTENT_URI, values);
    }

    /***************************** Voice recording *****************************/

    private void toggleRecording() {
        if (mRecorder != null) {
            stopRecording();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] { Manifest.permission.RECORD_AUDIO },
                    REQUEST_RECORD_PERMISSION);
            return;
        }

        startRecording();
    }

    private void startRecording() {
        File dir = new File(getFilesDir(), ATTACHMENT_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            Toast.makeText(this, R.string.recording_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        File file = new File(dir, "rec_" + System.currentTimeMillis() + ".m4a");
        MediaRecorder recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        recorder.setOutputFile(file.getAbsolutePath());

        try {
            recorder.prepare();
            recorder.start();
            mRecorder = recorder;
            mRecordingFile = file;
            mRecordButton.setText(R.string.recording_stop);
        } catch (IOException e) {
            Log.e(TAG, "Failed to start recording", e);
            recorder.release();
            file.delete();
            Toast.makeText(this, R.string.recording_failed, Toast.LENGTH_SHORT).show();
        } catch (RuntimeException e) {
            Log.e(TAG, "Failed to start recording", e);
            recorder.release();
            file.delete();
            Toast.makeText(this, R.string.recording_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRecording() {
        MediaRecorder recorder = mRecorder;
        mRecorder = null;
        boolean stopped = false;

        if (recorder != null) {
            try {
                recorder.stop();
                stopped = true;
            } catch (RuntimeException e) {
                // Thrown when the recording is too short to produce a file.
                Log.w(TAG, "Recording stopped before any audio was captured", e);
            } finally {
                recorder.release();
            }
        }

        mRecordButton.setText(R.string.action_voice);

        if (stopped && mRecordingFile != null && mRecordingFile.length() > 0) {
            addAttachment(NotePad.Attachments.TYPE_AUDIO, mRecordingFile.getAbsolutePath());
            refreshExtras();
        } else if (mRecordingFile != null) {
            mRecordingFile.delete();
        }
        mRecordingFile = null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        if (requestCode == REQUEST_RECORD_PERMISSION) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startRecording();
            } else {
                Toast.makeText(this, R.string.record_permission_denied,
                        Toast.LENGTH_SHORT).show();
            }
            return;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    /** True when the note has at least one attachment. */
    private boolean hasExtras() {
        if (mUri == null) {
            return false;
        }
        Cursor cursor = getContentResolver().query(
                NotePad.Attachments.CONTENT_URI,
                new String[] { NotePad.Attachments._ID },
                NotePad.Attachments.COLUMN_NAME_NOTE_ID + "=?",
                new String[] { String.valueOf(getNoteId()) },
                null);
        if (cursor == null) {
            return false;
        }
        try {
            return cursor.getCount() > 0;
        } finally {
            cursor.close();
        }
    }

    @Override
    protected void onDestroy() {
        if (mRecorder != null) {
            mRecorder.release();
            mRecorder = null;
        }
        if (mPlayer != null) {
            mPlayer.release();
            mPlayer = null;
        }
        super.onDestroy();
    }
}
