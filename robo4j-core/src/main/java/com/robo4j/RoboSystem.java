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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Robo4J. If not, see <http://www.gnu.org/licenses/>.
 */
package com.robo4j;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import com.robo4j.configuration.Configuration;
import com.robo4j.logging.SimpleLoggingUtil;
import com.robo4j.scheduler.DefaultScheduler;
import com.robo4j.scheduler.RoboThreadFactory;
import com.robo4j.scheduler.Scheduler;

/**
 * This is the default implementation for a local {@link RoboContext}. Contains
 * RoboUnits, a lookup service for references to RoboUnits, and a system level
 * life cycle.
 *
 * @author Marcus Hirt (@hirt)
 * @author Miroslav Wengner (@miragemiko)
 */
final class RoboSystem implements RoboContext {
	private static final String NAME_BLOCKING_POOL = "Robo4J Blocking Pool";
	private static final String NAME_WORKER_POOL = "Robo4J Worker Pool";
	private static final String KEY_SCHEDULER_POOL_SIZE = "poolSizeScheduler";
	private static final String KEY_WORKER_POOL_SIZE = "poolSizeWorker";
	private static final String KEY_BLOCKING_POOL_SIZE = "poolSizeBlocking";

	private static final int DEFAULT_BLOCKING_POOL_SIZE = 4;
	private static final int DEFAULT_WORKING_POOL_SIZE = 2;
	private static final int DEFAULT_SCHEDULER_POOL_SIZE = 2;
	private static final int KEEP_ALIVE_TIME = 10;
	private static final EnumSet<LifecycleState> MESSAGE_DELIVERY_CRITERIA = EnumSet.of(LifecycleState.STARTED, LifecycleState.STOPPED,
			LifecycleState.STOPPING);

	private final AtomicReference<LifecycleState> state = new AtomicReference<>(LifecycleState.UNINITIALIZED);
	private final Map<String, RoboUnit<?>> units = new HashMap<>();
	private final Map<RoboUnit<?>, RoboReference<?>> referenceCache = new WeakHashMap<>();

	private final Scheduler systemScheduler;

	private final ThreadPoolExecutor workExecutor;
	private final LinkedBlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>();

	private final ThreadPoolExecutor blockingExecutor;
	private final LinkedBlockingQueue<Runnable> blockingQueue = new LinkedBlockingQueue<>();

	private final String uid;

	private enum DeliveryPolicy {
		SYSTEM, WORK, BLOCKING
	}

	private enum ThreadingPolicy {
		NORMAL, CRITICAL
	}

	private class LocalRoboReference<T> implements RoboReference<T> {
		private final RoboUnit<T> unit;
		private final DeliveryPolicy deliveryPolicy;
		private final ThreadingPolicy threadingPolicy;

		LocalRoboReference(RoboUnit<T> unit) {
			this.unit = unit;
			@SuppressWarnings("unchecked")
			Class<? extends RoboUnit<?>> clazz = (Class<? extends RoboUnit<?>>) unit.getClass();
			this.deliveryPolicy = deriveDeliveryPolicy(clazz);
			this.threadingPolicy = deriveThreadingPolicy(clazz);
		}

		private ThreadingPolicy deriveThreadingPolicy(Class<? extends RoboUnit<?>> clazz) {
			if (clazz.getAnnotation(CriticalSectionTrait.class) != null) {
				return ThreadingPolicy.CRITICAL;
			}
			return ThreadingPolicy.NORMAL;
		}

		private DeliveryPolicy deriveDeliveryPolicy(Class<? extends RoboUnit<?>> clazz) {
			if (clazz.getAnnotation(WorkTrait.class) != null) {
				return DeliveryPolicy.WORK;
			}
			if (clazz.getAnnotation(BlockingTrait.class) != null) {
				return DeliveryPolicy.BLOCKING;
			}
			return DeliveryPolicy.SYSTEM;
		}

		@Override
		public String getId() {
			return unit.getId();
		}

		@Override
		public LifecycleState getState() {
			return unit.getState();
		}

		@Override
		public Configuration getConfiguration() {
			return unit.getConfiguration();
		}

		@Override
		public void sendMessage(T message) {
			if (MESSAGE_DELIVERY_CRITERIA.contains(getState())) {
				if (threadingPolicy != ThreadingPolicy.CRITICAL) {
					deliverOnQueue(message);
				} else {
					synchronized (unit) {
						deliverOnQueue(message);
					}
				}
			}
		}

