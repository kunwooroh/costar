package com.voicetodo.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** 스마트폰 내부(SQLite)에 할 일을 저장하는 로컬 데이터베이스. */
public class TodoDb extends SQLiteOpenHelper {

    private static final String DB_NAME = "voice_todo.db";
    private static final int DB_VERSION = 1;
    private static final String T = "todos";

    private static TodoDb instance;

    public static synchronized TodoDb get(Context context) {
        if (instance == null) {
            instance = new TodoDb(context.getApplicationContext());
        }
        return instance;
    }

    private TodoDb(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + T + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "text TEXT NOT NULL, "
                + "interval_hours INTEGER NOT NULL, "
                + "done INTEGER NOT NULL DEFAULT 0, "
                + "created_at INTEGER NOT NULL, "
                + "next_at INTEGER NOT NULL DEFAULT 0)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 버전 1 뿐이라 아직 마이그레이션 없음
    }

    public long insert(String text, int intervalHours) {
        ContentValues v = new ContentValues();
        v.put("text", text);
        v.put("interval_hours", intervalHours);
        v.put("done", 0);
        v.put("created_at", System.currentTimeMillis());
        v.put("next_at", 0);
        return getWritableDatabase().insert(T, null, v);
    }

    /** 미완료 먼저, 그 안에서는 최근 추가 순. */
    public List<Todo> getAll() {
        return query(null, null, "done ASC, created_at DESC");
    }

    public List<Todo> getActive() {
        return query("done = 0", null, "created_at DESC");
    }

    public Todo getById(long id) {
        List<Todo> list = query("id = ?", new String[]{String.valueOf(id)}, null);
        return list.isEmpty() ? null : list.get(0);
    }

    public void setDone(long id, boolean done) {
        ContentValues v = new ContentValues();
        v.put("done", done ? 1 : 0);
        if (done) v.put("next_at", 0);
        update(id, v);
    }

    public void setInterval(long id, int intervalHours) {
        ContentValues v = new ContentValues();
        v.put("interval_hours", intervalHours);
        update(id, v);
    }

    public void setNextAt(long id, long nextAt) {
        ContentValues v = new ContentValues();
        v.put("next_at", nextAt);
        update(id, v);
    }

    public void delete(long id) {
        getWritableDatabase().delete(T, "id = ?", new String[]{String.valueOf(id)});
    }

    private void update(long id, ContentValues v) {
        getWritableDatabase().update(T, v, "id = ?", new String[]{String.valueOf(id)});
    }

    private List<Todo> query(String where, String[] args, String orderBy) {
        List<Todo> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query(T,
                new String[]{"id", "text", "interval_hours", "done", "created_at", "next_at"},
                where, args, null, null, orderBy)) {
            while (c.moveToNext()) {
                out.add(new Todo(
                        c.getLong(0),
                        c.getString(1),
                        c.getInt(2),
                        c.getInt(3) == 1,
                        c.getLong(4),
                        c.getLong(5)));
            }
        }
        return out;
    }
}
