package com.voicetodo.app;

/** 할 일 한 건. */
public class Todo {
    public long id;
    public String text;
    /** 알림 간격 (시간 단위: 3, 6, 9, 24) */
    public int intervalHours;
    public boolean done;
    public long createdAt;
    /** 다음 알림 예정 시각 (epoch millis, 0 = 없음) */
    public long nextAt;

    public Todo(long id, String text, int intervalHours, boolean done, long createdAt, long nextAt) {
        this.id = id;
        this.text = text;
        this.intervalHours = intervalHours;
        this.done = done;
        this.createdAt = createdAt;
        this.nextAt = nextAt;
    }
}
