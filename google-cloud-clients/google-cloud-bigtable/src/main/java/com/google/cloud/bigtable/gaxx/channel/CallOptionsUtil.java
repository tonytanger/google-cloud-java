/*
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.cloud.bigtable.gaxx.channel;

import com.google.api.gax.grpc.ResponseMetadataHandler;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import io.grpc.CallOptions;
import io.grpc.Metadata;
import io.grpc.Metadata.Key;
import java.util.Collections;
import java.util.Map;

/** A utility class that provides helper functions to work with custom call options. */
class CallOptionsUtil {
  // this is a call option name, not a header name, it is not transferred over the wire
  private static final CallOptions.Key<Map<Key<String>, String>> DYNAMIC_HEADERS_CALL_OPTION_KEY =
      CallOptions.Key.createWithDefault(
          "gax_dynamic_headers", Collections.<Key<String>, String>emptyMap());
  // this is the header name, it is transferred over the wire
  static Metadata.Key<String> REQUEST_PARAMS_HEADER_KEY =
      Metadata.Key.of("x-goog-request-params", Metadata.ASCII_STRING_MARSHALLER);
  private static final CallOptions.Key<ResponseMetadataHandler> METADATA_HANDLER_CALL_OPTION_KEY =
      CallOptions.Key.createWithDefault("gax_metadata_handler", null);

  private CallOptionsUtil() {}

  static CallOptions putRequestParamsDynamicHeaderOption(
      CallOptions callOptions, String requestParams) {
    if (callOptions == null || requestParams.isEmpty()) {
      return callOptions;
    }

    Map<Key<String>, String> dynamicHeadersOption =
        callOptions.getOption(DYNAMIC_HEADERS_CALL_OPTION_KEY);

    // This will fail, if REQUEST_PARAMS_HEADER_KEY is already there
    dynamicHeadersOption =
        ImmutableMap.<Key<String>, String>builder()
            .putAll(dynamicHeadersOption)
            .put(REQUEST_PARAMS_HEADER_KEY, requestParams)
            .build();

    return callOptions.withOption(DYNAMIC_HEADERS_CALL_OPTION_KEY, dynamicHeadersOption);
  }

  static Map<Key<String>, String> getDynamicHeadersOption(CallOptions callOptions) {
    return callOptions.getOption(DYNAMIC_HEADERS_CALL_OPTION_KEY);
  }

  static CallOptions putMetadataHandlerOption(
      CallOptions callOptions, ResponseMetadataHandler handler) {
    Preconditions.checkNotNull(callOptions);
    Preconditions.checkNotNull(handler);
    return callOptions.withOption(METADATA_HANDLER_CALL_OPTION_KEY, handler);
  }

  public static ResponseMetadataHandler getMetadataHandlerOption(CallOptions callOptions) {
    return callOptions.getOption(METADATA_HANDLER_CALL_OPTION_KEY);
  }
}
