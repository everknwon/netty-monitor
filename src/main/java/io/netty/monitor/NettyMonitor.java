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

import io.netty.monitor.utils.PropertyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringReader;
import java.net.BindException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static io.netty.monitor.Const.*;
import static java.util.Objects.requireNonNull;

public final class NettyMonitor {
  private static final Logger log = LoggerFactory.getLogger(NettyMonitor.class);

  /** Determine whether the iot service is started */
  private volatile boolean started = false;

  /**
   * Use netty to build a tcp server, env is used to collect configuration information,
   * and single Executor is used to execute a single thread
   */
  private final Environment environment = new Environment();
  private final Server nettyServer = new NettyMonitorServer();
  private final CountDownLatch countDownLatch = new CountDownLatch(1);
  private final Executor singleExecutor = this.getSingleExecutor();

  /** Information you may need */
  private Class<?> bootCls;
  private String bootName;
  private String[] args;

  public NettyMonitor openSsl(boolean openSsl) {
    this.environment.add(PATH_SERVER_SSL, openSsl);
    return this;
  }

  public NettyMonitor needClientAuth(boolean needClientAuth) {
    this.environment.add(PATH_NEED_CLIENT_AUTH, needClientAuth);
    return this;
  }

  public NettyMonitor keystorePath(String keystorePath) {
    requireNonNull(keystorePath, "keystore path cannot be null");
    this.environment.add(PATH_SERVER_SSL_PRIVATE_KEY, keystorePath);
    return this;
  }

  public NettyMonitor caCertificatePath(String caCertificatePath) {
    requireNonNull(caCertificatePath, "Certificate path cannot be null");
    this.environment.add(PATH_SERVER_SSL_CERT, caCertificatePath);
    return this;
  }

  public NettyMonitor certificatePassword(char[] certificatePassword) {
    requireNonNull(certificatePassword, "Certificate password must be set");
    this.environment.add(PATH_SERVER_SSL_PRIVATE_KEY_PASS, certificatePassword);
    return this;
  }

  private NettyMonitor() {
  }

  /** Ensures that the argument expression is true. */
  static void requireArgument(boolean expression) {
    if (!expression) {
      throw new IllegalArgumentException();
    }
  }

  /** Ensures that the state expression is true */
  static void requireState(boolean expression) {
    if (!expression) {
      throw new IllegalStateException();
    }
  }

  /** Ensures that the state expression is true. */
  static void requireState(boolean expression, String template, Object... args) {
    if (!expression) {
      throw new IllegalStateException(String.format(template, args));
    }
  }

  public static NettyMonitor of() {
    return new NettyMonitor();
  }

  public NettyMonitor bind(int port) {
    requireState(!(port <= 0 || port > 65533), "The port number must be correctly available");
    this.environment.add(PATH_SERVER_PORT, port);
    return this;
  }

  public Class<?> bootCls() {
    return bootCls;
  }

  public String[] args() {
    return args;
  }

  public String bootName() {
    return bootName;
  }

  public Environment environment() {
    return this.environment;
  }

  private Executor getSingleExecutor() {
    MonitorThreadFactory monitorThreadFactory = new MonitorThreadFactory(SERVER_THREAD_NAME);
    return Executors.newCachedThreadPool(monitorThreadFactory);
  }

  public <T> void start(Class<T> bootCls, String[] args) {
    try {
      this.bootName = bootCls.getName();
      this.args = args;
      this.loadConfig();
    } catch (IllegalAccessException e) {
      log.error("An exception occurred while loading the configuration", e);
    }
    final String threadName = this.environment.get(PATH_APP_THREAD_NAME, SERVER_THREAD_NAME);
    final Thread bootThread = new Thread(openTcpConnection(bootCls), threadName);
    this.singleExecutor.execute(bootThread);
    this.started = true;
  }

  private <T> Runnable openTcpConnection(Class<T> bootCls) {
    return () -> {
      try {
        this.bootCls = bootCls;
        this.nettyServer.start(this);
        this.countDownLatch.countDown();
        this.nettyServer.join();
      } catch (BindException e) {
        log.error("Bind port is exception:", e);
      } catch (Exception e) {
        log.error("An exception occurred while the service started", e);
      }
    };
  }

  private void loadConfig() throws IllegalAccessException {
    String bootConf = this.environment.get(PATH_SERVER_BOOT_CONFIG, PATH_CONFIG_PROPERTIES);

    final Environment bootConfEnv = Environment.of(bootConf);
    final Map<String, String> constFieldMap = PropertyUtils.confFieldMap();

    this.loadPropsOrYaml(bootConfEnv, constFieldMap);
    //Support loading configuration from args array of main function
    if (!requireNonNull(bootConfEnv).isEmpty()) {
      Map<String, String> bootEnvMap = bootConfEnv.toStringMap();
      Set<Map.Entry<String, String>> entrySet = bootEnvMap.entrySet();
      entrySet.forEach(entry -> this.environment.add(entry.getKey(), entry.getValue()));
    }
  }

  private void loadPropsOrYaml(Environment bootConfEnv, Map<String, String> constField) {
    //Properties are configured by default, and the properties loaded by default are application.properties
    constField.keySet().forEach(key ->
            Optional.ofNullable(System.getProperty(constField.get(key)))
                    .ifPresent(property -> bootConfEnv.add(key, property)));
    //If there is no properties configuration, the yaml format is used, and the default yaml loaded is application.yml
    if (bootConfEnv.isEmpty()) {
      Optional.ofNullable(PropertyUtils.yaml(PATH_CONFIG_YAML))
              .ifPresent(yamlConfigTreeMap ->
                      bootConfEnv.load(new StringReader(
                              PropertyUtils.toProperties(yamlConfigTreeMap))));
    }
  }

  public NettyMonitor await() {
    if (!this.started) {
      throw new IllegalStateException("Server hasn't been started. Call start() before calling this method.");
    }
    try {
      this.countDownLatch.await();
    } catch (Exception e) {
      log.error("Server start await error", e);
      Thread.currentThread().interrupt();
    }
    return this;
  }

  public void stop() {
    this.nettyServer.stop();
  }
}
