package com.svenruppert.sse.client;

/**
 * Ein einfaches SSE-Event.
 */
public record SseEvent(String event, String data, String id) { }