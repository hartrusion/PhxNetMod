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
import com.hartrusion.modeling.solvers.SimpleIterator;
import static org.testng.Assert.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 *
 * @author Viktor Alexander Hartung
 */
public class MutualCapacitanceTest {

    /*   n1      r       n2                  tau = R * C
     *   o-----XXXXXX-----o                  After time tau has passed, the
     *   |                |                  effort in C has 63 % of its final
     *   |                |                  value.
     *  (|) u           ===== instance       Given R = 470 kOhms and C = 5.6 µF
     *   |                |                  tau will be 2.632 seconds.
     *   |                |                  Note that "tau" is not the same for
     *   o-----------------                  this circuit and the instance.
     *   | n0
     *  _|_  gnd
     */
    MutualCapacitance instance;
    LinearDissipator r;
    EffortSource u;
    ClosedOrigin gnd;
    GeneralNode[] n = new GeneralNode[3];
    SimpleIterator solver;

    public MutualCapacitanceTest() {
    }

    @BeforeMethod
    public void setUpMethod() throws Exception {
        r = new LinearDissipator(PhysicalDomain.ELECTRICAL);
        r.setName("r");
        r.setResistanceParameter(4.7e5);
        u = new EffortSource(PhysicalDomain.ELECTRICAL);
        u.setName("u");
        u.setEffort(100);
        gnd = new ClosedOrigin(PhysicalDomain.ELECTRICAL);
        gnd.setName("gnd");
        for (int idx = 0; idx < n.length; idx++) {
            n[idx] = new GeneralNode(PhysicalDomain.ELECTRICAL);
            n[idx].setName("n" + idx);
        }
        gnd.connectTo(n[0]);
        u.connectTo(n[0]);
        u.connectTo(n[1]);
        r.connectTo(n[1]);
        r.connectTo(n[2]);

        instance = new MutualCapacitance(PhysicalDomain.ELECTRICAL);
        instance.setTimeConstant(5.6e-6);

        instance.connectTo(n[2]);
        instance.connectTo(n[0]);

        solver = new SimpleIterator();
        solver.addElement(gnd);
        solver.addElement(u);
        solver.addElement(r);
        solver.addElement(instance);

    }

    @AfterMethod
    public void tearDownMethod() throws Exception {
        instance = null;
        r = null;
        u = null;
        gnd = null;
        n = new GeneralNode[3];
        solver = null;
    }

    @DataProvider(name = "stepTime")
    public static Object[] stepTime() {
        return new Object[]{0.01, 0.1, 0.2};
    }

    /**
     * Tests the charge function of a capacitor with prepared values.It is
     * expected for the charge to reach 63 % after tau is passed, while tau is
     * calculated by timeConstant and resistance of the prepared elements.
     *
     * @param stepTime Global step time for model, given by data provider as a
     * parameter.
     */
    @Test(dataProvider = "stepTime")
    public void testChargeFunction(double stepTime) {
        instance.setStepTime(stepTime);
        int numberOfSteps = (int) (2.632 / stepTime);
        for (int step = 0; step < numberOfSteps; step++) {
            solver.prepareCalculation();
            assertEquals(solver.isCalculationFinished(), false);
            solver.doCalculation();
            assertEquals(solver.isCalculationFinished(), true, "no solution?");
        }
        // use step time as delta, larger step time results in greater error.
        assertEquals(instance.getEffort(), 63.0, stepTime * 20,
                "Charge not reached");
    }

}
