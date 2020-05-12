package io.netty.monitor;

import io.lettuce.core.codec.RedisCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;

public class LettuceRedisCodec<K, V> implements RedisCodec<K, V> {
  private static final Logger log = LoggerFactory.getLogger(LettuceRedisCodec.class);

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
