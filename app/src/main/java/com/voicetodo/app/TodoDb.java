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
    /** v2: 간격을 숫자+단위로 저장, v3: 한 번만 알림(once) */
    private static final int DB_VERSION = 3;
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
                + "interval_hours INTEGER NOT NULL DEFAULT 0, "   // v1 호환용 (사용 안 함)
                + "done INTEGER NOT NULL DEFAULT 0, "
                + "created_at INTEGER NOT NULL, "
                + "next_at INTEGER NOT NULL DEFAULT 0, "
                + "interval_amount INTEGER NOT NULL DEFAULT 6, "
                + "interval_unit TEXT NOT NULL DEFAULT 'HOUR', "
                + "once INTEGER NOT NULL DEFAULT 0)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE " + T + " ADD COLUMN interval_amount INTEGER NOT NULL DEFAULT 6");
            db.execSQL("ALTER TABLE " + T + " ADD COLUMN interval_unit TEXT NOT NULL DEFAULT 'HOUR'");
            db.execSQL("UPDATE " + T + " SET interval_amount = interval_hours, interval_unit = 'HOUR'");
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE " + T + " ADD COLUMN once INTEGER NOT NULL DEFAULT 0");
        }
    }

    public long insert(String text, Interval interval, boolean once) {
        ContentValues v = new ContentValues();
        v.put("text", text);
        v.put("interval_hours", (int) Math.max(1, interval.approxSeconds() / 3600));
        v.put("interval_amount", interval.amount);
        v.put("interval_unit", interval.unit);
        v.put("once", once ? 1 : 0);
        v.put("done", 0);
        v.put("created_at", System.currentTimeMillis());
        v.put("next_at", 0);
        return getWritableDatabase().insert(T, null, v);
    }

    /** 미완료 먼저, 그 안에서는 다음 알림이 빠른 순. */
    public List<Todo> getAll() {
        return query(null, null,
                "done ASC, CASE WHEN next_at = 0 THEN 1 ELSE 0 END, next_at ASC, created_at DESC");
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

    public void setInterval(long id, Interval interval, boolean once) {
        ContentValues v = new ContentValues();
        v.put("interval_hours", (int) Math.max(1, interval.approxSeconds() / 3600));
        v.put("interval_amount", interval.amount);
        v.put("interval_unit", interval.unit);
        v.put("once", once ? 1 : 0);
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
                new String[]{"id", "text", "interval_amount", "interval_unit", "once", "done", "created_at", "next_at"},
                where, args, null, null, orderBy)) {
            while (c.moveToNext()) {
                out.add(new Todo(
                        c.getLong(0),
                        c.getString(1),
                        Interval.of(c.getInt(2), c.getString(3)),
                        c.getInt(4) == 1,
                        c.getInt(5) == 1,
                        c.getLong(6),
                        c.getLong(7)));
            }
        }
        return out;
    }
}