		private void deliverOnQueue(T message) {
			switch (deliveryPolicy) {
			case SYSTEM:
				systemScheduler.execute(new Messenger<T>(unit, message));
				break;
			case WORK:
				workExecutor.execute(new Messenger<T>(unit, message));
				break;
			case BLOCKING:
				blockingExecutor.execute(new Messenger<T>(unit, message));
				break;
			default:
				SimpleLoggingUtil.error(getClass(), "not supported policy: " + deliveryPolicy);
			}
		}

		@Override
		public <R> Future<R> getAttribute(AttributeDescriptor<R> attribute) {
			return systemScheduler.submit(() -> unit.onGetAttribute(attribute));
		}

		@Override
		public Collection<AttributeDescriptor<?>> getKnownAttributes() {
			return unit.getKnownAttributes();
		}

		@Override
		public Future<Map<AttributeDescriptor<?>, Object>> getAttributes() {
			return systemScheduler.submit(unit::onGetAttributes);
		}

		@Override
		public Class<T> getMessageType() {
			return unit.getMessageType();
		}
	}

	// Protects the executors from problems in the units.
	private static class Messenger<T> implements Runnable {
		private final RoboUnit<T> unit;
		private final T message;

		public Messenger(RoboUnit<T> unit, T message) {
			this.unit = unit;
			this.message = message;
		}

		@Override
		public void run() {
			try {
				unit.onMessage(message);
			} catch (Throwable t) {
				SimpleLoggingUtil.error(unit.getClass(), "Error processing message", t);
			}
		}
	}

	/**
	 * Constructor.
	 */
	RoboSystem() {
		this(UUID.randomUUID().toString());
	}

	/**
	 * Constructor.
	 */
	RoboSystem(String uid) {
		this(uid, DEFAULT_SCHEDULER_POOL_SIZE, DEFAULT_WORKING_POOL_SIZE, DEFAULT_BLOCKING_POOL_SIZE);
	}

	/**
	 * Constructor.
	 */
	RoboSystem(String uid, Configuration systemConfig) {
		this(uid, systemConfig.getInteger(KEY_SCHEDULER_POOL_SIZE, DEFAULT_SCHEDULER_POOL_SIZE),
				systemConfig.getInteger(KEY_WORKER_POOL_SIZE, DEFAULT_WORKING_POOL_SIZE),
				systemConfig.getInteger(KEY_BLOCKING_POOL_SIZE, DEFAULT_SCHEDULER_POOL_SIZE));
	}

	/**
	 * Constructor.
	 */
	RoboSystem(Configuration config) {
		this(UUID.randomUUID().toString(), config);
	}

	/**
	 * Constructor.
	 */
	RoboSystem(String uid, int schedulerPoolSize, int workerPoolSize, int blockingPoolSize) {
		this.uid = uid;
		workExecutor = new ThreadPoolExecutor(workerPoolSize, workerPoolSize, KEEP_ALIVE_TIME, TimeUnit.SECONDS, workQueue,
				new RoboThreadFactory(new ThreadGroup(NAME_WORKER_POOL), NAME_WORKER_POOL, true));
		blockingExecutor = new ThreadPoolExecutor(blockingPoolSize, blockingPoolSize, KEEP_ALIVE_TIME, TimeUnit.SECONDS, blockingQueue,
				new RoboThreadFactory(new ThreadGroup(NAME_BLOCKING_POOL), NAME_BLOCKING_POOL, true));
		systemScheduler = new DefaultScheduler(this, schedulerPoolSize);
	}

	/**
	 * Adds the specified units to the system.
	 * 
	 * @param unitSet
	 *            the units to add.
	 */
	public void addUnits(Set<RoboUnit<?>> unitSet) {
		if (state.get() != LifecycleState.UNINITIALIZED) {
			throw new UnsupportedOperationException("All units must be registered up front for now.");
		}
		addToMap(unitSet);
	}

	/**
	 * Adds the specified units to the system.
	 * 
	 * @param units
	 *            the units to add.
	 */
	public void addUnits(RoboUnit<?>... units) {
		if (state.get() != LifecycleState.UNINITIALIZED) {
			throw new UnsupportedOperationException("All units must be registered up front for now.");
		}
		addToMap(units);
	}

