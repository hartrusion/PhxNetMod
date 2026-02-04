/*
 * The MIT License
 *
 * Copyright 2026 viktor.
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
package com.hartrusion.modeling.solvers;

import com.hartrusion.modeling.PhysicalDomain;
import com.hartrusion.modeling.general.GeneralNode;
import com.hartrusion.modeling.general.LinearDissipator;
import com.hartrusion.util.SimpleLogOut;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 *
 * @author viktor
 */
public class StarPolygonTransformTest {

    GeneralNode starNode;
    GeneralNode[] starNodes;
    LinearDissipator[] starElements;
    
    public StarPolygonTransformTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        // Keep Log out clean during test run
        SimpleLogOut.configureLoggingWarningsOnly();
    }

    @AfterMethod
    public void tearDownMethod() throws Exception {
        starNode = null;
        starNodes = null;
        starElements = null;
    }

    /**
     * Generates a star of resistors, initializes the array.
     * @param branches number of branches.
     */
    private void setUpStar(int branches) {
        starNodes = new GeneralNode[branches];
        starElements = new LinearDissipator[branches];
        starNode = new GeneralNode(PhysicalDomain.ELECTRICAL);
        for (int idx = 0; idx < branches; idx++) {
            starNodes[idx] = new GeneralNode(PhysicalDomain.ELECTRICAL);
            starElements[idx] = new LinearDissipator(PhysicalDomain.ELECTRICAL);
            starElements[idx].connectBetween(starNode, starNodes[idx]);
        }
    }
    
    @Test
    public void fullEqualStar() {
        setUpStar(6);
        StarPolygonTransform instance = new StarPolygonTransform();
        instance.setupStar(starNode);
        instance.calculatePolygonResistorValues();
    }
}
