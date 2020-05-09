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

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.monitor.banner.BannerFont;
import io.netty.monitor.utils.SystemInfoUtils;
import io.netty.util.ResourceLeakDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import oshi.SystemInfo;
import oshi.hardware.ComputerSystem;
import oshi.hardware.Firmware;
import oshi.hardware.HardwareAbstractionLayer;

import javax.net.ssl.SSLException;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.CertificateException;

import static io.netty.monitor.Const.*;

public class NettyMonitorServer implements Server {
  private static final Logger log = LoggerFactory.getLogger(NettyMonitorServer.class);

  /** Obtain system information through oshi. */
  private final SystemInfo systemInfo = new SystemInfo();
  private final HardwareAbstractionLayer hardware = systemInfo.getHardware();
  private final ComputerSystem computerSystem = hardware.getComputerSystem();
  private final Firmware firmware = computerSystem.getFirmware();

  /** Netty builds long connection service. */
  private final ServerBootstrap serverBootstrap = new ServerBootstrap();
  private final NettyServerBanner defaultBanner = new NettyServerBanner();
  private EventLoopGroup bossGroup;
  private EventLoopGroup workerGroup;
  private Channel channel;
  private SslContext sslContext;

  /** Service startup status, using volatile to ensure threads are visible. */
  private volatile boolean stop = false;

  private NettyMonitor nettyMonitor;
  private Environment environment;

  @Override
  public void start(NettyMonitor nettyMonitor) throws Exception {
    long startMs = System.currentTimeMillis();

    this.nettyMonitor = nettyMonitor;
    this.environment = nettyMonitor.environment();
    this.printBanner();

    final String serialNumber = this.computerSystem.getSerialNumber();
    final String model = this.computerSystem.getModel();
    final String name = firmware.getName();
    final String bootClsName = nettyMonitor.bootName();
    final String currentUserName = System.getProperty("user.name");
    final String pidCode = SystemInfoUtils.getPid();
    final String hostName = SystemInfoUtils.getHostName();
    final Integer availableProcessors = SystemInfoUtils.getAvailableProcessors();

    log.info("Starting {} on {} with PID {} ", bootClsName, name + "/" + currentUserName, pidCode);
    log.info("The serialized version number of this machine is {}", serialNumber);
    log.info("The computer system mode is {}", model);
    log.info("The host name is {}", hostName);
    log.info("The current computer has {} processors available", availableProcessors);
    log.info("Starting service [Netty]");
    log.info("Starting Iot Server: Netty/4.1.45.Final");

    this.enableSSL();
    this.startServer(startMs);
    this.shutdownHook();
  }

  private void enableSSL() throws CertificateException, SSLException {
    log.info("Check if the ssl configuration is enabled.");

    final Boolean ssl = environment.getBoolean(PATH_SERVER_SSL, SERVER_SSL);
    final SelfSignedCertificate ssc = new SelfSignedCertificate();

    if (ssl) {
      log.info("Ssl configuration takes effect :{}", true);

      final String sslCert = this.environment.get(PATH_SERVER_SSL_CERT, null);
      final String sslPrivateKey = this.environment.get(PATH_SERVER_SSL_PRIVATE_KEY, null);
      final String sslPrivateKeyPass = this.environment.get(PATH_SERVER_SSL_PRIVATE_KEY_PASS, null);

      log.info("SSL CertChainFile  Path: {}", sslCert);
      log.info("SSL PrivateKeyFile Path: {}", sslPrivateKey);
      log.info("SSL PrivateKey Pass: {}", sslPrivateKeyPass);

      this.sslContext = SslContextBuilder.forServer(setKeyCertFileAndPriKey(sslCert, ssc.certificate()),
              setKeyCertFileAndPriKey(sslPrivateKey, ssc.privateKey()), sslPrivateKeyPass).build();
    }

    log.info("Current netty server ssl startup status: {}", ssl);
    log.info("A valid ssl connection configuration is not configured and is rolled back to the default connection state.");
  }

