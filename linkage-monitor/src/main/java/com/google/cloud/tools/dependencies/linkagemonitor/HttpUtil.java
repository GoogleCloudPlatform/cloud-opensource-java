/*
 * Copyright 2020 Google LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.tools.dependencies.linkagemonitor;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.net.UrlEscapers;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

class HttpUtil {

  private static final int DEFAULT_CONNECT_TIMEOUT_MS = 3000;
  private static final int DEFAULT_READ_TIMEOUT_MS = 3000;
  // Don't change the value; this name is used as an originating "application" of usage metrics.
  private static final String USER_AGENT = "linkage-monitor";
      
  static int sendPost(String urlString, Map<String, String> parameters) throws IOException {
    String parametersString = getParametersString(parameters);
    return sendPost(urlString, parametersString, "application/x-www-form-urlencoded");
  }

  static int sendPost(String urlString, String body, String mediaType) throws IOException {
    byte[] bytesToWrite = body.getBytes(StandardCharsets.UTF_8);

    URL url = new URL(urlString);
    HttpURLConnection connection = null;
    try {
      connection = (HttpURLConnection) url.openConnection();
      connection.setDoOutput(true);
      connection.setRequestMethod("POST");
      // This prevent Analytics from identifying our pings as spam.
      connection.setRequestProperty("User-Agent", USER_AGENT);
      connection.setRequestProperty("Content-type", mediaType);
      connection.setConnectTimeout(DEFAULT_CONNECT_TIMEOUT_MS);
      connection.setReadTimeout(DEFAULT_READ_TIMEOUT_MS);
      connection.setFixedLengthStreamingMode(bytesToWrite.length);

      try (OutputStream out = connection.getOutputStream()) {
        out.write(bytesToWrite);
        out.flush();
      }
      return connection.getResponseCode();
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }
  }

  @VisibleForTesting
  static String getParametersString(Map<String, String> parametersMap) {
    StringBuilder resultBuilder = new StringBuilder();
    boolean ampersandNeeded = false;
    for (Map.Entry<String, String> entry : parametersMap.entrySet()) {
      if (ampersandNeeded) {
        resultBuilder.append('&');
      } else {
        ampersandNeeded = true;
      }
      resultBuilder.append(entry.getKey());
      resultBuilder.append('=');
      resultBuilder.append(UrlEscapers.urlFormParameterEscaper().escape(entry.getValue()));
    }
    return resultBuilder.toString();
  }
}
