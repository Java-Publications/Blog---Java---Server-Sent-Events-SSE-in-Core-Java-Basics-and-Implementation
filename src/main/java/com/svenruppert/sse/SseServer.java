package com.svenruppert.sse;

import com.sun.net.httpserver.HttpServer;
import com.svenruppert.dependencies.core.logger.HasLogger;

import java.io.*;
import java.net.InetSocketAddress;

import static java.nio.charset.StandardCharsets.UTF_8;

public class SseServer
    implements HasLogger {

  protected static final String PATH_SSE = "/sse";
  private volatile boolean sendMessages = false;
  private volatile boolean shutdownMessage = false;

  // Referenz, damit wir den Server aus der CLI sauber stoppen können
  private HttpServer server;

  public void start()
      throws Exception {
    int port = 8080;
    this.server = HttpServer.create(new InetSocketAddress(port), 0);

    // Hintergrund-Thread für CLI-Steuerung (start | stop | shutdown)
    Thread cli = new Thread(() -> {
      try (BufferedReader console = new BufferedReader(new InputStreamReader(System.in))) {
        String line;
        while ((line = console.readLine()) != null) {
          String cmd = line.trim().toLowerCase();
          switch (cmd) {
            case "start" -> cmdStart();
            case "stop" -> cmdStop();
            case "shutdown" -> cmdShutdown();
            default -> logger().info("Unbekanntes Kommando: {} (erlaubt: start | stop | shutdown)", cmd);
          }
        }
      } catch (IOException ioe) {
        logger().warn("CLI-Steuerung beendet: {}", ioe.getMessage());
      }
    }, "cli-control");
    cli.setDaemon(true);
    cli.start();

    server.createContext(PATH_SSE, exchange -> {
      if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
        exchange.sendResponseHeaders(405, -1);
        return;
      }
      exchange.getResponseHeaders().add("Content-Type", "text/event-stream; charset=utf-8");
      exchange.getResponseHeaders().add("Cache-Control", "no-cache");
      exchange.getResponseHeaders().add("Connection", "keep-alive");
      exchange.sendResponseHeaders(200, 0);

      try (OutputStream os = exchange.getResponseBody();
           OutputStreamWriter osw = new OutputStreamWriter(os, UTF_8);
           BufferedWriter bw = new BufferedWriter(osw);
           PrintWriter out = new PrintWriter(bw, true)) {

        long id = 0;
        while (!shutdownMessage) {
          if (sendMessages) {
            id++;
            String data = "tick @" + System.currentTimeMillis();
            writeEvent(out, "tick", data, Long.toString(id));
          } else {
            heartbeat(out);
          }
          Thread.sleep(1000);
        }
        // Optional: Abschiedsereignis vor dem Beenden senden
        writeEvent(out, "shutdown", "Server fährt herunter", Long.toString(++id));

      } catch (IOException | InterruptedException ioe) {
        logger().warn("SSE-Client getrennt: {}", ioe.getMessage());
      } finally {
        logger().info("SSE-Stream beendet");
      }
    });

    server.start();
    logger().info("SSE-Server läuft auf http://localhost:{}/sse", port);
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      logger().info("Beende Server …");
      if (server != null) {
        server.stop(0);
      }
    }));
  }

  private void cmdShutdown() {
//    logger().info("Shutdown-Kommando empfangen – Server fährt herunter …");
//    shutdownMessage = true;  // beendet laufende Streams
//    try {
//      if (server != null) {
//        server.stop(0);      // HTTP-Server sofort stoppen
//      }
//    } catch (Exception e) {
//      logger().warn("Fehler beim Stoppen des Servers: {}", e.getMessage());
//    }
//    // Kurze Pause, damit Handler-Schleifen sauber aussteigen können
//    try {
//      Thread.sleep(200);
//    } catch (InterruptedException ignored) {
//    }
//    logger().info("Applikation wird beendet.");
//    System.exit(0);
    logger().info("Shutdown-Kommando empfangen – 'shutdown'-Event wird gesendet …");
    shutdownMessage = true; // signalisiert allen aktiven Handlern, ein Shutdown-Event zu senden
    try {
      // Gnadenfrist, damit Handler noch writeEvent(..., "shutdown", ...) + flush ausführen können
      Thread.sleep(1200);
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
    }
    try {
      if (server != null) {
        server.stop(0); // danach HTTP-Server stoppen
      }
    } catch (Exception e) {
      logger().warn("Fehler beim Stoppen: {}", e.getMessage());
    }
    logger().info("Applikation wird beendet.");
    System.exit(0);
  }

  private void cmdStop() {
    sendMessages = false;
    logger().info("SSE-Nachrichtenversand gestoppt durch CLI");
  }

  private void cmdStart() {
    sendMessages = true;
    logger().info("SSE-Nachrichtenversand gestartet durch CLI");
  }

  // Hilfsfunktion zum Senden eines Events im SSE-Format
  private void writeEvent(PrintWriter out, String event, String data, String id) {
    if (event != null && !event.isEmpty()) {
      out.printf("event: %s%n", event);
    }
    if (id != null && !id.isEmpty()) {
      out.printf("id: %s%n", id);
    }
    if (data != null) {
      // Mehrzeilige Daten korrekt ausgeben
      for (String line : data.split("\\R", -1)) {
        out.printf("data: %s%n", line);
      }
    }
    out.print("\n"); // Nachricht mit Leerzeile terminieren
    out.flush();
  }

  // Kommentarbasierter Heartbeat, vom Client ignoriert – hält Verbindungen aktiv
  private void heartbeat(PrintWriter out) {
    out.print(": keep-alive\n\n");
    out.flush();
  }
}