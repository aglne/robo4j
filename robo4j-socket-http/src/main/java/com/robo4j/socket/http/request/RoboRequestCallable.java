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

package com.robo4j.socket.http.request;

import com.robo4j.AttributeDescriptor;
import com.robo4j.RoboContext;
import com.robo4j.RoboReference;
import com.robo4j.logging.SimpleLoggingUtil;
import com.robo4j.socket.http.HttpMessage;
import com.robo4j.socket.http.HttpMessageDescriptor;
import com.robo4j.socket.http.HttpMessageWrapper;
import com.robo4j.socket.http.dto.RoboPathReferenceDTO;
import com.robo4j.socket.http.enums.StatusCode;
import com.robo4j.socket.http.enums.SystemPath;
import com.robo4j.socket.http.units.HttpUriRegister;
import com.robo4j.socket.http.util.HttpPathUtil;

import java.net.URI;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * @author Marcus Hirt (@hirt)
 * @author Miro Wengner (@miragemiko)
 */
public class RoboRequestCallable implements Callable<RoboResponseProcess> {

	private static final int PATH_SECOND_LEVEL = 2;
	/* currently is supported only one PATH */
	private static final int DEFAULT_PATH_POSITION_0 = 0;
	private static final int DEFAULT_PATH_POSITION_1 = 1;
	public static final int PATH_DEFAULT_LEVEL = 0;
	public static final int PATH_FIRST_LEVEL = 1;

	private final RoboContext context;
	private final HttpMessageDescriptor messageDescriptor;
	private final DefaultRequestFactory<?> factory;

	public RoboRequestCallable(RoboContext context, HttpMessageDescriptor messageDescriptor, DefaultRequestFactory<Object> factory) {
		assert messageDescriptor != null;
		this.context = context;
		this.messageDescriptor = messageDescriptor;
		this.factory = factory;
	}



	@Override
	public RoboResponseProcess call() throws Exception {

		final RoboResponseProcess result = new RoboResponseProcess();

		if (messageDescriptor.getMethod() != null) {
			result.setMethod(messageDescriptor.getMethod());

			final HttpMessage httpMessage = new HttpMessage(messageDescriptor);
			final List<String> paths = HttpPathUtil.uriStringToPathList(messageDescriptor.getPath());

			switch (messageDescriptor.getMethod()) {
			case GET:
				switch (paths.size()) {
				case PATH_DEFAULT_LEVEL:
					result.setCode(StatusCode.OK);
					result.setResult(factory.processGet(context) );
					break;
				case PATH_FIRST_LEVEL:
					result.setCode(StatusCode.NOT_IMPLEMENTED);
					break;
				case PATH_SECOND_LEVEL:
					final RoboPathReferenceDTO pathReference = getRoboReferenceByPath(paths);
					switch (pathReference.getPath()) {
					case NONE:
						result.setCode(StatusCode.NOT_FOUND);
						break;
					case UNITS:
						if (pathReference.getRoboReference() == null) {
							result.setCode(StatusCode.NOT_FOUND);
						} else {
							AttributeDescriptor<?> attributeDescriptor = getAttributeByQuery(
									pathReference.getRoboReference(), httpMessage.uri());
							if (attributeDescriptor != null) {
								result.setCode(StatusCode.OK);
								result.setResult(factory.processGet(pathReference.getRoboReference(), attributeDescriptor));
							} else {
								final Object unitDescription = factory
										.processGetByRegisteredPaths(pathReference.getRoboReference(), paths);
								result.setCode(StatusCode.OK);
								result.setResult(unitDescription);
							}
						}
					}
					break;
				default:
				}
				return result;
			case POST:
				final String postValue = messageDescriptor.getMessage();
				switch (paths.size()) {
				case PATH_DEFAULT_LEVEL:
				case PATH_FIRST_LEVEL:
					result.setCode(StatusCode.NOT_IMPLEMENTED);
					break;
				case PATH_SECOND_LEVEL:
					final RoboPathReferenceDTO pathReference = getRoboReferenceByPath(paths);
					switch (pathReference.getPath()) {
					case UNITS:
						if (pathReference.getRoboReference() != null) {
							Object respObj = factory.processPost(pathReference.getRoboReference(), paths,
									new HttpMessageWrapper<>(httpMessage, postValue));
							if (respObj != null) {
								result.setCode(StatusCode.ACCEPTED);
								result.setResult(respObj);
							} else {
								result.setCode(StatusCode.NOT_FOUND);
							}
						}
						break;
					case NONE:
					default:
						result.setCode(StatusCode.NOT_FOUND);
					}
					break;
				}

				return result;

			default:
				result.setCode(StatusCode.BAD_REQUEST);
				SimpleLoggingUtil.debug(getClass(), "not implemented method: " + messageDescriptor.getMethod());
			}
		} else {
			result.setCode(StatusCode.BAD_REQUEST);
		}

		return result;
	}

	// Private Methods
	/**
	 * @param unit
	 *            desired unit {@see RoboReference}
	 * @param query
	 *            URI query attributes
	 * @return specific Attribute
	 */
	private AttributeDescriptor<?> getAttributeByQuery(RoboReference<?> unit, URI query) {
		// @formatter:off
		return unit.getKnownAttributes().stream().filter(a -> a.getAttributeName().equals(query.getRawQuery()))
				.findFirst().orElse(null);
		// @formatter:on
	}

	/**
	 * parse desired path. If no path available. System health state for all units
	 * is returned returned note: currently is supported only one level path
	 *
	 * @param paths
	 *            registered paths by the configuration
	 * @return reference to desired RoboUnit
	 */
	private RoboPathReferenceDTO getRoboReferenceByPath(final List<String> paths) {
		if (paths.isEmpty()) {
			return new RoboPathReferenceDTO(SystemPath.NONE, null);
		} else {
			final SystemPath systemPath = SystemPath.getByPath(paths.get(DEFAULT_PATH_POSITION_0));
			if (systemPath != null && paths.size() == PATH_SECOND_LEVEL) {
				switch (systemPath) {
				case UNITS:
					final HttpUriRegister httpUriRegister = HttpUriRegister.getInstance();
					final RoboReference<?> reference = httpUriRegister
							.getRoboUnitByPath(paths.get(DEFAULT_PATH_POSITION_1));
					return new RoboPathReferenceDTO(systemPath, reference);
				default:
					throw new IllegalArgumentException("Unsupported path " + systemPath);
				}
			}
			return new RoboPathReferenceDTO(SystemPath.NONE, null);
		}

	}

}
