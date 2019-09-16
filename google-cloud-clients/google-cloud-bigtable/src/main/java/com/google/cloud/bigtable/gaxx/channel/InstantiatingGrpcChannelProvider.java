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

import com.google.api.core.ApiFunction;
import com.google.api.core.BetaApi;
import com.google.api.core.InternalExtensionOnly;
import com.google.api.gax.core.ExecutorProvider;
import com.google.api.gax.core.FixedExecutorProvider;
import com.google.api.gax.grpc.GrpcHeaderInterceptor;
import com.google.api.gax.grpc.GrpcInterceptorProvider;
import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.rpc.FixedHeaderProvider;
import com.google.api.gax.rpc.HeaderProvider;
import com.google.api.gax.rpc.TransportChannel;
import com.google.api.gax.rpc.TransportChannelProvider;
import com.google.auth.Credentials;
import com.google.auth.oauth2.ComputeEngineCredentials;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import io.grpc.CallCredentials;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.alts.ComputeEngineChannelBuilder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import org.threeten.bp.Duration;

/**
 * InstantiatingGrpcChannelProvider is a TransportChannelProvider which constructs a gRPC
 * ManagedChannel with a number of configured inputs every time getChannel(...) is called. These
 * inputs include a port, a service address, and credentials.
 *
 * <p>The credentials can either be supplied directly (by providing a FixedCredentialsProvider to
 * Builder.setCredentialsProvider()) or acquired implicitly from Application Default Credentials (by
 * providing a GoogleCredentialsProvider to Builder.setCredentialsProvider()).
 *
 * <p>The client lib header and generator header values are used to form a value that goes into the
 * http header of requests to the service.
 */
@InternalExtensionOnly
public final class InstantiatingGrpcChannelProvider implements TransportChannelProvider {
  static final String DIRECT_PATH_ENV_VAR = "GOOGLE_CLOUD_ENABLE_DIRECT_PATH";
  static final long DIRECT_PATH_KEEP_ALIVE_TIME_SECONDS = 3600;
  static final long DIRECT_PATH_KEEP_ALIVE_TIMEOUT_SECONDS = 20;
  // refresh every 55 minutes
  static final long CHANNEL_REFRESH_PERIOD = 55;
  // spread out the refresh between channels to every minute
  static final long CHANNEL_REFRESH_DELAY = 1;

  private final int processorCount;
  private final ExecutorProvider executorProvider;
  private final HeaderProvider headerProvider;
  private final String endpoint;
  private final EnvironmentProvider envProvider;
  @Nullable private final GrpcInterceptorProvider interceptorProvider;
  @Nullable private final Integer maxInboundMessageSize;
  @Nullable private final Integer maxInboundMetadataSize;
  @Nullable private final Duration keepAliveTime;
  @Nullable private final Duration keepAliveTimeout;
  @Nullable private final Boolean keepAliveWithoutCalls;
  @Nullable private final Integer poolSize;
  @Nullable private final Credentials credentials;
  @Nullable private final PrimeChannel primeChannel;
  @Nullable private final Map<String, CallCredentials> tableCredentials;

  @Nullable
  private final ApiFunction<ManagedChannelBuilder, ManagedChannelBuilder> channelConfigurator;

  private InstantiatingGrpcChannelProvider(Builder builder) {
    this.processorCount = builder.processorCount;
    this.executorProvider = builder.executorProvider;
    this.headerProvider = builder.headerProvider;
    this.endpoint = builder.endpoint;
    this.envProvider = builder.envProvider;
    this.interceptorProvider = builder.interceptorProvider;
    this.maxInboundMessageSize = builder.maxInboundMessageSize;
    this.maxInboundMetadataSize = builder.maxInboundMetadataSize;
    this.keepAliveTime = builder.keepAliveTime;
    this.keepAliveTimeout = builder.keepAliveTimeout;
    this.keepAliveWithoutCalls = builder.keepAliveWithoutCalls;
    this.poolSize = builder.poolSize;
    this.channelConfigurator = builder.channelConfigurator;
    this.credentials = builder.credentials;
    this.primeChannel = builder.primeChannel;
    this.tableCredentials = builder.tableCredentials;
  }

  @Override
  public boolean needsExecutor() {
    return executorProvider == null;
  }

