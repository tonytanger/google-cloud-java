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

import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import java.util.List;
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

  /**
   * Initializes the channel pool. Assumes that all channels have the same authority.
   *
   * @param channels a List of channels to pool.
   */
  ChannelPool(List<ManagedChannel> channels) {
    this.channels = channels;
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

    return getNextChannel().newCall(methodDescriptor, callOptions);
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
   * @return A {@link ManagedChannel} that can be used for a single RPC call.
   */
  private ManagedChannel getNextChannel() {
    return getChannel(indexTicker.getAndIncrement());
  }

  /**
   * Returns one of the channels managed by this pool. The pool continues to "own" the channel, and
   * the caller should not shut it down.
   *
   * @param affinity Two calls to this method with the same affinity returns the same channel. The
   *     reverse is not true: Two calls with different affinities might return the same channel.
   *     However, the implementation should attempt to spread load evenly.
   */
  ManagedChannel getChannel(int affinity) {
    int index = affinity % channels.size();
    index = Math.abs(index);
    // If index is the most negative int, abs(index) is still negative.
    if (index < 0) {
      index = 0;
    }
    return channels.get(index);
  }
}
