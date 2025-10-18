/*
 * The MIT License
 *
 * Copyright 2025 Viktor Alexander Hartung.
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

import com.hartrusion.modeling.PhysicalDomain;
import static org.testng.Assert.*;
import org.testng.annotations.Test;

/**
 *
 * @author Viktor Alexander Hartung
 */
public class LinearDissipatorTest {

    public LinearDissipatorTest() {
    }

    /**
     * This test will connect a linear dissipator with an effor source. Its the
     * most basic ohms law thingy.
     */
    @Test
    public void testBasicOhmsLaw() {
        System.out.println("basicOhmsLaw");
        /*
         *          p1            It is expected for the flow to be 0.02 Ampere.
         *   --------o--------    The flow is expecte to to be a positive value
         * 1 |            0  |    according to convention (positive: into 0, out
         *   |               X    of 1).
         *  (|) U            X R  
         *   |  16 V         X  800 Ohms
         * 0 |            1  |
         *   o---------------- 
         *   |       p2
         *  _|_ z
         *
         */
        LinearDissipator r;
        EffortSource u;
        ClosedOrigin z;
        GeneralNode p1, p2;

        r = new LinearDissipator(PhysicalDomain.ELECTRICAL);
        r.setName("Resistor");
        r.setResistanceParameter(800);
        u = new EffortSource(PhysicalDomain.ELECTRICAL);
        u.setName("Voltage-Source");
        u.setEffort(16);
        z = new ClosedOrigin(PhysicalDomain.ELECTRICAL);
        z.setName("Zero");
        p1 = new GeneralNode(PhysicalDomain.ELECTRICAL);
        p1.setName("node1");
        p2 = new GeneralNode(PhysicalDomain.ELECTRICAL);
        p2.setName("node2");

        z.connectTo(p1);
        u.connectTo(p1);
        u.connectTo(p2);
        r.connectTo(p2);
        r.connectTo(p1);

        r.prepareCalculation();
        u.prepareCalculation();

        z.doCalculation();
        u.doCalculation();
        r.doCalculation();

        // calculation must be finished
        assertEquals(u.isCalculationFinished(), true);
        assertEquals(r.isCalculationFinished(), true);

        assertEquals(r.getFlow(), 0.02, 0.000001);
    }

    /**
     * Test behaviour of open connection with a voltage soruce. This is an
     * allowed case that can happen for some model states with a closed valve.
     */
    @Test
    public void testOpenConnectionWithVoltageSource() {
        System.out.println("openConnectionWithVoltageSource");
        /*
         *          p1           
         *   --------o--------   
         * 1 |            0  |    
         *   |                  
         *  (|) U              R  
         *   |  16 V           inf Ohms
         * 0 |            1  |
         *   o---------------- 
         *   |       p2
         *  _|_ z
         *
         */
        LinearDissipator r;
        EffortSource u;
        ClosedOrigin z;
        GeneralNode p1, p2;

        r = new LinearDissipator(PhysicalDomain.ELECTRICAL);
        r.setName("Resistor");
        r.setOpenConnection();
        u = new EffortSource(PhysicalDomain.ELECTRICAL);
        u.setName("Voltage-Source");
        u.setEffort(123456);
        z = new ClosedOrigin(PhysicalDomain.ELECTRICAL);
        z.setName("Zero");
        p1 = new GeneralNode(PhysicalDomain.ELECTRICAL);
        p1.setName("node1");
        p2 = new GeneralNode(PhysicalDomain.ELECTRICAL);
        p2.setName("node2");

        z.connectTo(p1);
        u.connectTo(p1);
        u.connectTo(p2);
        r.connectTo(p2);
        r.connectTo(p1);

        r.prepareCalculation();
        u.prepareCalculation();

        z.doCalculation();
        u.doCalculation();
        r.doCalculation();

        // calculation must be finished
        assertEquals(u.isCalculationFinished(), true);
        assertEquals(r.isCalculationFinished(), true);

        assertEquals(r.getFlow(), 0.0, 1e-8);
        assertEquals(p1.getEffort(), 0.0, 1e-8);
        assertEquals(p2.getEffort(), 123456, 1e-8);
    }

}
