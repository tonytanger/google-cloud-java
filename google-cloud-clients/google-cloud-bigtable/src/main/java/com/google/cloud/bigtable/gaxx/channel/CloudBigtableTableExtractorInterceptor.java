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

import com.google.bigtable.v2.CheckAndMutateRowRequest;
import com.google.bigtable.v2.MutateRowRequest;
import com.google.bigtable.v2.MutateRowsRequest;
import com.google.bigtable.v2.ReadModifyWriteRowRequest;
import com.google.bigtable.v2.ReadRowsRequest;
import com.google.bigtable.v2.SampleRowKeysRequest;
import io.grpc.CallCredentials;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.MethodDescriptor;
import java.util.Map;

class CloudBigtableTableExtractorInterceptor implements ClientInterceptor {
  private Map<String, CallCredentials> tableCredentials;

  CloudBigtableTableExtractorInterceptor(Map<String, CallCredentials> tableCredentials) {
    this.tableCredentials = tableCredentials;
  }

  @Override
  public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
      final MethodDescriptor<ReqT, RespT> method, final CallOptions callOptions, Channel next) {
    final ClientCall<ReqT, RespT> call = next.newCall(method, callOptions);

    return new ForwardingClientCall<ReqT, RespT>() {
      @Override
      protected ClientCall<ReqT, RespT> delegate() {
        return call;
      }

      @Override
      public void sendMessage(ReqT message) {
        // extract table name and credentials for cloud bigtable requests
        String tableName = "";
        if (method.getServiceName() != null && method.getServiceName().equals("google.bigtable.v2.Bigtable")) {
          if (message instanceof ReadRowsRequest) {
            tableName = ((ReadRowsRequest) message).getTableName();
          } else if (message instanceof MutateRowRequest) {
            tableName = ((MutateRowRequest) message).getTableName();
          } else if (message instanceof MutateRowsRequest) {
            tableName = ((MutateRowsRequest) message).getTableName();
          } else if (message instanceof SampleRowKeysRequest) {
            tableName = ((SampleRowKeysRequest) message).getTableName();
          } else if (message instanceof CheckAndMutateRowRequest) {
            tableName = ((CheckAndMutateRowRequest) message).getTableName();
          } else if (message instanceof ReadModifyWriteRowRequest) {
            tableName = ((ReadModifyWriteRowRequest) message).getTableName();
          }
        }
        if (!tableName.isEmpty()) {
          tableCredentials.put(tableName, callOptions.getCredentials());
        }
        delegate().sendMessage(message);
      }
    };
  }
}