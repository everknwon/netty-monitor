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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import static io.netty.monitor.NettyMonitor.requireState;
import static java.util.Objects.requireNonNull;

public class LettuceRedis<K, V> implements RedisCodec<K, V> {
  private static final Logger log = LoggerFactory.getLogger(LettuceRedis.class);

  private final String withHost = "localhost";
  private final Integer withPort = 6379;

  private RedisURI redisUri = RedisURI.builder().withHost(withHost).withPort(withPort)
          .withTimeout(Duration.of(10, ChronoUnit.SECONDS)).build();

  private final RedisClient redisClient = RedisClient.create(redisUri);
  private StatefulRedisConnection<K, V> connection;

  private LettuceRedis() {}

  private LettuceRedis(RedisURI redisURI) {
    requireNonNull(redisURI, "Redis uri configuration is required.");
    requireState(redisURI.getPort() >= 0 && redisURI.getPort() <= 65533,
            "Port number must be available: %s", redisURI.getPort());
    Integer port = Objects.requireNonNullElse(redisURI.getPort(), withPort);
    redisURI.setPort(port);
    this.redisUri = redisURI;
  }

  public static <K, V> LettuceRedis<K, V> newBuilder() {
    return new LettuceRedis<>();
  }

  public static <K, V> LettuceRedis<K, V> newBuilder(RedisURI redisURI) {
    return new LettuceRedis<>(redisURI);
  }

  public RedisCommands<K, V> build() {
    this.connection = redisClient.connect(this);
    return connection.sync();
  }

  public RedisAsyncCommands<K, V> buildAsync() {
    this.connection = redisClient.connect(this);
    return connection.async();
  }

  public RedisReactiveCommands<K, V> buildReactive() {
    this.connection = redisClient.connect(this);
    return connection.reactive();
  }

  public void release() {
    if (!Objects.isNull(connection)) {
      this.connection.close();
      this.redisClient.shutdown();
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public K decodeKey(ByteBuffer byteBuffer) {
    return (K) decodeByteBuffer(byteBuffer);
  }


  @SuppressWarnings("unchecked")
  @Override
  public V decodeValue(ByteBuffer byteBuffer) {
    return (V) decodeByteBuffer(byteBuffer);
  }

  @Override
  public ByteBuffer encodeKey(K k) {
    return encodeByteBuffer(k);
  }

  @Override
  public ByteBuffer encodeValue(V v) {
    return encodeByteBuffer(v);
  }

  private ByteBuffer encodeByteBuffer(Object o) {
    try (ByteArrayOutputStream byteArrayOps =
                 new ByteArrayOutputStream();
         ObjectOutputStream objectOps =
                 new ObjectOutputStream(byteArrayOps)) {
      objectOps.writeObject(o);
      long freeMemory = Runtime.getRuntime().freeMemory();
      if (byteArrayOps.size() > freeMemory) {
        throw new OutOfMemoryError("ByteBuffer needs to allocate more memory than freeMemory");
      }
      ByteBuffer byteBuffer = ByteBuffer.allocateDirect(byteArrayOps.size());
      byteBuffer.put(byteArrayOps.toByteArray());
      return byteBuffer;
    } catch (Exception e) {
      log.error("encode an exception occurs:{}", o, e);
      return null;
    }
  }

  private Object decodeByteBuffer(ByteBuffer byteBuffer) {
    byte[] array = byteBuffer.array();
    try (ByteArrayInputStream byteArrayIps =
                 new ByteArrayInputStream(array);
         ObjectInputStream objectIps =
                 new ObjectInputStream(byteArrayIps)) {
      return objectIps.readObject();
    } catch (Exception e) {
      log.error("decode an exception occurs:{}", byteBuffer, e);
      return null;
    }
  }
}
