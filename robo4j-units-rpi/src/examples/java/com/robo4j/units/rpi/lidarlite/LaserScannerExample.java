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
package com.robo4j.units.rpi.lidarlite;

import java.io.IOException;
import java.io.InputStream;

import com.robo4j.core.RoboBuilder;
import com.robo4j.core.RoboBuilderException;
import com.robo4j.core.RoboContext;
import com.robo4j.core.RoboReference;
import com.robo4j.units.rpi.pwm.ServoUnitExample;

/**
 * Runs the laser scanner, printing the max range and min range found on stdout.
 * (To see all data, run with JFR and dump a recording.)
 * 
 * @author Marcus Hirt (@hirt)
 * @author Miroslav Wengner (@miragemiko)
 */
public class LaserScannerExample {

	public static void main(String[] args) throws RoboBuilderException, IOException {
		RoboBuilder builder = new RoboBuilder();
		InputStream settings = ServoUnitExample.class.getClassLoader().getResourceAsStream("lidarexample.xml");
		if (settings == null) {
			System.out.println("Could not find the settings for the LaserScannerExample!");
			System.exit(2);
		}
		builder.add(settings).add(LaserScannerTestController.class, "controller").add(LaserScanProcessor.class, "processor");
		RoboContext ctx = builder.build();
		RoboReference<String> reference = ctx.getReference("controller");
		System.out.println("Starting scanning for ever\nPress enter to quit");
		reference.sendMessage("scan");
		System.in.read();	
	}
}
