package com.svenruppert.sse.client;

import com.svenruppert.dependencies.core.logger.HasLogger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Minimaler, instanzbasierter SSE-Client auf Basis von java.net.http.HttpClient.
 * - verwaltet Last-Event-ID
 * - implementiert Reconnect-Schleife
 * - parst text/event-stream
 */
public final class SseClient
    implements HasLogger, AutoCloseable {
  private final HttpClient http;
  private final URI uri;
  private final Duration reconnectDelay = Duration.ofSeconds(2);
  private volatile boolean running = true;
  private volatile String lastEventId = null;

  public SseClient(URI uri) {
    this.uri = uri;
    this.http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();
  }

  /**
   * Startet die Streaming-Schleife und ruft bei jedem vollständigen Event den Callback auf.
   */
  public void run(Consumer<SseEvent> onEvent) {
    while (running) {
      try {
        HttpRequest.Builder b = HttpRequest.newBuilder()
            .uri(uri)
            .timeout(Duration.ofMinutes(10))
            .GET();
        if (lastEventId != null) {
          b.setHeader("Last-Event-ID", lastEventId);
        }
        HttpRequest req = b.build();

        // synchrones Streaming, damit wir Zeile für Zeile parsen können
        HttpResponse<Stream<String>> resp =
            http.send(req, HttpResponse.BodyHandlers.ofLines());

        if (resp.statusCode() != 200) {
          logger().warn("Unerwarteter Status {} von {}", resp.statusCode(), uri);
          sleep(reconnectDelay);
          continue;
        }

        try {
          parseStream(resp.body(), onEvent);
        } catch (java.io.UncheckedIOException uioe) {
          // Häufiger Fall beim Server-Shutdown: Stream wird geschlossen → sauber reconnecten
          var cause = uioe.getCause();
          logger().info("Stream geschlossen ({}). Reconnect folgt …",
                        cause != null
                            ? cause.getClass().getSimpleName()
                            : uioe.getClass().getSimpleName());
        } catch (RuntimeException re) {
          logger().warn("Unerwartete Laufzeit-Exception im Parser: {}", re.getMessage());
        }

      } catch (IOException | InterruptedException e) {
        if (!running) break; // regulär beendet
        logger().warn("Verbindung unterbrochen ({}). Reconnect in {}s …",
                      e.getClass().getSimpleName(), reconnectDelay.toSeconds());
        sleep(reconnectDelay);
      }
    }
  }

  private void parseStream(Stream<String> lines, Consumer<SseEvent> onEvent) {
    String event = null, id = null;
    StringBuilder data = null;

    try {
      for (String line : (Iterable<String>) lines::iterator) {
        if (line.isEmpty()) {
          // Nachricht abschließen
          if (data != null) {
            String payload = data.toString();
            SseEvent ev = new SseEvent(event, payload, id);
            if (id != null) lastEventId = id; // Fortschritt merken
            // Shutdown-Signal vom Server → Client beendet sich kontrolliert
            if ("shutdown".equalsIgnoreCase(ev.event())) {
              try {
                onEvent.accept(ev);
              } catch (RuntimeException ex) {
                logger().warn("Event-Callback warf Exception: {}", ex.getMessage());
              }
              this.running = false;
              break; // Parsing-Schleife verlassen
            }
            try {
              onEvent.accept(ev);
            } catch (RuntimeException ex) {
              logger().warn("Event-Callback warf Exception: {}", ex.getMessage());
            }
          }
          event = null;
          id = null;
          data = null;
          continue;
        }

        if (line.startsWith(":")) {
          // Kommentar/Heartbeat – still ignorieren (kein Log-Spam)
          continue;
        }

        int idx = line.indexOf(':');
        String field = (idx >= 0 ? line.substring(0, idx) : line).trim();
        String value = (idx >= 0 ? line.substring(idx + 1) : "");
        if (value.startsWith(" ")) value = value.substring(1); // optionales Space entfernen

        switch (field) {
          case "event" -> event = value;
          case "id" -> id = value;
          case "data" -> {
            if (data == null) data = new StringBuilder();
            if (!data.isEmpty()) data.append(' ');
            data.append(value);
          }
          default -> { /* unbekanntes Feld ignorieren */ }
        }
      }
    } catch (java.io.UncheckedIOException uioe) {
      // Typischerweise EOF/Closed bei Server-Stop → keine Exception nach außen werfen
      var cause = uioe.getCause();
      logger().info("Inputstream beendet ({}).",
                    cause != null
                        ? cause.getClass().getSimpleName()
                        : uioe.getClass().getSimpleName());
    }
  }

  private void sleep(Duration d) {
    try {
      Thread.sleep(d.toMillis());
    } catch (InterruptedException ignored) {
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public void close() { running = false; }
}