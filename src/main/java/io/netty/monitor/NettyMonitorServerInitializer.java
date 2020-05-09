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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.CharsetUtil;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class NettyMonitorServerInitializer extends ChannelInitializer<SocketChannel> {
  private final SslContext sslContext;

  public NettyMonitorServerInitializer(SslContext sslContext) {
    this.sslContext = sslContext;
  }

  @Override
  protected void initChannel(SocketChannel ch) {
    ChannelPipeline channelPipeline = ch.pipeline();
    ByteBuf delimiter = Unpooled.copiedBuffer("$_", CharsetUtil.UTF_8);
    channelPipeline.addLast(new StringDecoder(CharsetUtil.UTF_8));
    channelPipeline.addLast(new StringEncoder(CharsetUtil.UTF_8));
    channelPipeline.addLast(new DelimiterBasedFrameDecoder(1024, delimiter));

    if (!Objects.isNull(sslContext)){
      channelPipeline.addLast(sslContext.newHandler(ch.alloc()));
    }

    channelPipeline.addLast(new IdleStateHandler(45, 0, 0, TimeUnit.SECONDS));
    channelPipeline.addLast(new NettyMonitorServerHandler());
  }
}
