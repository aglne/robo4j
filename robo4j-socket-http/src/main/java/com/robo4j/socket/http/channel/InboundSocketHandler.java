/*
 * Copyright (c) 2014, 2017, Marcus Hirt, Miroslav Wengner
 *
 * Robo4J is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Robo4J is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Robo4J. If not, see <http://www.gnu.org/licenses/>.
 */

package com.robo4j.socket.http.channel;

import com.robo4j.RoboContext;
import com.robo4j.logging.SimpleLoggingUtil;
import com.robo4j.socket.http.PropertiesProvider;
import com.robo4j.socket.http.request.RoboResponseProcess;
import com.robo4j.socket.http.units.HttpCodecRegistry;
import com.robo4j.socket.http.units.ServerContext;
import com.robo4j.socket.http.util.ChannelUtils;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.robo4j.socket.http.units.HttpServerUnit.PROPERTY_CODEC_REGISTRY;
import static com.robo4j.socket.http.util.RoboHttpUtils.HTTP_PROPERTY_BUFFER_CAPACITY;

/**
 * Inbound context co
 *
 * @author Marcus Hirt (@hirt)
 * @author Miro Wengner (@miragemiko)
 */
public class InboundSocketHandler implements SocketHandler {

	private final RoboContext context;
	private final ServerContext serverContext;
	private final Map<SelectionKey, RoboResponseProcess> outBuffers = new ConcurrentHashMap<>();
	private ServerSocketChannel socketChannel;
	private PropertiesProvider properties;
	private boolean active;

	public InboundSocketHandler(RoboContext context, ServerContext serverContext,
			PropertiesProvider properties) {
		this.context = context;
		this.serverContext = serverContext;
		this.properties = properties;
	}

	@Override
	public void start() {
		if (!active) {
			active = true;
			context.getScheduler().execute(() -> initSocketChannel(serverContext, properties));
		}
	}

	@Override
	public void stop() {
		try {
			if (socketChannel != null && socketChannel.isOpen()) {
				active = false;
				socketChannel.close();
			}
		} catch (IOException e) {
			SimpleLoggingUtil.error(getClass(), "server stop problem: ", e);
		}
	}

	private void initSocketChannel(ServerContext serverContext, PropertiesProvider properties) {
		socketChannel = ChannelUtils.initServerSocketChannel(properties);
		final SelectionKey key = ChannelUtils.registerSelectionKey(socketChannel);

		final HttpCodecRegistry codecRegistry = properties.getPropertySafe(PROPERTY_CODEC_REGISTRY);
		final int bufferCapacity = properties.getIntSafe(HTTP_PROPERTY_BUFFER_CAPACITY);

		while (active) {
			int channelReady = ChannelUtils.getReadyChannelBySelectionKey(key);
			if (channelReady == 0) {
				continue;
			}

			Set<SelectionKey> selectedKeys = key.selector().selectedKeys();
			Iterator<SelectionKey> selectedIterator = selectedKeys.iterator();

			while (selectedIterator.hasNext()) {
				final SelectionKey selectedKey = selectedIterator.next();

				selectedIterator.remove();

				if (selectedKey.isAcceptable()) {
					handleSelectorHandler(new AcceptSelectionKeyHandler(selectedKey, bufferCapacity));
				} else if (selectedKey.isConnectable()) {
					handleSelectorHandler(new ConnectSelectionKeyHandler(selectedKey));
				} else if (selectedKey.isReadable()) {
					handleSelectorHandler(new ReadSelectionKeyHandler(context, serverContext, codecRegistry, outBuffers, selectedKey));
				} else if (selectedKey.isWritable()) {
					handleSelectorHandler(new WriteSelectionKeyHandler(context, serverContext, outBuffers, selectedKey));
				}
			}
		}
	}

	private void handleSelectorHandler(SelectionKeyHandler handler) {
		handler.handle();
	}

}
