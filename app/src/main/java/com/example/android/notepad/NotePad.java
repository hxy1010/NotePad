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

import android.net.Uri;
import android.provider.BaseColumns;

/**
 * Defines a contract between the Note Pad content provider and its clients. A contract defines the
 * information that a client needs to access the provider as one or more data tables. A contract
 * is a public, non-extendable (final) class that contains constants defining column names and
 * URIs. A well-written client depends only on the constants in the contract.
 */
public final class NotePad {
    public static final String AUTHORITY = "com.google.provider.NotePad";

    // This class cannot be instantiated
    private NotePad() {
    }

    /**
     * Notes table contract
     */
    public static final class Notes implements BaseColumns {

        // This class cannot be instantiated
        private Notes() {}

        /**
         * The table name offered by this provider
         */
        public static final String TABLE_NAME = "notes";

        /*
         * URI definitions
         */

        /**
         * The scheme part for this provider's URI
         */
        private static final String SCHEME = "content://";

        /**
         * Path parts for the URIs
         */

        /**
         * Path part for the Notes URI
         */
        private static final String PATH_NOTES = "/notes";

        /**
         * Path part for the Note ID URI
         */
        private static final String PATH_NOTE_ID = "/notes/";

        /**
         * 0-relative position of a note ID segment in the path part of a note ID URI
         */
        public static final int NOTE_ID_PATH_POSITION = 1;

        /**
         * Path part for the Live Folder URI
         */
        private static final String PATH_LIVE_FOLDER = "/live_folders/notes";

        /**
         * The content:// style URL for this table
         */
        public static final Uri CONTENT_URI =  Uri.parse(SCHEME + AUTHORITY + PATH_NOTES);

        /**
         * The content URI base for a single note. Callers must
         * append a numeric note id to this Uri to retrieve a note
         */
        public static final Uri CONTENT_ID_URI_BASE
            = Uri.parse(SCHEME + AUTHORITY + PATH_NOTE_ID);

        /**
         * The content URI match pattern for a single note, specified by its ID. Use this to match
         * incoming URIs or to construct an Intent.
         */
        public static final Uri CONTENT_ID_URI_PATTERN
            = Uri.parse(SCHEME + AUTHORITY + PATH_NOTE_ID + "/#");

        /**
         * The content Uri pattern for a notes listing for live folders
         */
        public static final Uri LIVE_FOLDER_URI
            = Uri.parse(SCHEME + AUTHORITY + PATH_LIVE_FOLDER);

        /*
         * MIME type definitions
         */

        /**
         * The MIME type of {@link #CONTENT_URI} providing a directory of notes.
         */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.google.note";

        /**
         * The MIME type of a {@link #CONTENT_URI} sub-directory of a single
         * note.
         */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.google.note";

        /**
         * The default sort order for this table
         */
        public static final String DEFAULT_SORT_ORDER = "modified DESC";

        /*
         * Column definitions
         */

        /**
         * Column name for the title of the note
         * <P>Type: TEXT</P>
         */
        public static final String COLUMN_NAME_TITLE = "title";

        /**
         * Column name of the note content
         * <P>Type: TEXT</P>
         */
        public static final String COLUMN_NAME_NOTE = "note";

        /**
         * Column name for the creation timestamp
         * <P>Type: INTEGER (long from System.curentTimeMillis())</P>
         */
        public static final String COLUMN_NAME_CREATE_DATE = "created";

        /**
         * Column name for the modification timestamp
         * <P>Type: INTEGER (long from System.curentTimeMillis())</P>
         */
        public static final String COLUMN_NAME_MODIFICATION_DATE = "modified";

        /**
         * Column name of the notebook this note belongs to
         * <P>Type: INTEGER (foreign key to the notebooks table)</P>
         */
        public static final String COLUMN_NAME_NOTEBOOK_ID = "notebook_id";
    }

    /**
     * Notebooks table contract. A notebook groups many notes and carries a category.
     */
    public static final class Notebooks implements BaseColumns {

        private Notebooks() {}

        public static final String TABLE_NAME = "notebooks";

