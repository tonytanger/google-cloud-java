package com.google.cloud.bigtable.gaxx.channel;

import io.grpc.ManagedChannel;

public interface PrimeChannel {
  void primeChannel(ManagedChannel channel);
}
