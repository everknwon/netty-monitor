/*
 * MIT License
 *
 * Copyright (c) 2020 1619kHz
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.netty.monitor;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.reactive.RedisReactiveCommands;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.codec.RedisCodec;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import static io.netty.monitor.NettyMonitor.requireState;
import static java.util.Objects.requireNonNull;

public class LettuceRedis<K, V> {
  private String withHost = "localhost";
  private Integer withPort = 6379;
  private Duration withTimeout = Duration.of(10, ChronoUnit.SECONDS);
  private RedisURI redisUri = RedisURI.builder().withHost(withHost).withPort(withPort)
          .withTimeout(withTimeout).build();

  private final RedisClient redisClient = RedisClient.create(redisUri);
  private StatefulRedisConnection<? extends K, ? extends V> connection;

  private LettuceRedis() {}

  public static LettuceRedis<Object, Object> newBuilder() {
    return new LettuceRedis<>();
  }

  public LettuceRedis<K, V> redisURI(RedisURI redisURI) {
    requireNonNull(redisURI, "The Redis uri configuration of redis is required.");
    requireState(redisURI.getPort() >= 0 && redisURI.getPort() <= 65533,
            "Port number must be available: %s", redisURI.getPort());
    Integer port = Objects.requireNonNullElse(redisURI.getPort(), withPort);
    redisURI.setPort(port);
    this.redisUri = redisURI;
    return this;
  }

  public LettuceRedis<K, V> withPort(int withPort) {
    requireState(withPort >= 0 && withPort <= 65533,
            "Port number must be available: %s", withPort);
    this.withPort = withPort;
    return this;
  }

  public LettuceRedis<K, V> withHost(String withHost) {
    requireNonNull(withHost, "The connection address of redis is required.");
    if (withHost.contains(":")) {
      String[] split = withHost.split(":");
      if (split[0].contains(".")) this.withHost = split[0];
    }
    this.withHost = withHost;
    return this;
  }

  public LettuceRedis<K, V> withTimeout(Duration duration) {
    requireNonNull(duration, "Tne with timeout of redis is required. ");
    this.withTimeout = duration;
    return this;
  }

  public <K1 extends K, V1 extends V> RedisCommands<K1, V1> build() {
    StatefulRedisConnection<K1, V1> connection = getGenericConnection();
    return connection.sync();
  }

  public <K1 extends K, V1 extends V> RedisAsyncCommands<K1, V1> buildAsync() {
    StatefulRedisConnection<K1, V1> connection = getGenericConnection();
    return connection.async();
  }

  public <K1 extends K, V1 extends V> RedisReactiveCommands<K1, V1> buildReactive() {
    StatefulRedisConnection<K1, V1> connection = getGenericConnection();
    return connection.reactive();
  }

  private <K1 extends K, V1 extends V> StatefulRedisConnection<K1, V1> getGenericConnection() {
    RedisCodec<K1, V1> redisCodec = new LettuceRedisCodec<>();
    StatefulRedisConnection<K1, V1> connect = redisClient.connect(redisCodec);
    this.connection = connect;
    return connect;
  }

  public void release() {
    if (!Objects.isNull(connection)) {
      this.connection.close();
      this.redisClient.shutdown();
    }
  }
}
