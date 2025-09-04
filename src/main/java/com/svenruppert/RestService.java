package com.svenruppert;

import io.javalin.Javalin;
import io.javalin.http.Context;


public class RestService {

  public static final int DEFAULT_PORT = 7070;
  private static final String UPPER_CASE_PATH = "/upper/{value}/{name}";
  private final Javalin service;
  private final int port;
  private final UpperCaseService upperCaseService = new UpperCaseService();

  public RestService() {
    this.port = DEFAULT_PORT;
    service = initService();
  }

  public RestService(int port) {
    this.port = port;
    service = initService();
  }

  public Javalin getService() {
    return service;
  }

  public Javalin startService() {
    return service.start(port);
  }

  public Javalin startService(int port) {
    return service.start(port);
  }

  private Javalin initService() {
    return Javalin.create(/*config*/)
        .get("/", ctx -> ctx.result("Hello World"))
        .get(UPPER_CASE_PATH, this::handleUpperCaseRequest);
  }

  private void handleUpperCaseRequest(Context ctx) {
    String value = ctx.pathParam("value");
    String name = ctx.pathParam("name");
    String result = upperCaseService.toUpperCase(value + "-" + name);
    ctx.result(result);
  }
}