  /**
   * Use the configured path if the certificate and private key are
   * configured, otherwise use the default configuration
   *
   * @param keyPath
   * @param defaultFilePath
   * @return
   */
  private File setKeyCertFileAndPriKey(String keyPath, File defaultFilePath) {
    return keyPath != null ? Paths.get(keyPath).toFile() : defaultFilePath;
  }

  /**
   * Start tcp connection server
   *
   * @param startMs
   * @throws Exception
   */
  private void startServer(long startMs) throws Exception {
    ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.DISABLED);

    this.serverBootstrap.childHandler(new NettyMonitorServerInitializer(sslContext));

    int acceptThreadCount = environment.getInteger(PATH_SERVER_NETTY_ACCEPT_THREAD_COUNT, DEFAULT_ACCEPT_THREAD_COUNT);
    int ioThreadCount = environment.getInteger(PATH_SERVER_NETTY_IO_THREAD_COUNT, DEFAULT_IO_THREAD_COUNT);

    NettyServerGroup nettyServerGroup = EventLoopKit.nioGroup(acceptThreadCount, ioThreadCount);
    this.bossGroup = nettyServerGroup.getBossGroup();
    this.workerGroup = nettyServerGroup.getWorkGroup();

    if (EventLoopKit.epollIsAvailable()) {
      nettyServerGroup = EventLoopKit.epollGroup(acceptThreadCount, ioThreadCount);
      this.bossGroup = nettyServerGroup.getBossGroup();
      this.workerGroup = nettyServerGroup.getWorkGroup();
    }

    this.serverBootstrap.group(bossGroup, workerGroup).channel(nettyServerGroup.getChannelClass())
            .childOption(ChannelOption.SO_KEEPALIVE, true)
            .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
            .option(ChannelOption.SO_BACKLOG, 128);

    String bootClsSimpleName = EventLoopKit.judgeMode(nettyServerGroup.getChannelClass().getSimpleName());
    log.info("The IO mode of the application startup is: {}", bootClsSimpleName);

    String address = this.environment.getString(Const.PATH_SERVER_ADDRESS, Const.SERVER_ADDRESS);
    Integer port = this.environment.getInteger(Const.PATH_SERVER_PORT, Const.SERVER_PORT);

    this.channel = serverBootstrap.bind(address, port).sync().channel();

    this.stop = false;

    long endTime = System.currentTimeMillis();
    long startUpTime = (endTime - startMs);
    long jvmStartTime = (endTime - SystemInfoUtils.getJvmStartUpTime());

    log.info("Iot Server started on port(s): {} with context path ''", port);
    log.info("Started {} in {} ms (JVM running for {} ms)", nettyMonitor.bootName(), startUpTime, jvmStartTime);
  }

  /**
   * Add a hook that stops the current service when the system is shut down
   */
  private void shutdownHook() {
    Thread shutdownThread = new Thread(this::stop);
    shutdownThread.setName("shutdown@thread");
    Runtime.getRuntime().addShutdownHook(shutdownThread);
  }

  /**
   * stop http server
   */
  @Override
  public void stop() {
    log.info("Netty Server Shutdown...");
    if (stop) {
      return;
    }
    stop = true;
    try {
      if (bossGroup != null) {
        this.bossGroup.shutdownGracefully();
      }
      if (workerGroup != null) {
        this.workerGroup.shutdownGracefully();
      }
      log.info("The netty service is gracefully closed");
    } catch (Exception e) {
      log.error("An exception occurred while the Netty Iot service was down", e);
    }
  }

  @Override
  public void join() {
    try {
      this.channel.closeFuture().sync();
    } catch (InterruptedException e) {
      log.error("Channel close future fail", e);
    }
  }

  /**
   * print default banner
   */
  private void printBanner() {
    this.defaultBanner.printBanner(System.out, Const.BANNER_TEXT, BannerFont.FONT_DEFAULT);
  }
}
