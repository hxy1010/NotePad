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
 * 定义 NotePad 内容提供者（ContentProvider）与其客户端之间的契约。契约以一张或多张
 * 数据表的形式，描述了客户端访问该提供者所需的信息。契约是一个公开且不可继承
 * （final）的类，其中包含定义列名和 URI 的常量。编写良好的客户端只依赖契约
 * 中定义的常量。
 */
public final class NotePad {
    public static final String AUTHORITY = "com.google.provider.NotePad";

    // 此类不能被实例化
    private NotePad() {
    }

    /**
     * 笔记表契约
     */
    public static final class Notes implements BaseColumns {

        // 此类不能被实例化
        private Notes() {}

        /**
         * 本提供者对外提供的表名
         */
        public static final String TABLE_NAME = "notes";

        /*
         * URI 定义
         */

        /**
         * 本提供者 URI 的 scheme 部分
         */
        private static final String SCHEME = "content://";

        /**
         * URI 的路径部分
         */

        /**
         * 笔记集合 URI 的路径部分
         */
        private static final String PATH_NOTES = "/notes";

        /**
         * 单条笔记 ID URI 的路径部分
         */
        private static final String PATH_NOTE_ID = "/notes/";

        /**
         * 本表的 content:// 风格 URL
         */
        public static final Uri CONTENT_URI =  Uri.parse(SCHEME + AUTHORITY + PATH_NOTES);

        /**
         * 单条笔记的内容 URI 基址。调用方必须在此 Uri
         * 后追加数字形式的笔记 ID 才能获取该笔记
         */
        public static final Uri CONTENT_ID_URI_BASE
            = Uri.parse(SCHEME + AUTHORITY + PATH_NOTE_ID);

        /*
         * MIME 类型定义
         */

        /**
         * {@link #CONTENT_URI} 的 MIME 类型，表示笔记目录。
         */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.google.note";

        /**
         * {@link #CONTENT_URI} 下单条笔记子目录的 MIME
         * 类型。
         */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.google.note";

        /**
         * 本表的默认排序方式
         */
        public static final String DEFAULT_SORT_ORDER = "modified DESC";

        /*
         * 列定义
         */

        /**
         * 笔记标题的列名
         * <P>类型：TEXT</P>
         */
        public static final String COLUMN_NAME_TITLE = "title";

        /**
         * 笔记内容的列名
         * <P>类型：TEXT</P>
         */
        public static final String COLUMN_NAME_NOTE = "note";

        /**
         * 创建时间戳的列名
         * <P>类型：INTEGER（由 System.curentTimeMillis() 得到的 long 值）</P>
         */
        public static final String COLUMN_NAME_CREATE_DATE = "created";

        /**
         * 修改时间戳的列名
         * <P>类型：INTEGER（由 System.curentTimeMillis() 得到的 long 值）</P>
         */
        public static final String COLUMN_NAME_MODIFICATION_DATE = "modified";

        /**
         * 笔记所属笔记本的列名
         * <P>类型：INTEGER（指向 notebooks 表的外键）</P>
         */
        public static final String COLUMN_NAME_NOTEBOOK_ID = "notebook_id";
    }

    /**
     * 笔记本表契约。一个笔记本聚合多条笔记，并带有一个分类。
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

        /** 笔记本名称 <P>类型：TEXT</P> */
        public static final String COLUMN_NAME_NAME = "name";

        /** 笔记本分类，取值为 notebook_categories 数组中的某一项
         *  <P>类型：TEXT</P> */
        public static final String COLUMN_NAME_CATEGORY = "category";

        /** 创建时间戳 <P>类型：INTEGER</P> */
        public static final String COLUMN_NAME_CREATE_DATE = "created";

        /** 修改时间戳 <P>类型：INTEGER</P> */
        public static final String COLUMN_NAME_MODIFICATION_DATE = "modified";
    }

    /**
     * 待办表契约。用于存储属于某条笔记的简单可勾选待办事项。
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

        public static final String DEFAULT_SORT_ORDER =
                "deadline IS NULL, deadline ASC, created DESC";

        /** 此待办所属笔记的 ID <P>类型：INTEGER</P> */
        public static final String COLUMN_NAME_NOTE_ID = "note_id";

        /** 待办内容 <P>类型：TEXT</P> */
        public static final String COLUMN_NAME_CONTENT = "content";

        /** 待办已完成时为 1，否则为 0 <P>类型：INTEGER</P> */
        public static final String COLUMN_NAME_DONE = "done";

        /** 创建时间戳 <P>类型：INTEGER</P> */
        public static final String COLUMN_NAME_CREATE_DATE = "created";

        /** 截止日期时间戳；未设置时为 NULL <P>类型：INTEGER</P> */
        public static final String COLUMN_NAME_DEADLINE = "deadline";
    }
}
