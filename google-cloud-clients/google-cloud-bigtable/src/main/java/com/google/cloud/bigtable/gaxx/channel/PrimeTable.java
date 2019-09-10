package com.google.cloud.bigtable.gaxx.channel;

import com.google.bigtable.v2.MutateRowRequest;
import com.google.bigtable.v2.MutateRowResponse;
import com.google.bigtable.v2.Mutation;
import com.google.bigtable.v2.Mutation.SetCell;
import com.google.bigtable.v2.ReadRowsRequest;
import com.google.bigtable.v2.ReadRowsResponse;
import com.google.bigtable.v2.RowSet;
import com.google.protobuf.ByteString;
import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ClientCalls;
import java.util.Iterator;
import java.util.Map;

class PrimeTable implements PrimeChannel {
  Map<String, CallOptions> tableCallOptions;
  String primeKey;
  PrimeTable(Map<String, CallOptions> tableCallOptions, String primeKey) {
    this.tableCallOptions = tableCallOptions;
    this.primeKey = primeKey;
  }

  @Override
  public void primeChannel(ManagedChannel channel) {
    for (Map.Entry<String, CallOptions> tableCallOption: tableCallOptions.entrySet()) {
      for (int i = 0; i < 1; i++) {
        long start = System.currentTimeMillis();
        String tableId = tableCallOption.getKey();
        CallOptions callOptionsFromRequest = tableCallOption.getValue();
        CallOptions callOptions = CallOptions.DEFAULT
            .withCallCredentials(callOptionsFromRequest.getCredentials())
            .withExecutor(callOptionsFromRequest.getExecutor())
            .withCompression(callOptionsFromRequest.getCompressor())
            .withAuthority(callOptionsFromRequest.getAuthority());

        if (callOptionsFromRequest.isWaitForReady()) {
          callOptions = callOptions.withWaitForReady();
        } else {
          callOptions = callOptions.withoutWaitForReady();
        }
        // System.out.println(callOptions.toString());

        MethodDescriptor<ReadRowsRequest, ReadRowsResponse>
            readRowMethodDescriptor =
            MethodDescriptor.<ReadRowsRequest, ReadRowsResponse>newBuilder()
                .setType(MethodDescriptor.MethodType.SERVER_STREAMING)
                .setFullMethodName("google.bigtable.v2.Bigtable/ReadRows")
                .setRequestMarshaller(ProtoUtils.marshaller(ReadRowsRequest.getDefaultInstance()))
                .setResponseMarshaller(
                    ProtoUtils.marshaller(ReadRowsResponse.getDefaultInstance()))
                .build();

        ReadRowsRequest readRowsRequest = ReadRowsRequest.newBuilder()
            .setTableName(tableId)
            .setRows(
                RowSet.newBuilder().addRowKeys(ByteString.copyFromUtf8(primeKey)))
            .build();

        Iterator<ReadRowsResponse> responseIterator = ClientCalls
            .blockingServerStreamingCall(channel, readRowMethodDescriptor, callOptions, readRowsRequest);

        while (responseIterator.hasNext()) {
          responseIterator.next();
        }

        MethodDescriptor<MutateRowRequest, MutateRowResponse>
            mutateRowMethodDescriptor =
            MethodDescriptor.<MutateRowRequest, MutateRowResponse>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("google.bigtable.v2.Bigtable/MutateRow")
                .setRequestMarshaller(ProtoUtils.marshaller(MutateRowRequest.getDefaultInstance()))
                .setResponseMarshaller(ProtoUtils.marshaller(MutateRowResponse.getDefaultInstance()))
                .build();

        MutateRowRequest mutateRowRequest = MutateRowRequest.newBuilder()
            .setTableName(tableId)
            .setRowKey(ByteString.copyFromUtf8(primeKey))
            .addMutations(Mutation.newBuilder().setSetCell(
                SetCell.newBuilder()
                    .setFamilyName("cloudbigtableprimekey")
                    .setColumnQualifier(ByteString.copyFromUtf8("prime"))
                    .setValue(ByteString.copyFromUtf8("primed"))
                    .build()))
            .build();

        ClientCalls
            .blockingUnaryCall(channel, mutateRowMethodDescriptor, callOptions, mutateRowRequest);

        long end = System.currentTimeMillis() - start;
        System.err.printf("Channel priming took %dms\n",end);
      }
    }
    System.err.println("Tables primed");
  }
}