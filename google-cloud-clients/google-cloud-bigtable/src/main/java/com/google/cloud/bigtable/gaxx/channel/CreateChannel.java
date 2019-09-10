package com.google.cloud.bigtable.gaxx.channel;

import io.grpc.ManagedChannel;
import java.io.IOException;

public interface CreateChannel {
  ManagedChannel createChannel() throws IOException;
}
