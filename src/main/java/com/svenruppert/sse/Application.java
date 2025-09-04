package com.svenruppert.sse;

public class Application {
  public static void main(String[] args)
      throws Exception {
    var sseServer = new SseServer();
    sseServer.start();
  }
}