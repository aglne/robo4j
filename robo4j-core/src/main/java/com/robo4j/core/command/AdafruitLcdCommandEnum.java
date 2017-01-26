/*
 * Copyright (C) 2017. Miroslav Wengner, Marcus Hirt
 * This AdafruitLcdCommandEnum.java  is part of robo4j.
 * module: robo4j-commons
 *
 * robo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * robo4j is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with robo4j .  If not, see <http://www.gnu.org/licenses/>.
 */

package com.robo4j.core.command;

import static com.robo4j.core.command.CommandTargetEnum.LCD_UNIT;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import com.robo4j.core.enums.RoboHardwareEnumI;
import com.robo4j.core.enums.RoboTargetEnumI;

/**
 * @author Miro Wengner (@miragemiko)
 * @since 15.01.2017
 */
public enum AdafruitLcdCommandEnum implements RoboUnitCommand, RoboHardwareEnumI<CommandTypeEnum>, RoboTargetEnumI<CommandTargetEnum> {

    // @formatter:off
	EXIT		    (0,     "exit"),
	BUTTON_SET 		(1, 	"button_set"),
    BUTTON_RIGHT	(2, 	"button_right"),
    BUTTON_LEFT		(3, 	"button_left"),
    BUTTON_UP		(4, 	"button_up"),
    BUTTON_DOWN     (5, 	"button_down"),
	;
	// @formatter:on

    private volatile static Map<Integer, AdafruitLcdCommandEnum> codeToLcdCommandCodeMapping;

    private int code;
    private String name;

    AdafruitLcdCommandEnum(int c, String name) {
        this.code = c;
        this.name = name;
    }

    public static AdafruitLcdCommandEnum getRequestValue(String name) {
        if (codeToLcdCommandCodeMapping == null) {
            codeToLcdCommandCodeMapping = initMapping();
        }
        return codeToLcdCommandCodeMapping.entrySet().stream().filter(e -> e.getValue().getName().equals(name))
                .map(Map.Entry::getValue).reduce(null, (e1, e2) -> e2);
    }

    public static AdafruitLcdCommandEnum getRequestCommand(CommandTargetEnum target, String name) {
        if (codeToLcdCommandCodeMapping == null) {
            codeToLcdCommandCodeMapping = initMapping();
        }

        return codeToLcdCommandCodeMapping.entrySet().stream().map(Map.Entry::getValue)
                .filter(v -> v.getTarget().equals(target)).filter(v -> v.getName().equals(name))
                .reduce(null, (e1, e2) -> e2);
    }

    public int getCode() {
        return code;
    }

    @Override
    public CommandTypeEnum getType() {
        return CommandTypeEnum.DIRECT;
    }

    @Override
    public String getName() {
        return name;
    }

    public CommandTargetEnum getTarget() {
        return LCD_UNIT;
    }

    @Override
    public String toString() {
        return "AdafruitLcdCommandEnum{" + "code=" + code + ", target=" + getTarget() + ", name='" + name + '\'' + '}';
    }

    // Private Methods
    private static Map<Integer, AdafruitLcdCommandEnum> initMapping() {
        return Arrays.stream(values()).collect(Collectors.toMap(AdafruitLcdCommandEnum::getCode, e -> e));
    }


}