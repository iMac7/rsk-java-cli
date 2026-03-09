package com.rsk.utils;

import java.io.IOException;
import okhttp3.OkHttpClient;
import okhttp3.Request;

public final class Http {
  private Http() {}

  public static class HistoryClient {
    private final OkHttpClient client = new OkHttpClient();

    public String get(String url) {
      Request request = new Request.Builder().url(url).build();
      try (var response = client.newCall(request).execute()) {
        if (!response.isSuccessful()) {
          throw new IllegalStateException("HTTP error " + response.code());
        }
        return response.body() == null ? "" : response.body().string();
      } catch (IOException ex) {
        throw new IllegalStateException("Unable to call history endpoint", ex);
      }
    }
  }
}