	@Override
	public void start() {
		if (state.compareAndSet(LifecycleState.STOPPED, LifecycleState.STARTING)) {
			// NOTE(Marcus/Sep 4, 2017): Do we want to support starting a
			// stopped system?
			startUnits();
		}
		if (state.compareAndSet(LifecycleState.INITIALIZED, LifecycleState.STARTING)) {
			startUnits();
		}

		// This is only used from testing for now, it should never happen from
		// the builder.
		if (state.compareAndSet(LifecycleState.UNINITIALIZED, LifecycleState.STARTING)) {
			startUnits();
		}

	}

	private void startUnits() {
		// NOTE(Marcus/Sep 4, 2017): May want to schedule the starts.
		for (RoboUnit<?> unit : units.values()) {
			unit.setState(LifecycleState.STARTING);
			unit.start();
			unit.setState(LifecycleState.STARTED);
		}
		state.set(LifecycleState.STARTED);
	}

	@Override
	public void stop() {
		// NOTE(Marcus/Sep 4, 2017): We may want schedule the stops in the
		// future.
		if (state.compareAndSet(LifecycleState.STARTED, LifecycleState.STOPPING)) {
			units.values().forEach(RoboUnit::stop);
		}
		state.set(LifecycleState.STOPPED);
	}

	@Override
	public void shutdown() {
		stop();
		state.set(LifecycleState.SHUTTING_DOWN);
		units.values().forEach((unit) -> unit.setState(LifecycleState.SHUTTING_DOWN));

		// First shutdown all executors. We don't care at this point, as any
		// messages will no longer be delivered.
		workExecutor.shutdown();
		blockingExecutor.shutdown();

		// Then schedule shutdowns on the scheduler threads...
		for (RoboUnit<?> unit : units.values()) {
			getScheduler().execute(new Runnable() {
				@Override
				public void run() {
					RoboSystem.shutdownUnit(unit);
				}
			});
		}

		// Then shutdown the system scheduler. Will wait until the termination
		// shutdown of the system scheduler (or the timeout).
		try {
			systemScheduler.shutdown();
		} catch (InterruptedException e) {
			SimpleLoggingUtil.error(getClass(), "System scheduler was interrupted when shutting down.", e);
		}
		state.set(LifecycleState.SHUTDOWN);
	}

	@Override
	public LifecycleState getState() {
		return state.get();
	}

	public void setState(LifecycleState state) {
		this.state.set(state);
	}

	@Override
	public Collection<RoboReference<?>> getUnits() {
		return units.values().stream().map(unit -> getReference(unit)).collect(Collectors.toList());
	}

	@Override
	public <T> RoboReference<T> getReference(String id) {
		@SuppressWarnings("unchecked")
		RoboUnit<T> roboUnit = (RoboUnit<T>) units.get(id);
		if (roboUnit == null) {
			return null;
		}
		return getReference(roboUnit);
	}

	@Override
	public Scheduler getScheduler() {
		return systemScheduler;
	}

	@Override
	public String getId() {
		return uid;
	}

	/**
	 * Returns the reference for a specific unit.
	 * 
	 * @param roboUnit
	 *            the robo unit for which to retrieve a reference.
	 * @return the {@link RoboReference} to the unit.
	 */
	public <T> RoboReference<T> getReference(RoboUnit<T> roboUnit) {
		@SuppressWarnings("unchecked")
		RoboReference<T> reference = (RoboReference<T>) referenceCache.get(roboUnit);
		if (reference == null) {
			reference = createReference(roboUnit);
			referenceCache.put(roboUnit, reference);
		}
		return reference;
	}

	private <T> RoboReference<T> createReference(RoboUnit<T> roboUnit) {
		return new LocalRoboReference<>(roboUnit);
	}

	private void addToMap(Set<RoboUnit<?>> unitSet) {
		unitSet.forEach(unit -> units.put(unit.getId(), unit));
	}

	private void addToMap(RoboUnit<?>... unitArray) {
		// NOTE(Marcus/Aug 9, 2017): Do not streamify...
		for (RoboUnit<?> unit : unitArray) {
			units.put(unit.getId(), unit);
		}
	}

	private static void shutdownUnit(RoboUnit<?> unit) {
		// NOTE(Marcus/Aug 11, 2017): Should really be scheduled and done in
		// parallel.
		unit.shutdown();
		unit.setState(LifecycleState.SHUTDOWN);
	}
}