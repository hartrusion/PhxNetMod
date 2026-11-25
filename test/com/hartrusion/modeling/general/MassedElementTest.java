/*
 * The MIT License
 *
 * Copyright 2026 Viktor Alexander Hartung.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.hartrusion.modeling.general;

import com.hartrusion.modeling.heatfluid.HeatClosedFluidTank;
import com.hartrusion.modeling.heatfluid.HeatFluidTank;
import com.hartrusion.modeling.phasedfluid.PhasedClosedSteamedReservoir;
import com.hartrusion.modeling.phasedfluid.Water;
import com.hartrusion.util.SimpleLogOut;
import static org.testng.Assert.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Tests that the elements with a variable mass convert the initial mass that
 * is set with the VariableMassElement interface to their own state value and
 * return the same mass with getMass, which is the mass that the material layer
 * uses for mixing.
 */
public class MassedElementTest {

    @BeforeClass
    public static void setUpClass() throws Exception {
        // Keep Log out clean during test run
        SimpleLogOut.configureLoggingWarningsOnly();
    }

    /**
     * The open tank stores the hydrostatic pressure, so an initial mass of
     * h * rho * A must result in the pressure h * rho * g for a time constant
     * of A / g.
     */
    @Test
    public void testHeatFluidTankInitialMass() {
        HeatFluidTank tank = new HeatFluidTank();
        tank.setTimeConstant(100 / 9.81);
        tank.setInitialMass(7.1 * 997 * 100);
        assertEquals(tank.getEffort(), 7.1 * 997 * 9.81, 1e-6);
        assertEquals(tank.getMass(), 7.1 * 997 * 100, 1e-6);
    }

    /**
     * The closed tank converts the mass with the polytropic compression of
     * the gas above the fluid, so the mass for a given pressure must result in
     * exactly that pressure.
     */
    @Test
    public void testHeatClosedFluidTankInitialMass() {
        HeatClosedFluidTank tank = new HeatClosedFluidTank();
        tank.initCharacteristic(25.0, 1.4, 1000, 25e5);
        // m = rho * V * (1 - (p0 / p)^(1/n)) for p = 35e5 Pa
        double mass = 1000 * 25.0 * (1.0 - Math.pow(25e5 / 35e5, 1.0 / 1.4));
        tank.setInitialMass(mass);
        assertEquals(tank.getEffort(), 35e5, 1e-3);
        assertEquals(tank.getMass(), mass, 1e-6);
    }

    @Test
    public void testPhasedClosedSteamedReservoirInitialMass() {
        PhasedClosedSteamedReservoir reservoir
                = new PhasedClosedSteamedReservoir(Water.INSTANCE);
        reservoir.setInitialMass(14000);
        reservoir.setInitialTemperature(300.0);
        assertEquals(reservoir.getMass(), 14000, 1e-12);
    }
}
