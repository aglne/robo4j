package com.robo4j.socket.http.units;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration for http server unit {@link HttpServerUnit} Server context
 * contains available registered paths
 *
 * @author Marcus Hirt (@hirt)
 * @author Miroslav Wengner (@miragemiko)
 */
public final class ServerContext implements HttpContext<ServerPathConfig> {

	/**
	 * map of registered paths and related configuration
	 */
	private final Map<String, ServerPathConfig> pathConfigs = new HashMap<>();

	/**
	 * context properties
	 */
	private final Map<String, Object> properties = new HashMap<>();

	ServerContext(){
	}

	@Override
	public void addPaths(Map<String, ServerPathConfig> paths) {
		pathConfigs.putAll(paths);
	}

	@Override
	public boolean isEmpty() {
		return pathConfigs.isEmpty();
	}

	@Override
	public boolean containsPath(String path) {
		return pathConfigs.containsKey(path);
	}

	@Override
	public Collection<ServerPathConfig> getPathConfigs() {
		return pathConfigs.values();
	}

	@Override
	public ServerPathConfig getPathConfig(String path) {
		return pathConfigs.get(path);
	}

	@Override
	public void putProperty(String key, Object val) {
		properties.put(key, val);
	}

	@Override
	public <E> E getProperty(Class<E> clazz, String key) {
		return properties.containsKey(key) ? clazz.cast(properties.get(key)) : null;
	}

	/**
	 *
	 * @param clazz
	 *            desired known class E
	 * @param key
	 *            property key
	 * @param <E>
	 *            property element instance
	 * @return property element
	 */
	@Override
	public <E> E getPropertySafe(Class<E> clazz, String key) {
		return clazz.cast(properties.get(key));
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		ServerContext that = (ServerContext) o;
		return Objects.equals(pathConfigs, that.pathConfigs) && Objects.equals(properties, that.properties);
	}

	@Override
	public int hashCode() {

		return Objects.hash(pathConfigs, properties);
	}

	@Override
	public String toString() {
		return "ServerContext{" + "pathConfigs=" + pathConfigs + '}';
	}
}