        private static final String SCHEME = "content://";
        private static final String PATH_NOTEBOOKS = "/notebooks";
        private static final String PATH_NOTEBOOK_ID = "/notebooks/";

        public static final Uri CONTENT_URI = Uri.parse(SCHEME + AUTHORITY + PATH_NOTEBOOKS);
        public static final Uri CONTENT_ID_URI_BASE =
                Uri.parse(SCHEME + AUTHORITY + PATH_NOTEBOOK_ID);

        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.google.notebook";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.google.notebook";

        public static final String DEFAULT_SORT_ORDER = "modified DESC";

        /** Notebook name <P>Type: TEXT</P> */
        public static final String COLUMN_NAME_NAME = "name";

        /** Notebook category, one of the values in the notebook_categories array
         *  <P>Type: TEXT</P> */
        public static final String COLUMN_NAME_CATEGORY = "category";

        /** Creation timestamp <P>Type: INTEGER</P> */
        public static final String COLUMN_NAME_CREATE_DATE = "created";

        /** Modification timestamp <P>Type: INTEGER</P> */
        public static final String COLUMN_NAME_MODIFICATION_DATE = "modified";
    }

    /**
     * Attachments table contract. Stores photos and audio recordings that belong to a note.
     */
    public static final class Attachments implements BaseColumns {

        private Attachments() {}

        public static final String TABLE_NAME = "attachments";

        private static final String SCHEME = "content://";
        private static final String PATH_ATTACHMENTS = "/attachments";
        private static final String PATH_ATTACHMENT_ID = "/attachments/";

        public static final Uri CONTENT_URI = Uri.parse(SCHEME + AUTHORITY + PATH_ATTACHMENTS);
        public static final Uri CONTENT_ID_URI_BASE =
                Uri.parse(SCHEME + AUTHORITY + PATH_ATTACHMENT_ID);

        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.google.attachment";
        public static final String CONTENT_ITEM_TYPE =
                "vnd.android.cursor.item/vnd.google.attachment";

        public static final String DEFAULT_SORT_ORDER = "created ASC";

        /** ID of the note this attachment belongs to <P>Type: INTEGER</P> */
        public static final String COLUMN_NAME_NOTE_ID = "note_id";

        /** Attachment kind: {@link #TYPE_IMAGE} or {@link #TYPE_AUDIO}
         *  <P>Type: TEXT</P> */
        public static final String COLUMN_NAME_TYPE = "type";

        /** Absolute path of the attachment file in the app's private storage
         *  <P>Type: TEXT</P> */
        public static final String COLUMN_NAME_PATH = "path";

        /** Creation timestamp <P>Type: INTEGER</P> */
        public static final String COLUMN_NAME_CREATE_DATE = "created";

        public static final String TYPE_IMAGE = "image";
        public static final String TYPE_AUDIO = "audio";
    }

    /**
     * Todos table contract. Stores simple checkable todo items that belong to a note.
     */
    public static final class Todos implements BaseColumns {

        private Todos() {}

        public static final String TABLE_NAME = "todos";

        private static final String SCHEME = "content://";
        private static final String PATH_TODOS = "/todos";
        private static final String PATH_TODO_ID = "/todos/";

        public static final Uri CONTENT_URI = Uri.parse(SCHEME + AUTHORITY + PATH_TODOS);
        public static final Uri CONTENT_ID_URI_BASE =
                Uri.parse(SCHEME + AUTHORITY + PATH_TODO_ID);

        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.google.todo";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.google.todo";

        public static final String DEFAULT_SORT_ORDER = "created ASC";

        /** ID of the note this todo belongs to <P>Type: INTEGER</P> */
        public static final String COLUMN_NAME_NOTE_ID = "note_id";

        /** Todo text <P>Type: TEXT</P> */
        public static final String COLUMN_NAME_CONTENT = "content";

        /** 1 when the todo is done, 0 otherwise <P>Type: INTEGER</P> */
        public static final String COLUMN_NAME_DONE = "done";

        /** Creation timestamp <P>Type: INTEGER</P> */
        public static final String COLUMN_NAME_CREATE_DATE = "created";
    }
}
