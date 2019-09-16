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

import com.google.bigtable.v2.MutateRowRequest;
import com.google.bigtable.v2.MutateRowResponse;
import com.google.bigtable.v2.Mutation;
import com.google.bigtable.v2.Mutation.SetCell;
import com.google.bigtable.v2.ReadRowsRequest;
import com.google.bigtable.v2.ReadRowsResponse;
import com.google.bigtable.v2.RowSet;
import com.google.protobuf.ByteString;
import io.grpc.CallCredentials;
import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ClientCalls;
import java.util.Iterator;
import java.util.Map;

class PrimeChannel {
  private Map<String, CallCredentials> tableCredentials;
  private String primeKey;
  private String primeKeyFamilyName;

  PrimeChannel(Map<String, CallCredentials> tableCredentials, String primeKey, String primeKeyFamilyName) {
    this.tableCredentials = tableCredentials;
    this.primeKey = primeKey;
    this.primeKeyFamilyName = primeKeyFamilyName;
  }

  void primeChannel(ManagedChannel channel) {
    for (Map.Entry<String, CallCredentials> tableCredential: tableCredentials.entrySet()) {
      System.out.printf("Priming table %s\n", tableCredential.getKey());
      long start = System.currentTimeMillis();
      String tableId = tableCredential.getKey();
      // CallCredentials credentials = tableCallOption.getValue();
      CallOptions callOptions = CallOptions.DEFAULT
          .withCallCredentials(tableCredential.getValue());
      // .withExecutor(callOptionsFromRequest.getExecutor())
      // .withCompression(callOptionsFromRequest.getCompressor())
      // .withAuthority(callOptionsFromRequest.getAuthority());

      // if (callOptionsFromRequest.isWaitForReady()) {
      //   callOptions = callOptions.withWaitForReady();
      // } else {
      //   callOptions = callOptions.withoutWaitForReady();
      // }
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

      // MethodDescriptor<CheckAndMutateRowRequest, CheckAndMutateRowResponse>
      //     checkAndMutateRowMethodDescriptor =
      //     MethodDescriptor.<CheckAndMutateRowRequest, CheckAndMutateRowResponse>newBuilder()
      //         .setType(MethodDescriptor.MethodType.UNARY)
      //         .setFullMethodName("google.bigtable.v2.Bigtable/CheckAndMutateRow")
      //         .setRequestMarshaller(
      //             ProtoUtils.marshaller(CheckAndMutateRowRequest.getDefaultInstance()))
      //         .setResponseMarshaller(
      //             ProtoUtils.marshaller(CheckAndMutateRowResponse.getDefaultInstance()))
      //         .build();
      //
      // CheckAndMutateRowRequest checkAndMutateRowRequest = CheckAndMutateRowRequest.newBuilder()
      //     .setTableName(tableId)
      //     .setRowKey(ByteString.copyFromUtf8("primetablekey"))
      //     .addTrueMutations(Mutation.newBuilder().setSetCell(
      //         SetCell.newBuilder()
      //             .setFamilyName("data1")
      //             .setColumnQualifier(ByteString.copyFromUtf8("1"))
      //             .setValue(ByteString.copyFromUtf8("true"))
      //             .build()))
      //     .addFalseMutations(Mutation.newBuilder().setSetCell(
      //         SetCell.newBuilder()
      //             .setFamilyName("data1")
      //             .setColumnQualifier(ByteString.copyFromUtf8("1"))
      //             .setValue(ByteString.copyFromUtf8("false"))
      //             .build()))
      //     .build();
      //
      // ClientCalls
      //     .blockingUnaryCall(channel, checkAndMutateRowMethodDescriptor, callOptions, checkAndMutateRowRequest);

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
                  .setFamilyName(primeKeyFamilyName)
                  .setColumnQualifier(ByteString.copyFromUtf8(primeKey))
                  .setValue(ByteString.copyFromUtf8(primeKey))
                  .build()))
          .build();

      ClientCalls
          .blockingUnaryCall(channel, mutateRowMethodDescriptor, callOptions, mutateRowRequest);

      long end = System.currentTimeMillis() - start;
      System.err.printf("Channel priming took %dms\n",end);
    }
    System.err.println("Tables primed");
  }
}