  @Override
  public TransportChannelProvider withExecutor(ScheduledExecutorService executor) {
    return toBuilder().setExecutorProvider(FixedExecutorProvider.create(executor)).build();
  }

  @Override
  @BetaApi("The surface for customizing headers is not stable yet and may change in the future.")
  public boolean needsHeaders() {
    return headerProvider == null;
  }

  @Override
  @BetaApi("The surface for customizing headers is not stable yet and may change in the future.")
  public TransportChannelProvider withHeaders(Map<String, String> headers) {
    return toBuilder().setHeaderProvider(FixedHeaderProvider.create(headers)).build();
  }

  @Override
  public String getTransportName() {
    return GrpcTransportChannel.getGrpcTransportName();
  }

  @Override
  public boolean needsEndpoint() {
    return endpoint == null;
  }

  @Override
  public TransportChannelProvider withEndpoint(String endpoint) {
    validateEndpoint(endpoint);
    return toBuilder().setEndpoint(endpoint).build();
  }

  @Override
  @BetaApi("The surface for customizing pool size is not stable yet and may change in the future.")
  public boolean acceptsPoolSize() {
    return poolSize == null;
  }

  @Override
  @BetaApi("The surface for customizing pool size is not stable yet and may change in the future.")
  public TransportChannelProvider withPoolSize(int size) {
    Preconditions.checkState(acceptsPoolSize(), "pool size already set to %s", poolSize);
    return toBuilder().setPoolSize(size).build();
  }

  @Override
  public boolean needsCredentials() {
    return credentials == null;
  }

  @Override
  public TransportChannelProvider withCredentials(Credentials credentials) {
    return toBuilder().setCredentials(credentials).build();
  }

  @Override
  public TransportChannel getTransportChannel() throws IOException {
    if (needsExecutor()) {
      throw new IllegalStateException("getTransportChannel() called when needsExecutor() is true");
    } else if (needsHeaders()) {
      throw new IllegalStateException("getTransportChannel() called when needsHeaders() is true");
    } else if (needsEndpoint()) {
      throw new IllegalStateException("getTransportChannel() called when needsEndpoint() is true");
    } else {
      return createChannel();
    }
  }

  private class RefreshSingleChannel implements Runnable {
    private int index;
    private List<ManagedChannel> channels;

    RefreshSingleChannel(List<ManagedChannel> channels, int index) {
      this.channels = channels;
      this.index = index;
    }

