package com.svenruppert.sse.client;

import com.svenruppert.dependencies.core.logger.HasLogger;

import java.net.URI;

public final class SseClientApp implements HasLogger {
  public static void main(String[] args) {
    String url = (args.length > 0 ? args[0] : "http://localhost:8080/sse");
    URI uri = URI.create(url);

    try (SseClient client = new SseClient(uri)) {
      Runtime.getRuntime().addShutdownHook(new Thread(client::close));

      client.run(ev -> {
        // Hier könnte man nach ev.event() verzweigen – wir loggen zunächst nur
        if (ev.id() != null) {
          System.out.printf("[%s] id=%s data=%s%n", ev.event(), ev.id(), ev.data());
        } else {
          System.out.printf("[%s] data=%s%n", ev.event(), ev.data());
        }
      });
    }
  }
}