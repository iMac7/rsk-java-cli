package com.evmcli.infrastructure.http;

import static org.assertj.core.api.Assertions.assertThat;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

class HistoryClientTest {
  @Test
  void returnsResponseBody() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(new MockResponse().setBody("{\"ok\":true}"));
      server.start();

      HistoryClient client = new HistoryClient();
      String body = client.get(server.url("/history").toString());

      assertThat(body).contains("\"ok\":true");
    }
  }
}
