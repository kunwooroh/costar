package com.voicetodo.app;

/** 할 일 한 건. */
public class Todo {
    public long id;
    public String text;
    /** 알림 간격 (30분 ~ 몇 달) */
    public Interval interval;
    public boolean done;
    public long createdAt;
    /** 다음 알림 예정 시각 (epoch millis, 0 = 없음) */
    public long nextAt;
    /** true 면 반복 없이 한 번만 알림 (interval 은 '얼마 뒤' 의미) */
    public boolean once;

    public Todo(long id, String text, Interval interval, boolean once, boolean done, long createdAt, long nextAt) {
        this.id = id;
        this.text = text;
        this.interval = interval;
        this.once = once;
        this.done = done;
        this.createdAt = createdAt;
        this.nextAt = nextAt;
    }

    /** "30분마다" 또는 "한 번 알림" */
    public String scheduleLabel() {
        return once ? "한 번 알림" : interval.label();
    }
}
