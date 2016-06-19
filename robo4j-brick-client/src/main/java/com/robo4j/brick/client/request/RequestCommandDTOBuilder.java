/*
 * Copyright (C) 2016. Miroslav Kopecky
 * This RequestCommandDTOBuilder.java is part of robo4j.
 *
 *     robo4j is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     robo4j is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with robo4j .  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.robo4j.brick.client.request;

import com.robo4j.brick.dto.ClientCommandDTO;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by miroslavkopecky on 19/06/16.
 */
public class RequestCommandDTOBuilder {

    public static final class Builder<DTOObject extends ClientCommandDTO> {

        private final List<DTOObject> content;

        public Builder(){
            this.content = new LinkedList<>();
        }

        public RequestCommandDTOBuilder.Builder<DTOObject> add(DTOObject element){
            this.content.add(element);
            return this;
        }

        public RequestCommandDTOBuilder.Builder<DTOObject> addAll(Iterable<DTOObject> elements){
            Iterator<DTOObject> iterator = elements.iterator();

            while(iterator.hasNext()) {
                DTOObject element = iterator.next();
                this.add(element);
            }
            return this;
        }

        public List<DTOObject> build(){
            return Collections.unmodifiableList(content);
        }

    }

}
