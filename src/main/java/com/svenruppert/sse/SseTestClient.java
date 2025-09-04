package com.svenruppert.sse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static java.net.http.HttpClient.newHttpClient;

public class SseTestClient {
  public static void main(String[] args)
      throws Exception {
    HttpClient client = newHttpClient();
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:8080/sse"))
        .GET()
        .build();

    client.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
        .thenAccept(response -> response.body().forEach(System.out::println)).join();
  }
}