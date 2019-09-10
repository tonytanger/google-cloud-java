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

import com.google.api.core.InternalApi;
import com.google.api.gax.grpc.ResponseMetadataHandler;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientCall.Listener;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall;
import io.grpc.ForwardingClientCallListener.SimpleForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;

/**
 * An interceptor to handle receiving the response headers.
 *
 * <p>Package-private for internal usage.
 */
@InternalApi
class GrpcMetadataHandlerInterceptor implements ClientInterceptor {

  @Override
  public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
      MethodDescriptor<ReqT, RespT> method, final CallOptions callOptions, Channel next) {
    ClientCall<ReqT, RespT> call = next.newCall(method, callOptions);

    final ResponseMetadataHandler metadataHandler =
        CallOptionsUtil.getMetadataHandlerOption(callOptions);

    if (metadataHandler == null) {
      return call;
    }
    return new SimpleForwardingClientCall<ReqT, RespT>(call) {
      @Override
      public void start(Listener<RespT> responseListener, Metadata headers) {
        Listener<RespT> forwardingResponseListener =
            new SimpleForwardingClientCallListener<RespT>(responseListener) {
              @Override
              public void onHeaders(Metadata headers) {
                super.onHeaders(headers);
                metadataHandler.onHeaders(headers);
              }

              @Override
              public void onClose(Status status, Metadata trailers) {
                super.onClose(status, trailers);
                metadataHandler.onTrailers(trailers);
              }
            };
        super.start(forwardingResponseListener, headers);
      }
    };
  }
}