    @Override
    public void run() {
      try {
        ManagedChannel oldChannel = channels.get(index);
        ManagedChannel newChannel = createSingleChannel();
        channels.set(index, newChannel);
        if (oldChannel == null) {
          return;
        }
        oldChannel.shutdown();
        if (!oldChannel.awaitTermination(1, TimeUnit.MINUTES)) {
          oldChannel.shutdownNow();
        }
      } catch (IOException | InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  private TransportChannel createChannel() throws IOException {
    List<ManagedChannel> channels = new ArrayList<>();
    int channelListSize;
    if (poolSize == null) {
      channelListSize = 1;
    } else {
      channelListSize = poolSize;
    }
    for (int i = 0; i < channelListSize; i++) {
      channels.add(createSingleChannel());
      executorProvider.getExecutor()
          .scheduleWithFixedDelay(new RefreshSingleChannel(channels, i),
              CHANNEL_REFRESH_PERIOD - i * CHANNEL_REFRESH_DELAY,
              CHANNEL_REFRESH_PERIOD, TimeUnit.SECONDS);
    }
    return GrpcTransportChannel.create(new ChannelPool(channels));
  }

  // The environment variable is used during the rollout phase for directpath.
  // This checker function will be removed once directpath is stable.
  private boolean isDirectPathEnabled(String serviceAddress) {
    String whiteList = envProvider.getenv(DIRECT_PATH_ENV_VAR);
    if (whiteList == null) return false;
    for (String service : whiteList.split(",")) {
      if (!service.isEmpty() && serviceAddress.contains(service)) return true;
    }
    return false;
  }

  private ManagedChannel createSingleChannel() throws IOException {
    ScheduledExecutorService executor = executorProvider.getExecutor();
    GrpcHeaderInterceptor headerInterceptor =
        new GrpcHeaderInterceptor(headerProvider.getHeaders());
    GrpcMetadataHandlerInterceptor metadataHandlerInterceptor =
        new GrpcMetadataHandlerInterceptor();

    int colon = endpoint.indexOf(':');
    if (colon < 0) {
      throw new IllegalStateException("invalid endpoint - should have been validated: " + endpoint);
    }
    int port = Integer.parseInt(endpoint.substring(colon + 1));
    String serviceAddress = endpoint.substring(0, colon);

    ManagedChannelBuilder builder;

    // TODO(weiranf): Add a new API in ComputeEngineCredentials to check whether it's using default
    // service account.
    if (isDirectPathEnabled(serviceAddress) && credentials instanceof ComputeEngineCredentials) {
      builder = ComputeEngineChannelBuilder.forAddress(serviceAddress, port);
      // Set default keepAliveTime and keepAliveTimeout when directpath environment is enabled.
      // Will be overridden by user defined values if any.
      builder.keepAliveTime(DIRECT_PATH_KEEP_ALIVE_TIME_SECONDS, TimeUnit.SECONDS);
      builder.keepAliveTimeout(DIRECT_PATH_KEEP_ALIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } else {
      builder = ManagedChannelBuilder.forAddress(serviceAddress, port);
    }
    builder =
        builder
            // See https://github.com/googleapis/gapic-generator/issues/2816
            .disableServiceConfigLookUp()
            .intercept(new CloudBigtableTableExtractorInterceptor(tableCredentials))
            .intercept(new GrpcChannelUUIDInterceptor())
            .intercept(headerInterceptor)
            .intercept(metadataHandlerInterceptor)
            .userAgent(headerInterceptor.getUserAgentHeader())
            .executor(executor);

    if (maxInboundMetadataSize != null) {
      builder.maxInboundMetadataSize(maxInboundMetadataSize);
    }
    if (maxInboundMessageSize != null) {
      builder.maxInboundMessageSize(maxInboundMessageSize);
    }
    if (keepAliveTime != null) {
      builder.keepAliveTime(keepAliveTime.toMillis(), TimeUnit.MILLISECONDS);
    }
    if (keepAliveTimeout != null) {
      builder.keepAliveTimeout(keepAliveTimeout.toMillis(), TimeUnit.MILLISECONDS);
    }
    if (keepAliveWithoutCalls != null) {
      builder.keepAliveWithoutCalls(keepAliveWithoutCalls);
    }
    if (interceptorProvider != null) {
      builder.intercept(interceptorProvider.getInterceptors());
    }
    if (channelConfigurator != null) {
      builder = channelConfigurator.apply(builder);
    }
    ManagedChannel managedChannel = builder.build();
    if (primeChannel != null) {
      primeChannel.primeChannel(managedChannel);
    }
    return managedChannel;
  }

  /** The endpoint to be used for the channel. */
  public String getEndpoint() {
    return endpoint;
  }

  /** The time without read activity before sending a keepalive ping. */
  public Duration getKeepAliveTime() {
    return keepAliveTime;
  }

  /** The time without read activity after sending a keepalive ping. */
  public Duration getKeepAliveTimeout() {
    return keepAliveTimeout;
  }

  /** Whether keepalive will be performed when there are no outstanding RPCs. */
  public Boolean getKeepAliveWithoutCalls() {
    return keepAliveWithoutCalls;
  }

  /** The maximum metadata size allowed to be received on the channel. */
  @BetaApi("The surface for maximum metadata size is not stable yet and may change in the future.")
  public Integer getMaxInboundMetadataSize() {
    return maxInboundMetadataSize;
  }

  @Override
  public boolean shouldAutoClose() {
    return true;
  }

  public Builder toBuilder() {
    return new Builder(this);
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public static final class Builder {
    private int processorCount;
    private ExecutorProvider executorProvider;
    private HeaderProvider headerProvider;
    private String endpoint;
    private EnvironmentProvider envProvider;
    @Nullable private GrpcInterceptorProvider interceptorProvider;
    @Nullable private Integer maxInboundMessageSize;
    @Nullable private Integer maxInboundMetadataSize;
    @Nullable private Duration keepAliveTime;
    @Nullable private Duration keepAliveTimeout;
    @Nullable private Boolean keepAliveWithoutCalls;
    @Nullable private Integer poolSize;
    @Nullable private ApiFunction<ManagedChannelBuilder, ManagedChannelBuilder> channelConfigurator;
    @Nullable private Credentials credentials;
    @Nullable private PrimeChannel primeChannel;
    @Nullable private Map<String, CallCredentials> tableCredentials;

    private Builder() {
      processorCount = Runtime.getRuntime().availableProcessors();
      envProvider = DirectPathEnvironmentProvider.getInstance();
    }

    private Builder(InstantiatingGrpcChannelProvider provider) {
      this.processorCount = provider.processorCount;
      this.executorProvider = provider.executorProvider;
      this.headerProvider = provider.headerProvider;
      this.endpoint = provider.endpoint;
      this.envProvider = provider.envProvider;
      this.interceptorProvider = provider.interceptorProvider;
      this.maxInboundMessageSize = provider.maxInboundMessageSize;
      this.maxInboundMetadataSize = provider.maxInboundMetadataSize;
      this.keepAliveTime = provider.keepAliveTime;
      this.keepAliveTimeout = provider.keepAliveTimeout;
      this.keepAliveWithoutCalls = provider.keepAliveWithoutCalls;
      this.poolSize = provider.poolSize;
      this.channelConfigurator = provider.channelConfigurator;
      this.credentials = provider.credentials;
      this.primeChannel = provider.primeChannel;
      this.tableCredentials = provider.tableCredentials;
    }

    /** Sets the number of available CPUs, used internally for testing. */
    Builder setProcessorCount(int processorCount) {
      this.processorCount = processorCount;
      return this;
    }

    /** Sets the environment variable provider used for testing. */
    Builder setEnvironmentProvider(EnvironmentProvider envProvider) {
      this.envProvider = envProvider;
      return this;
    }

    /**
     * Sets the ExecutorProvider for this TransportChannelProvider.
     *
     * <p>This is optional; if it is not provided, needsExecutor() will return true, meaning that an
     * Executor must be provided when getChannel is called on the constructed
     * TransportChannelProvider instance. Note: GrpcTransportProvider will automatically provide its
     * own Executor in this circumstance when it calls getChannel.
     */
    public Builder setExecutorProvider(ExecutorProvider executorProvider) {
      this.executorProvider = executorProvider;
      return this;
    }

    /**
     * Sets the HeaderProvider for this TransportChannelProvider.
     *
     * <p>This is optional; if it is not provided, needsHeaders() will return true, meaning that
     * headers must be provided when getChannel is called on the constructed
     * TransportChannelProvider instance.
     */
    public Builder setHeaderProvider(HeaderProvider headerProvider) {
      this.headerProvider = headerProvider;
      return this;
    }

    /** Sets the endpoint used to reach the service, eg "localhost:8080". */
    public Builder setEndpoint(String endpoint) {
      validateEndpoint(endpoint);
      this.endpoint = endpoint;
      return this;
    }

    /**
     * Sets the GrpcInterceptorProvider for this TransportChannelProvider.
     *
     * <p>The provider will be called once for each underlying gRPC ManagedChannel that is created.
     * It is recommended to return a new list of new interceptors on each call so that interceptors
     * are not shared among channels, but this is not required.
     */
    public Builder setInterceptorProvider(GrpcInterceptorProvider interceptorProvider) {
      this.interceptorProvider = interceptorProvider;
      return this;
    }

    public String getEndpoint() {
      return endpoint;
    }

    /** The maximum message size allowed to be received on the channel. */
    public Builder setMaxInboundMessageSize(Integer max) {
      this.maxInboundMessageSize = max;
      return this;
    }

    /** The maximum message size allowed to be received on the channel. */
    public Integer getMaxInboundMessageSize() {
      return maxInboundMessageSize;
    }

    /** The maximum metadata size allowed to be received on the channel. */
    @BetaApi(
        "The surface for maximum metadata size is not stable yet and may change in the future.")
    public Builder setMaxInboundMetadataSize(Integer max) {
      this.maxInboundMetadataSize = max;
      return this;
    }

    /** The maximum metadata size allowed to be received on the channel. */
    @BetaApi(
        "The surface for maximum metadata size is not stable yet and may change in the future.")
    public Integer getMaxInboundMetadataSize() {
      return maxInboundMetadataSize;
    }

    /** The time without read activity before sending a keepalive ping. */
    public Builder setKeepAliveTime(Duration duration) {
      this.keepAliveTime = duration;
      return this;
    }

    /** The time without read activity before sending a keepalive ping. */
    public Duration getKeepAliveTime() {
      return keepAliveTime;
    }

    /** The time without read activity after sending a keepalive ping. */
    public Builder setKeepAliveTimeout(Duration duration) {
      this.keepAliveTimeout = duration;
      return this;
    }

    /** The time without read activity after sending a keepalive ping. */
    public Duration getKeepAliveTimeout() {
      return keepAliveTimeout;
    }

    /** Whether keepalive will be performed when there are no outstanding RPCs. */
    public Builder setKeepAliveWithoutCalls(Boolean keepalive) {
      this.keepAliveWithoutCalls = keepalive;
      return this;
    }

    /** Whether keepalive will be performed when there are no outstanding RPCs. */
    public Boolean getKeepAliveWithoutCalls() {
      return keepAliveWithoutCalls;
    }

    /**
     * Number of underlying grpc channels to open. Calls will be load balanced round robin across
     * them.
     */
    public int getPoolSize() {
      if (poolSize == null) {
        return 1;
      }
      return poolSize;
    }

    /**
     * Number of underlying grpc channels to open. Calls will be load balanced round robin across
     * them
     */
    public Builder setPoolSize(int poolSize) {
      Preconditions.checkArgument(poolSize > 0, "Pool size must be positive");
      this.poolSize = poolSize;
      return this;
    }

    /** Sets the number of channels relative to the available CPUs. */
    public Builder setChannelsPerCpu(double multiplier) {
      return setChannelsPerCpu(multiplier, 100);
    }

    public Builder setChannelsPerCpu(double multiplier, int maxChannels) {
      Preconditions.checkArgument(multiplier > 0, "multiplier must be positive");
      Preconditions.checkArgument(maxChannels > 0, "maxChannels must be positive");

      int channelCount = (int) Math.ceil(processorCount * multiplier);
      if (channelCount > maxChannels) {
        channelCount = maxChannels;
      }
      return setPoolSize(channelCount);
    }

    public Builder setCredentials(Credentials credentials) {
      this.credentials = credentials;
      return this;
    }

    public Builder setPrimeKey(String primeKey, String primeKeyFamilyName) {
      this.tableCredentials = new HashMap<>();
      this.primeChannel = new PrimeChannel(this.tableCredentials, primeKey, primeKeyFamilyName);
      return this;
    }

    public InstantiatingGrpcChannelProvider build() {
      return new InstantiatingGrpcChannelProvider(this);
    }

    /**
     * Add a callback that can intercept channel creation.
     *
     * <p>This can be used for advanced configuration like setting the netty event loop. The
     * callback will be invoked with a fully configured channel builder, which the callback can
     * augment or replace.
     */
    @BetaApi("Surface for advanced channel configuration is not yet stable")
    public Builder setChannelConfigurator(
        @Nullable ApiFunction<ManagedChannelBuilder, ManagedChannelBuilder> channelConfigurator) {
      this.channelConfigurator = channelConfigurator;
      return this;
    }

    @Nullable
    public ApiFunction<ManagedChannelBuilder, ManagedChannelBuilder> getChannelConfigurator() {
      return channelConfigurator;
    }
  }

  private static void validateEndpoint(String endpoint) {
    int colon = endpoint.indexOf(':');
    if (colon < 0) {
      throw new IllegalArgumentException(
          String.format("invalid endpoint, expecting \"<host>:<port>\""));
    }
    Integer.parseInt(endpoint.substring(colon + 1));
  }

  /**
   * EnvironmentProvider currently provides DirectPath environment variable, and is only used during
   * initial rollout for DirectPath. This interface will be removed once the DirectPath environment
   * is not used.
   */
  interface EnvironmentProvider {
    @Nullable
    String getenv(String env);
  }

  static class DirectPathEnvironmentProvider implements EnvironmentProvider {
    private static DirectPathEnvironmentProvider provider;

    private DirectPathEnvironmentProvider() {}

    public static DirectPathEnvironmentProvider getInstance() {
      if (provider == null) {
        provider = new DirectPathEnvironmentProvider();
      }
      return provider;
    }

    @Override
    public String getenv(String env) {
      return System.getenv(env);
    }
  }
}
