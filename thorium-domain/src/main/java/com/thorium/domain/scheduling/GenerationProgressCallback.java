package com.thorium.domain.scheduling;

public interface GenerationProgressCallback {
    void log(String level, String message);
    void progress(int placed, int total);
    void itemRejected(String summary, String reason);
    void tierChange(String tier);
    void complete(boolean success, int placed, int total, double quality);
    default boolean isCancelled() { return false; }
}
