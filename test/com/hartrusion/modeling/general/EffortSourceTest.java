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
import com.hartrusion.modeling.exceptions.CalculationException;
import com.hartrusion.modeling.exceptions.ModelErrorException;
import com.hartrusion.util.SimpleLogOut;
import static org.testng.Assert.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 *
 * @author Viktor Alexander Hartung
 */
public class EffortSourceTest {

    EffortSource instance;
    LinearDissipator r;
    ClosedOrigin z;
    GeneralNode p1, p2;

    @BeforeClass
    public static void setUpClass() throws Exception {
        // Keep Log out clean during test run
        SimpleLogOut.configureLoggingWarningsOnly();
    }

    public EffortSourceTest() {
    }

    @BeforeMethod
    public void setUpMethod() throws Exception {
        instance = new EffortSource(PhysicalDomain.ELECTRICAL);
        instance.setEffort(16);

        r = new LinearDissipator(PhysicalDomain.ELECTRICAL);
        r.setResistanceParameter(800);
        r.setName("Resistor");

        z = new ClosedOrigin(PhysicalDomain.ELECTRICAL);
        z.setName("Zero");
        p1 = new GeneralNode(PhysicalDomain.ELECTRICAL);
        p1.setName("node1");
        p2 = new GeneralNode(PhysicalDomain.ELECTRICAL);
        p2.setName("node2");

        z.connectTo(p1);
        instance.connectTo(p1);
        instance.connectTo(p2);
        r.connectTo(p2);
        r.connectTo(p1);
    }

    @AfterMethod
    public void tearDownMethod() throws Exception {
        instance = null;
        r = null;
        z = null;
        p1 = null;
        p2 = null;
    }

    /**
     * Most simple test, effort source on a resistance.
     */
    @Test
    public void testSourceAndResistor() {
        /*
         *          p2            It is expected for the flow to be 0.02 Ampere.
         *   --------o--------    The flow is expecte to to be a positive value
         * 1 |            0  |    according to convention (positive: into 0, out
         *   |               X    of 1).
         *  (|) U            X R  
         *   |  16 V         X  800 Ohms
         * 0 |            1  |
         *   o---------------- 
         *   |  p1
         *  _|_ z
         *
         */

        r.prepareCalculation();
        instance.prepareCalculation();

        z.doCalculation();
        instance.doCalculation();
        r.doCalculation();

        // calculation must be finished
        assertEquals(instance.isCalculationFinished(), true);
        assertEquals(r.isCalculationFinished(), true);

        assertEquals(r.getFlow(), 0.02, 0.000001);
    }

    /**
     * An open effort source does nothing but it is allowed and valid.
     */
    @Test
    public void testOpenSource() {
        /*
         *          p2          
         *   --------o-------- 
         * 1 |            0  |   
         *   |                   
         *  (|) U              R  
         *   |  16 V            inf Ohms
         * 0 |            1  |
         *   o---------------- 
         *   |  p1
         *  _|_ z
         *
         */

        r.setOpenConnection();

        r.prepareCalculation();
        instance.prepareCalculation();

        z.doCalculation();
        instance.doCalculation();
        r.doCalculation();

        // calculation must be finished
        assertEquals(instance.isCalculationFinished(), true);
        assertEquals(r.isCalculationFinished(), true);

        assertEquals(r.getFlow(), 0.0, 1e-12, "Flow must be zero");
        assertEquals(p2.getEffort(), 16.0, 1e-12, "Voltage not as expected");
        assertEquals(p1.getEffort(), 0.0, 1e-12, "Voltage not as expected");
    }

    /**
     * This test is supposed to fail. We will catch the exception and expect the
     * calculation run not to be finished.
     */
    // @Test
    public void testBridgedSource() {
        /*
         *          p2          
         *   --------o-------- 
         * 1 |            0  |   
         *   |              |||  
         *  (|) U           ||| R  
         *   |  16 V        ||| inf Ohms
         * 0 |            1  |
         *   o---------------- 
         *   |  p1
         *  _|_ z
         *
         */
        r.setBridgedConnection();

        r.prepareCalculation();
        instance.prepareCalculation();
        try {
            z.doCalculation();
            instance.doCalculation();
            r.doCalculation();
            instance.doCalculation();
        } catch (ModelErrorException e) {
            // its okay to fail like this
        } catch (CalculationException e) {
            // this is also totally fine - a validation now throws this thing.
        }

        // There might be no error but calculation must not finish as such
        // a network can't calculate a solution. Network solvers will throw
        // a warning to log if such things ever occur, getting a full network
        // solution is part of the solvers.
        assertEquals(instance.isCalculationFinished(), false);
    }

}
