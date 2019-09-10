/*
 * Copyright 2017 Google LLC
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *     * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer
 * in the documentation and/or other materials provided with the
 * distribution.
 *     * Neither the name of Google LLC nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.google.cloud.bigtable.gaxx.channel;

import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A {@link ManagedChannel} that will send requests round robin via a set of channels.
 *
 * <p>Package-private for internal use.
 */
class ChannelPool extends ManagedChannel {
  private final List<ManagedChannel> channels;
  private final AtomicInteger indexTicker = new AtomicInteger();
  private final String authority;
  private final CreateChannel createChannel;
  private final PrimeChannel primeChannel;
  private final Map<String, CallOptions> tableCallOptions = new HashMap<>();
  // refresh every 25 minutes
  private final long CHANNEL_REFRESH_PERIOD = 25 * 60;
  // spread out the refresh between channels to every minute
  private final long CHANNEL_REFRESH_DELAY = 60;

  /**
   * Initializes the channel pool. Assumes that all channels have the same authority.
   *
   */
  ChannelPool(int poolSize, CreateChannel createChannel, ScheduledExecutorService executorService) throws IOException {
    this.channels = new ArrayList<>();
    this.createChannel = createChannel;
    this.primeChannel = new PrimeTable(tableCallOptions, "cloud_bigtable/primetable/special_key");
    for (int i = 0; i < poolSize; i++) {
      ManagedChannel managedChannel = createChannel.createChannel();
      primeChannel.primeChannel(managedChannel);
      channels.add(managedChannel);
      executorService.scheduleWithFixedDelay(new RefreshSingleChannel(i), CHANNEL_REFRESH_PERIOD - i * CHANNEL_REFRESH_DELAY, CHANNEL_REFRESH_PERIOD, TimeUnit.SECONDS);
    }
    authority = channels.get(0).authority();
  }

  /** {@inheritDoc} */
  @Override
  public String authority() {
    return authority;
  }

  /**
   * Create a {@link ClientCall} on a Channel from the pool chosen in a round-robin fashion to the
   * remote operation specified by the given {@link MethodDescriptor}. The returned {@link
   * ClientCall} does not trigger any remote behavior until {@link
   * ClientCall#start(ClientCall.Listener, io.grpc.Metadata)} is invoked.
   */
  @Override
  public <ReqT, RespT> ClientCall<ReqT, RespT> newCall(
      MethodDescriptor<ReqT, RespT> methodDescriptor, CallOptions callOptions) {

    int index = getNextChannelIndex(indexTicker.getAndIncrement());
    ManagedChannel channel = channels.get(index);
    return new CloudBigtableTableExtractorInterceptor(tableCallOptions).interceptCall(methodDescriptor, callOptions, channel);
  }

  /** {@inheritDoc} */
  @Override
  public ManagedChannel shutdown() {
    for (ManagedChannel channelWrapper : channels) {
      channelWrapper.shutdown();
    }

    return this;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isShutdown() {
    for (ManagedChannel channel : channels) {
      if (!channel.isShutdown()) {
        return false;
      }
    }
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isTerminated() {
    for (ManagedChannel channel : channels) {
      if (!channel.isTerminated()) {
        return false;
      }
    }
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public ManagedChannel shutdownNow() {
    for (ManagedChannel channel : channels) {
      channel.shutdownNow();
    }
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    long endTimeNanos = System.nanoTime() + unit.toNanos(timeout);
    for (ManagedChannel channel : channels) {
      long awaitTimeNanos = endTimeNanos - System.nanoTime();
      if (awaitTimeNanos <= 0) {
        break;
      }
      channel.awaitTermination(awaitTimeNanos, TimeUnit.NANOSECONDS);
    }

    return isTerminated();
  }

  /**
   * Performs a simple round robin on the list of {@link ManagedChannel}s in the {@code channels}
   * list.
   *
   * @return An int denoting the index of the {@link ManagedChannel} that can be used for a single RPC call.
   */
  private int getNextChannelIndex(int affinity) {
    int index = affinity % channels.size();
    index = Math.abs(index);
    // If index is the most negative int, abs(index) is still negative.
    if (index < 0) {
      index = 0;
    }
    return index;
  }

  private class RefreshSingleChannel implements Runnable {
    private int index;

    RefreshSingleChannel(int index) {
      this.index = index;
    }
    @Override
    public void run() {
      try {
        // System.err.printf("Resetting Channel %d\n", index);
        ManagedChannel oldChannel = channels.get(index);
        ManagedChannel newChannel = createChannel.createChannel();
        primeChannel.primeChannel(newChannel);
        // System.err.printf("New channel %d created\n", index);
        channels.set(index, newChannel);
        // System.err.printf("New channel %d set\n", index);
        oldChannel.shutdown();
        if (!oldChannel.awaitTermination(1, TimeUnit.MINUTES)) {
          oldChannel.shutdownNow();
        }
        // System.err.printf("Swapped Channel %d\n", index);
      } catch (IOException | InterruptedException e) {
        e.printStackTrace();
      }
    }
  }
}
