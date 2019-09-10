package com.google.cloud.bigtable.gaxx.channel;

import com.google.bigtable.v2.CheckAndMutateRowRequest;
import com.google.bigtable.v2.MutateRowRequest;
import com.google.bigtable.v2.MutateRowsRequest;
import com.google.bigtable.v2.ReadModifyWriteRowRequest;
import com.google.bigtable.v2.ReadRowsRequest;
import com.google.bigtable.v2.SampleRowKeysRequest;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.MethodDescriptor;
import java.util.Map;

class CloudBigtableTableExtractorInterceptor implements ClientInterceptor {
  private Map<String, CallOptions> tableCallOptions;

  CloudBigtableTableExtractorInterceptor(Map<String, CallOptions> tableCallOptions) {
    this.tableCallOptions = tableCallOptions;
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
        // System.out.printf("Service Name: %s\n", method.getServiceName());
        // System.out.printf("Service Name: %s\n", method.getFullMethodName());
        // System.out.println(message);

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
          // System.err.printf("Extracted table name %s\n", tableName);
          tableCallOptions.put(tableName, callOptions);
        }
        delegate().sendMessage(message);
      }
    };
  }
}