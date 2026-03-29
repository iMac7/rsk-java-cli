package com.rsk.utils;

import java.io.IOException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Http {
  private static final Logger LOGGER = LoggerFactory.getLogger(Http.class);

  private Http() {}

  public static class HistoryClient {
    private final OkHttpClient client = new OkHttpClient();

    public String get(String url) {
      Request request = new Request.Builder().url(url).build();
      try (var response = client.newCall(request).execute()) {
        if (!response.isSuccessful()) {
          LOGGER.warn("History endpoint request failed for {} with status {}", url, response.code());
          throw new IllegalStateException("HTTP error " + response.code());
        }
        return response.body() == null ? "" : response.body().string();
      } catch (IOException ex) {
        LOGGER.error("Unable to call history endpoint {}", url, ex);
        throw new IllegalStateException("Unable to call history endpoint", ex);
      }
    }
  }
}
