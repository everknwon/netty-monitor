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
import io.lettuce.core.api.sync.RedisCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

public class LettuceRedis {
  private static final Logger log = LoggerFactory.getLogger(LettuceRedis.class);

  private static final LettuceRedis instance = new LettuceRedis();
  private final RedisURI redisUri = RedisURI.builder()
          .withHost("localhost").withPort(6379)
          .withTimeout(Duration.of(10, ChronoUnit.SECONDS)).build();

  private final RedisClient redisClient = RedisClient.create(redisUri);
  private final StatefulRedisConnection<String, String> connection = redisClient.connect();
  private final RedisCommands<String, String> redisCommands = connection.sync();

  private LettuceRedis(){}

  public static LettuceRedis getRedisHelper() {
    return Objects.requireNonNull(instance);
  }

  private void release() {
    if (!Objects.isNull(connection)) {
      this.connection.close();
      this.redisClient.shutdown();
    }
  }
}
