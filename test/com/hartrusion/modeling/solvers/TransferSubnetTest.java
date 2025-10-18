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
package com.hartrusion.modeling.solvers;

// import static org.testng.Assert.*;
import com.hartrusion.modeling.PhysicalDomain;
import org.testng.annotations.Test;
import com.hartrusion.modeling.general.Capacitance;
import com.hartrusion.modeling.general.EffortSource;
import com.hartrusion.modeling.general.FlowSource;
import com.hartrusion.modeling.general.GeneralNode;
import com.hartrusion.modeling.general.LinearDissipator;
import com.hartrusion.modeling.general.OpenOrigin;
import com.hartrusion.modeling.general.SelfCapacitance;
import static org.testng.Assert.assertEquals;
import static org.testng.AssertJUnit.assertTrue;
import org.testng.annotations.BeforeClass;

/**
 *
 * @author Viktor Alexander Hartung
 */
public class TransferSubnetTest {
    
    @BeforeClass
    public static void setUpClass() throws Exception {
        // Remove all loggers
        java.util.logging.Logger root = java.util.logging.Logger.getLogger("");
        for (java.util.logging.Handler h : root.getHandlers()) {
            root.removeHandler(h);
        }
    }

    public TransferSubnetTest() {
    }

    /**
     * This is the uttermost basic simple thing that the transfer subnet can be
     * supplied with. It makes no sense using the transfer subnet for this, but
     * it represents the total bare minimum that must be able to be solved.
     */
    @Test
    public void testMinimumNetwork() {
        System.out.println("minimumNetwork");
        TransferSubnet instance = new TransferSubnet();

        /*   n1        R         n2
         *    o------XXXXX------o
         *    |                 |
         *    |                 |
         *   (|) U            =====  C
         *    |
         *    |
         *    o n0
         *    |
         *   _|_
         *
         */
        GeneralNode[] n = new GeneralNode[3];
        LinearDissipator r = new LinearDissipator(PhysicalDomain.HYDRAULIC);
        r.setResistanceParameter(120);
        Capacitance c = new SelfCapacitance(PhysicalDomain.HYDRAULIC);
        EffortSource u = new EffortSource(PhysicalDomain.HYDRAULIC);
        OpenOrigin z = new OpenOrigin(PhysicalDomain.HYDRAULIC);

        for (int idx = 0; idx < n.length; idx++) { // init
            n[idx] = new GeneralNode(PhysicalDomain.HYDRAULIC);
            n[idx].setName("n" + idx);
        }

        u.setEffort(5.0);
        c.setInitialEffort(2.0);
        c.setTimeConstant(0.01);

        z.connectTo(n[0]);
        u.connectTo(n[0]);
        u.connectTo(n[1]);
        r.connectTo(n[1]);
        r.connectTo(n[2]);
        c.connectTo(n[2]);

        // Link all ports
        for (GeneralNode p : n) {
            instance.registerNode(p);
        }
        instance.registerElement(z);
        instance.registerElement(u);
        instance.registerElement(r);
        instance.registerElement(c);

        instance.setupTransferSubnet();
        instance.prepareCalculation();
        instance.doCalculation();
    }

    /**
     * Another very simple network with a small part outside the transfer net.
     * This will test basic setup behaviour if there are elements present in
     * the model, but they are not considered to be part of the transfer subnet.
     * It has, however, two origins, so it will trigger the handling of multiple
     * origins.
     */
    @Test
    public void testTwoOriginElements() {
        System.out.println("twoOriginElements");
        TransferSubnet instance = new TransferSubnet();

        /*   n1       R0        n2      R1       n3
         *    o------XXXXX------o------XXXXX------o
         *    |                 |                 |
         *    |                 |                _|_ z[1]
         *   (|) U            =====  C
         *    |
         *    |
         *    o n0
         *    |
         *   _|_ z[0]
         *
         */
        GeneralNode[] n = new GeneralNode[4];
        LinearDissipator[] r = new LinearDissipator[2];
        Capacitance c = new SelfCapacitance(PhysicalDomain.HYDRAULIC);
        EffortSource u = new EffortSource(PhysicalDomain.HYDRAULIC);
        OpenOrigin[] z = new OpenOrigin[2];

        for (int idx = 0; idx < n.length; idx++) { // init
            n[idx] = new GeneralNode(PhysicalDomain.HYDRAULIC);
            n[idx].setName("n" + idx);
        }

        for (int idx = 0; idx < r.length; idx++) { // init
            r[idx] = new LinearDissipator(PhysicalDomain.HYDRAULIC);
            r[idx].setResistanceParameter(120.0 * (double) (idx + 1)); // ???
            r[idx].setName("R" + idx);
        }

        for (int idx = 0; idx < z.length; idx++) { // init
            z[idx] = new OpenOrigin(PhysicalDomain.HYDRAULIC);
            z[idx].setName("Ground-" + idx);
        }

        u.setEffort(5.0);
        c.setInitialEffort(2.0);
        c.setTimeConstant(0.01);

        z[0].connectTo(n[0]);
        u.connectTo(n[0]);
        u.connectTo(n[1]);
        r[0].connectTo(n[1]);
        r[0].connectTo(n[2]);
        c.connectTo(n[2]);
        r[1].connectTo(n[2]);
        r[1].connectTo(n[3]);
        z[1].connectTo(n[3]);

        // Do not register all elements, just until node 1 and leave
        // out r1 and the additional origin.
        instance.registerNode(n[0]);
        instance.registerNode(n[1]);
        instance.registerNode(n[2]);
        instance.registerElement(z[0]);
        instance.registerElement(u);
        instance.registerElement(r[0]);
        instance.registerElement(c);

        instance.setupTransferSubnet();
        instance.prepareCalculation();
        instance.doCalculation();
    }

    /**
     * Sample integration test
     */
    @Test
    public void testTwoPumpsOneTankExample() {
        System.out.println("twoPumpsOneTankExample");
        TransferSubnet instance = new TransferSubnet();

        /* This test will use a part (subnet) of the following network:
                  n6      R4       n7       R5       n8
       ------------o-----XXXXXX-----o-----XXXXXX-----o 
       |           |      15        |       200      | 
       X           X                |                | 
       X R1        X R3             |                |
       X  10       X  50          =====             _|_
       |           |               C1               Z2
       o n2        o n5     
       |           |          This represents a network which will occur if two
      (|) U0      (|) U1      pumps (represented with U0 and U1) will fill up 
       |   15      |   15     a tank (C1) which is also drained (represented by
       o n1        o n4       R5 and Z2.
       |           |          The subnet can consist of all elements except
       X           X          Z2 and R5, as these can be solved by the effort
       X R0        X R2       value which is forced by C1.
       X  10       X  10      For testing, the whole network will be added.
       |           | 
       o n0        o n3
       |           |
      _|_         _|_
      Z0          Z1
        
         */
        GeneralNode[] n;
        LinearDissipator[] r;
        Capacitance c;
        EffortSource[] u;
        OpenOrigin[] z;

        n = new GeneralNode[9]; // allocate
        for (int idx = 0; idx < n.length; idx++) { // init
            n[idx] = new GeneralNode(PhysicalDomain.HYDRAULIC);
            n[idx].setName("n" + idx);
        }
        r = new LinearDissipator[6]; // allocate
        for (int idx = 0; idx < r.length; idx++) { // init
            r[idx] = new LinearDissipator(PhysicalDomain.HYDRAULIC);
            r[idx].setResistanceParameter(100);
            r[idx].setName("R" + idx);
        }
        c = new SelfCapacitance(PhysicalDomain.HYDRAULIC);
        c.setName("C1");
        u = new EffortSource[2];
        u[0] = new EffortSource(PhysicalDomain.HYDRAULIC);
        u[0].setName("U0");
        u[1] = new EffortSource(PhysicalDomain.HYDRAULIC);
        u[1].setName("U1");
        z = new OpenOrigin[3]; // allocate
        for (int idx = 0; idx < z.length; idx++) { // init
            z[idx] = new OpenOrigin(PhysicalDomain.HYDRAULIC);
            z[idx].setName("Z" + idx);
        }

        // add some values
        r[0].setResistanceParameter(10);
        r[1].setResistanceParameter(10);
        r[2].setResistanceParameter(10);
        r[3].setResistanceParameter(50);
        r[4].setResistanceParameter(15);
        r[5].setResistanceParameter(200);
        u[0].setEffort(15);
        u[1].setEffort(15);

        z[0].connectTo(n[0]);
        r[0].connectTo(n[0]);
        r[0].connectTo(n[1]);
        u[0].connectTo(n[1]);
        u[0].connectTo(n[2]);
        r[1].connectTo(n[2]);
        r[1].connectTo(n[6]);

        z[1].connectTo(n[3]);
        r[2].connectTo(n[3]);
        r[2].connectTo(n[4]);
        u[1].connectTo(n[4]);
        u[1].connectTo(n[5]);
        r[3].connectTo(n[5]);
        r[3].connectTo(n[6]);

        r[4].connectTo(n[6]);
        r[4].connectTo(n[7]);
        c.connectTo(n[7]);
        r[5].connectTo(n[7]);
        r[5].connectTo(n[8]);
        z[2].connectTo(n[8]);

        for (int idx = 0; idx < 8; idx++) {
            instance.registerNode(n[idx]);
        }

        instance.registerElement(z[0]);
        instance.registerElement(z[1]);

        instance.registerElement(r[0]);
        instance.registerElement(r[1]);
        instance.registerElement(r[2]);
        instance.registerElement(r[3]);
        instance.registerElement(r[4]);

        instance.registerElement(u[0]);
        instance.registerElement(u[1]);

        instance.registerElement(c);

        instance.setupTransferSubnet();

        instance.prepareCalculation();
        instance.doCalculation();
    }

    /**
     * If no origin is present, the transferSubnet has to generate a ground
     * origin and connect the capacitance boundaries to it. This came up on
     * later use of the solver and was added here to test this scenario.
     */
    @Test
    public void testNoOrigin() {
        System.out.println("noOrigin");
        TransferSubnet instance = new TransferSubnet();

        /*   n0        R         n1
         *    o------XXXXX------o
         *    |                 |
         *    |                 |
         *  =====  C0         =====  C1
         */
        GeneralNode[] n = new GeneralNode[2];
        LinearDissipator r = new LinearDissipator(PhysicalDomain.HYDRAULIC);
        r.setResistanceParameter(120);
        SelfCapacitance[] c = new SelfCapacitance[2];

        for (int idx = 0; idx < n.length; idx++) { // init
            c[idx] = new SelfCapacitance(PhysicalDomain.HYDRAULIC);
            n[idx] = new GeneralNode(PhysicalDomain.HYDRAULIC);
            n[idx].setName("n" + idx);
        }

        c[0].setInitialEffort(2.0);
        c[0].setTimeConstant(0.01);
        c[1].setInitialEffort(5.0);
        c[1].setTimeConstant(0.05);

        c[0].connectTo(n[0]);
        r.connectTo(n[0]);
        r.connectTo(n[1]);
        c[1].connectTo(n[1]);

        // Link all ports
        for (GeneralNode p : n) {
            instance.registerNode(p);
        }
        instance.registerElement(c[0]);
        instance.registerElement(c[1]);
        instance.registerElement(r);

        instance.setupTransferSubnet();
        instance.prepareCalculation();
        instance.doCalculation();

        // The expected result from the single calculation run is
        // I = U/R = (5.0-2.0) / 120 = 0.025
        assertEquals(r.getFlow(), -0.025, 1e-8, "Wrong flow value on R.");
    }

    /**
     * A self capacitance is usually replaced with a source, implying its
     * effort. But if a self capacitance has two or more ports which are part of
     * the network, the solver must merge those and build a proper child network
     * with only one port on the capacitance in the translated network.
     */
    @Test
    public void testTwoCapacitancePorts() {
        System.out.println("twoCapacitancePorts");
        TransferSubnet instance = new TransferSubnet();

        /*    .-------XXXXX-------o  n2
         *    |       R1          |
         * n0 o------XXXXX------o |
         *    |       R0    n1  | |
         *    |                 | |
         *  =====  C0          =====  C1
         */
        GeneralNode[] n = new GeneralNode[3];
        LinearDissipator[] r = new LinearDissipator[2];
        Capacitance[] c = new Capacitance[2];

        for (int idx = 0; idx < n.length; idx++) {
            n[idx] = new GeneralNode(PhysicalDomain.HYDRAULIC);
            n[idx].setName("n" + idx);
        }
        for (int idx = 0; idx < r.length; idx++) {
            r[idx] = new LinearDissipator(PhysicalDomain.HYDRAULIC);
            r[idx].setName("R" + idx);
            c[idx] = new SelfCapacitance(PhysicalDomain.HYDRAULIC);
            c[idx].setName("C" + idx);
        }

        c[0].setInitialEffort(2.0);
        c[0].setTimeConstant(0.01);
        c[1].setInitialEffort(5.0);
        c[1].setTimeConstant(0.05);
        r[0].setResistanceParameter(120.0);
        r[1].setResistanceParameter(240.0);

        c[0].connectTo(n[0]);
        r[0].connectTo(n[0]);
        r[1].connectTo(n[0]);
        c[1].connectTo(n[1]);
        c[1].connectTo(n[2]);
        r[0].connectTo(n[1]);
        r[1].connectTo(n[2]);

        // Link all nodes
        for (GeneralNode p : n) {
            instance.registerNode(p);
        }
        instance.registerElement(c[0]);
        instance.registerElement(c[1]);
        instance.registerElement(r[0]);
        instance.registerElement(r[1]);

        instance.setupTransferSubnet();
        instance.prepareCalculation();
        instance.doCalculation();

        assertEquals(r[0].getFlow(), -0.025, 1e-8, "Wrong flow value on R0.");
        assertEquals(r[1].getFlow(), -0.0125, 1e-8, "Wrong flow value on R1.");
    }

    /**
     * Same as twoCapacitancePorts, but with three ports.
     */
    @Test
    public void testThreeCapacitancePorts() {
        System.out.println("threeCapacitancePorts");
        TransferSubnet instance = new TransferSubnet();

        /*
         *    .--------XXXXX--------o  n3
         *    |         R2       n2 |
         *    .-------XXXXX-------o |
         *    |        R1         | |
         * n0 o------XXXXX------o | |
         *    |       R0    n1  | | |
         *    |                 | | |
         *  =====  C0          =======  C1
         */
        GeneralNode[] n = new GeneralNode[4];
        LinearDissipator[] r = new LinearDissipator[3];
        Capacitance[] c = new Capacitance[2];

        for (int idx = 0; idx < n.length; idx++) {
            n[idx] = new GeneralNode(PhysicalDomain.HYDRAULIC);
            n[idx].setName("n" + idx);
        }
        for (int idx = 0; idx < r.length; idx++) {
            r[idx] = new LinearDissipator(PhysicalDomain.HYDRAULIC);
            r[idx].setName("R" + idx);
        }
        for (int idx = 0; idx < c.length; idx++) {
            c[idx] = new SelfCapacitance(PhysicalDomain.HYDRAULIC);
            c[idx].setName("C" + idx);
        }

        c[0].setInitialEffort(2.0);
        c[0].setTimeConstant(0.01);
        c[1].setInitialEffort(5.0);
        c[1].setTimeConstant(0.05);
        r[0].setResistanceParameter(120.0);
        r[1].setResistanceParameter(240.0);
        r[2].setResistanceParameter(480.0);

        c[0].connectTo(n[0]);
        c[1].connectTo(n[1]);
        c[1].connectTo(n[2]);
        c[1].connectTo(n[3]);
        r[0].connectBetween(n[0], n[1]);
        r[1].connectBetween(n[0], n[2]);
        r[2].connectBetween(n[0], n[3]);

        // Link all nodes
        for (GeneralNode p : n) {
            instance.registerNode(p);
        }
        instance.registerElement(c[0]);
        instance.registerElement(c[1]);
        instance.registerElement(r[0]);
        instance.registerElement(r[1]);
        instance.registerElement(r[2]);

        instance.setupTransferSubnet();
        instance.prepareCalculation();
        instance.doCalculation();

        assertEquals(r[0].getFlow(), -0.025, 1e-8, "Wrong flow value on R0.");
        assertEquals(r[1].getFlow(), -0.0125, 1e-8, "Wrong flow value on R1.");
        assertEquals(r[2].getFlow(), -0.00625, 1e-8, "Wrong flow value on R2.");
    }

    /**
     * This test is a simplified model of the dual main circulation pumps of the
     * rbmk unit, during development, some issues occurred here. The network
     * consists of two loops each connected to one self capacitance with 2 ports
     * each (could be done with one port, but it needs to work with 2 also) and
     * both loops are connected with a resistor. Having a voltage set on one
     * pump should just circulate one loop but at the time of writing something
     * went wrong with the superposition solver that has to deal with this
     * rather strange circuit.
     */
    @Test
    public void testMainCirculationPumps() {
        System.out.println("mainCirculationPumps");
        TransferSubnet instance = new TransferSubnet();
        /*
         *           c0                                           c1
         *        ==========                                   ========== 
         *         |      |                                     |      |
         *         o n10  o n0                                  o n11  o n21
         *         |      |                                     |      |
         *         I      X                                     X      I     
         *         I r8   X r0                                  X r9   I r17  
         *         I      X                                     X      I      
         *         |      | n1                                  |      |    
         *         o n9   o-------                       -------o n12   o n20
         *         |      |      |                       |      |      |
         *         I      X      =                       =      =      I     
         *         I r7   X r1     r2                      r10   r11   I r16  
         *         I      X      =                       =      =      I      
         *         |      |      |                       |      |      |    
         *         o n8   o n2   o n3                    o n13  o n14   o n19 
         *         |      |      |                       |      |      |
         *         I      |      |                       |      |      I
         *         I r6  (|) u0 (|) u1                  (|) u2 (|) u3  I r15
         *         I      |      |                       |      |      I   
         *         |      |      |                       |      |      |
         *         o n7   o n4   o n5                    o n15  o n16  o n18
         *         |      |      |                       |      |      |
         *         X      X      =                       =      =      X
         *         X r5   X r3     r4                      r12    r13  X r14
         *         X      X      =                       =      =      X
         *         |      |      |                       |      |      |
         *         --------------o---XXXXX---o---XXXXX---o--------------
         *                       n6   r18   n22   r19   n17
         */

        GeneralNode[] n = new GeneralNode[23];
        LinearDissipator[] r = new LinearDissipator[20];
        Capacitance[] c = new SelfCapacitance[2];
        EffortSource[] u = new EffortSource[4];

        for (int idx = 0; idx < n.length; idx++) { // init
            n[idx] = new GeneralNode(PhysicalDomain.HYDRAULIC);
            n[idx].setName("n" + idx);
        }
        for (int idx = 0; idx < r.length; idx++) { // init
            r[idx] = new LinearDissipator(PhysicalDomain.HYDRAULIC);
            r[idx].setResistanceParameter(100);
            r[idx].setName("r" + idx);
        }
        for (int idx = 0; idx < c.length; idx++) { // init
            c[idx] = new SelfCapacitance(PhysicalDomain.HYDRAULIC);
            c[idx].setName("c" + idx);
            c[idx].setInitialEffort(10.0);
        }
        for (int idx = 0; idx < u.length; idx++) { // init
            u[idx] = new EffortSource(PhysicalDomain.HYDRAULIC);
            u[idx].setName("u" + idx);
        }
        // Shortcut connections
        r[6].setBridgedConnection();
        r[7].setBridgedConnection();
        r[8].setBridgedConnection();
        r[15].setBridgedConnection();
        r[16].setBridgedConnection();
        r[17].setBridgedConnection();

        u[0].setEffort(15.0);
        u[1].setEffort(0.0);
        u[2].setEffort(0.0);
        u[3].setEffort(0.0);

        // inactive loop
        r[2].setOpenConnection();
        r[4].setOpenConnection();
        r[10].setOpenConnection();
        r[12].setOpenConnection();
        r[11].setOpenConnection();
        r[13].setOpenConnection();

        // Build network 
        // left loop down
        c[0].connectTo(n[0]);
        r[0].connectTo(n[0]);
        r[0].connectTo(n[1]);
        r[1].connectTo(n[1]);
        r[1].connectTo(n[2]);
        r[2].connectTo(n[1]);
        r[2].connectTo(n[3]);
        u[0].connectTo(n[2]);
        u[0].connectTo(n[4]);
        u[1].connectTo(n[3]);
        u[1].connectTo(n[5]);
        r[3].connectTo(n[4]);
        r[3].connectTo(n[6]);
        r[4].connectTo(n[5]);
        r[4].connectTo(n[6]);
        // left loop up
        r[5].connectTo(n[6]);
        r[5].connectTo(n[7]);
        r[6].connectTo(n[7]);
        r[6].connectTo(n[8]);
        r[7].connectTo(n[8]);
        r[7].connectTo(n[9]);
        r[8].connectTo(n[9]);
        r[8].connectTo(n[10]);
        c[0].connectTo(n[10]);

        // right loop down
        c[1].connectTo(n[11]);
        r[9].connectTo(n[11]);
        r[9].connectTo(n[12]);
        r[10].connectTo(n[12]);
        r[10].connectTo(n[13]);
        r[11].connectTo(n[12]);
        r[11].connectTo(n[14]);
        u[2].connectTo(n[13]);
        u[2].connectTo(n[15]);
        u[3].connectTo(n[14]);
        u[3].connectTo(n[16]);
        r[12].connectTo(n[15]);
        r[12].connectTo(n[17]);
        r[13].connectTo(n[16]);
        r[13].connectTo(n[17]);

        // right loop up
        r[14].connectTo(n[17]);
        r[14].connectTo(n[18]);
        r[15].connectTo(n[18]);
        r[15].connectTo(n[19]);
        r[16].connectTo(n[19]);
        r[16].connectTo(n[20]);
        r[17].connectTo(n[20]);
        r[17].connectTo(n[21]);
        c[1].connectTo(n[21]);

        // middle connector
        r[18].connectTo(n[6]);
        r[18].connectTo(n[22]);
        r[19].connectTo(n[17]);
        r[19].connectTo(n[22]);

        // add everything to the transfer solver
        for (GeneralNode n1 : n) {
            instance.registerNode(n1);
        }
        for (LinearDissipator r1 : r) {
            instance.registerElement(r1);
        }
        for (Capacitance c1 : c) {
            instance.registerElement(c1);
        }
        for (EffortSource u1 : u) {
            instance.registerElement(u1);
        }

        instance.setupTransferSubnet();

        // make 2 calculation runs
        instance.prepareCalculation();
        instance.doCalculation();
        assertTrue(instance.isCalculationFinished());

        instance.prepareCalculation();
        instance.doCalculation();
        assertTrue(instance.isCalculationFinished());

        // All effort across the bridged nodes have to be equal:
        assertEquals(n[10].getEffort(), n[7].getEffort(), 1e-12,
                "Different effort value on left bridged ports.");
        assertEquals(n[21].getEffort(), n[18].getEffort(), 1e-12,
                "Different effort value on right bridged ports.");

        // Nodes connected to the capacitances have to have same efforts
        assertEquals(n[10].getEffort(), c[0].getEffort(), 1e-12,
                "Forced effort from capacitance not present");
        assertEquals(n[21].getEffort(), c[1].getEffort(), 1e-12,
                "Forced effort from capacitance not present");
    }

    /**
     * Same as test case mainCirculationPumps but with an additional connection
     * between the two capacitances on top. Representing the steam output valves
     * connected to the drum separators. This didn't work during development of
     * chornobly sim but the test works fine, we will keep it here.
     */
    @Test
    public void testMainCirculationPumpsWithSteamOut() {
        System.out.println("mainCirculationPumpsWithSteamOut");
        TransferSubnet instance = new TransferSubnet();
        /*
         *           c0       n23      r20  n24  r21        n25    c1
         *        ==========--o-------XXXXX--o--XXXXX--------o--========== 
         *         |      |                                     |      |
         *         o n10  o n0                                  o n11  o n21
         *         |      |                                     |      |
         *         I      X                                     X      I     
         *         I r8   X r0                                  X r9   I r17  
         *         I      X                                     X      I      
         *         |      | n1                                  |      |    
         *         o n9   o-------                       -------o n12   o n20
         *         |      |      |                       |      |      |
         *         I      X      =                       =      =      I     
         *         I r7   X r1     r2                      r10   r11   I r16  
         *         I      X      =                       =      =      I      
         *         |      |      |                       |      |      |    
         *         o n8   o n2   o n3                    o n13  o n14   o n19 
         *         |      |      |                       |      |      |
         *         I      |      |                       |      |      I
         *         I r6  (|) u0 (|) u1                  (|) u2 (|) u3  I r15
         *         I      |      |                       |      |      I   
         *         |      |      |                       |      |      |
         *         o n7   o n4   o n5                    o n15  o n16  o n18
         *         |      |      |                       |      |      |
         *         X      X      =                       =      =      X
         *         X r5   X r3     r4                      r12    r13  X r14
         *         X      X      =                       =      =      X
         *         |      |      |                       |      |      |
         *         --------------o---XXXXX---o---XXXXX---o--------------
         *                       n6   r18   n22   r19   n17
         */

        GeneralNode[] n = new GeneralNode[26];
        LinearDissipator[] r = new LinearDissipator[22];
        Capacitance[] c = new SelfCapacitance[2];
        EffortSource[] u = new EffortSource[4];

        for (int idx = 0; idx < n.length; idx++) { // init
            n[idx] = new GeneralNode(PhysicalDomain.HYDRAULIC);
            n[idx].setName("n" + idx);
        }
        for (int idx = 0; idx < r.length; idx++) { // init
            r[idx] = new LinearDissipator(PhysicalDomain.HYDRAULIC);
            r[idx].setResistanceParameter(100);
            r[idx].setName("r" + idx);
        }
        for (int idx = 0; idx < c.length; idx++) { // init
            c[idx] = new SelfCapacitance(PhysicalDomain.HYDRAULIC);
            c[idx].setName("c" + idx);
            c[idx].setInitialEffort(10.0);
        }
        for (int idx = 0; idx < u.length; idx++) { // init
            u[idx] = new EffortSource(PhysicalDomain.HYDRAULIC);
            u[idx].setName("u" + idx);
        }
        // Shortcut connections
        r[6].setBridgedConnection();
        r[7].setBridgedConnection();
        r[8].setBridgedConnection();
        r[15].setBridgedConnection();
        r[16].setBridgedConnection();
        r[17].setBridgedConnection();

        u[0].setEffort(15.0);
        u[1].setEffort(0.0);
        u[2].setEffort(0.0);
        u[3].setEffort(0.0);

        // inactive loop
        r[2].setOpenConnection();
        r[4].setOpenConnection();
        r[10].setOpenConnection();
        r[12].setOpenConnection();
        r[11].setOpenConnection();
        r[13].setOpenConnection();

        // Build network 
        // left loop down
        c[0].connectTo(n[0]);
        r[0].connectTo(n[0]);
        r[0].connectTo(n[1]);
        r[1].connectTo(n[1]);
        r[1].connectTo(n[2]);
        r[2].connectTo(n[1]);
        r[2].connectTo(n[3]);
        u[0].connectTo(n[2]);
        u[0].connectTo(n[4]);
        u[1].connectTo(n[3]);
        u[1].connectTo(n[5]);
        r[3].connectTo(n[4]);
        r[3].connectTo(n[6]);
        r[4].connectTo(n[5]);
        r[4].connectTo(n[6]);
        // left loop up
        r[5].connectTo(n[6]);
        r[5].connectTo(n[7]);
        r[6].connectTo(n[7]);
        r[6].connectTo(n[8]);
        r[7].connectTo(n[8]);
        r[7].connectTo(n[9]);
        r[8].connectTo(n[9]);
        r[8].connectTo(n[10]);
        c[0].connectTo(n[10]);

        // right loop down
        c[1].connectTo(n[11]);
        r[9].connectTo(n[11]);
        r[9].connectTo(n[12]);
        r[10].connectTo(n[12]);
        r[10].connectTo(n[13]);
        r[11].connectTo(n[12]);
        r[11].connectTo(n[14]);
        u[2].connectTo(n[13]);
        u[2].connectTo(n[15]);
        u[3].connectTo(n[14]);
        u[3].connectTo(n[16]);
        r[12].connectTo(n[15]);
        r[12].connectTo(n[17]);
        r[13].connectTo(n[16]);
        r[13].connectTo(n[17]);

        // right loop up
        r[14].connectTo(n[17]);
        r[14].connectTo(n[18]);
        r[15].connectTo(n[18]);
        r[15].connectTo(n[19]);
        r[16].connectTo(n[19]);
        r[16].connectTo(n[20]);
        r[17].connectTo(n[20]);
        r[17].connectTo(n[21]);
        c[1].connectTo(n[21]);

        // middle connector
        r[18].connectTo(n[6]);
        r[18].connectTo(n[22]);
        r[19].connectTo(n[17]);
        r[19].connectTo(n[22]);

        // Top connection
        c[0].connectTo(n[23]);
        c[1].connectTo(n[25]);
        r[20].connectBetween(n[23], n[24]);
        r[21].connectBetween(n[25], n[24]);

        // add everything to the transfer solver
        for (GeneralNode n1 : n) {
            instance.registerNode(n1);
        }
        for (LinearDissipator r1 : r) {
            instance.registerElement(r1);
        }
        for (Capacitance c1 : c) {
            instance.registerElement(c1);
        }
        for (EffortSource u1 : u) {
            instance.registerElement(u1);
        }

        instance.setupTransferSubnet();

        // make 2 calculation runs
        instance.prepareCalculation();
        instance.doCalculation();
        assertTrue(instance.isCalculationFinished());

        instance.prepareCalculation();
        instance.doCalculation();
        assertTrue(instance.isCalculationFinished());

        // All effort across the bridged nodes have to be equal:
        assertEquals(n[10].getEffort(), n[7].getEffort(), 1e-12,
                "Different effort value on left bridged ports.");
        assertEquals(n[21].getEffort(), n[18].getEffort(), 1e-12,
                "Different effort value on right bridged ports.");

        // Nodes connected to the capacitances have to have same efforts
        assertEquals(n[10].getEffort(), c[0].getEffort(), 1e-12,
                "Forced effort from capacitance not present");
        assertEquals(n[21].getEffort(), c[1].getEffort(), 1e-12,
                "Forced effort from capacitance not present");
    }

    /**
     * Another example that comes from the chernobyl model during development.
     * This is the thermal heat distribution model between both reactor sides,
     * and it failed to solve, mostly because during development the flow source
     * was barely used and considered to be kind of optional, which it clearly
     * is not. This lead to the implementation of checkForShortcutsByMerging in
     * the overlay class.
     */
    @Test
    public void testThermalFlowSources() {
        System.out.println("thermalFlowSources");
        TransferSubnet instance = new TransferSubnet();
        /*                         r2
         *    -------------------XXXXX-------------------
         *    |                                         |    
         *    |     r0      n3           n5     r1      |
         * n1 o----XXXXX----o             o----XXXXX----o n7
         *    |             |             |             |
         *    |             |             |             |
         *   (|) u[0]      (-) iq[0]     (-) iq[1]     (|) u[1]
         *    |             |             |             |
         *    |             |             |             |
         *    o n0          o n2          o n4          o n6
         *    |             |             |             |
         *   _|_ z[0]      _|_ z[1]      _|_ z[2]      _|_ z[3]
         *
         */

        OpenOrigin[] z = new OpenOrigin[4];
        GeneralNode[] n = new GeneralNode[8];
        LinearDissipator[] r = new LinearDissipator[3];
        FlowSource[] iq = new FlowSource[2];
        EffortSource[] u = new EffortSource[2];

        for (int idx = 0; idx < z.length; idx++) { // init
            z[idx] = new OpenOrigin(PhysicalDomain.THERMAL);
            z[idx].setName("z" + idx);
        }
        for (int idx = 0; idx < n.length; idx++) { // init
            n[idx] = new GeneralNode(PhysicalDomain.THERMAL);
            n[idx].setName("n" + idx);
        }
        for (int idx = 0; idx < r.length; idx++) { // init
            r[idx] = new LinearDissipator(PhysicalDomain.THERMAL);
            r[idx].setResistanceParameter(100);
            r[idx].setName("r" + idx);
        }
        for (int idx = 0; idx < iq.length; idx++) { // init
            iq[idx] = new FlowSource(PhysicalDomain.THERMAL);
            iq[idx].setName("iq" + idx);;
        }
        for (int idx = 0; idx < u.length; idx++) { // init
            u[idx] = new EffortSource(PhysicalDomain.THERMAL);
            u[idx].setName("u" + idx);
        }

        z[0].connectToVia(u[0], n[0]);
        z[1].connectToVia(iq[0], n[2]);
        z[2].connectToVia(iq[1], n[4]);
        z[3].connectToVia(u[1], n[6]);
        u[0].connectTo(n[1]);
        iq[0].connectTo(n[3]);
        iq[1].connectTo(n[5]);
        u[1].connectTo(n[7]);
        r[0].connectBetween(n[1], n[3]);
        r[1].connectBetween(n[5], n[7]);
        r[2].connectBetween(n[1], n[7]);

        // add everything to the transfer solver
        for (GeneralNode n1 : n) {
            instance.registerNode(n1);
        }
        for (OpenOrigin z1 : z) {
            instance.registerElement(z1);
        }
        for (LinearDissipator r1 : r) {
            instance.registerElement(r1);
        }
        for (FlowSource iq1 : iq) {
            instance.registerElement(iq1);
        }
        for (EffortSource u1 : u) {
            instance.registerElement(u1);
        }

        instance.setupTransferSubnet();

        // make 2 calculation runs
        instance.prepareCalculation();
        instance.doCalculation();
        assertTrue(instance.isCalculationFinished());

        instance.prepareCalculation();
        instance.doCalculation();
        assertTrue(instance.isCalculationFinished());
    }

    /**
     * Small variant of previous test, this time the two flow sources are
     * connected to the same origin.
     */
    @Test
    public void testThermalFlowSourcesSharedOrigin() {
        System.out.println("thermalFlowSourcesSharedOrigin");
        TransferSubnet instance = new TransferSubnet();
        /*                         r2
         *    -------------------XXXXX-------------------
         *    |                                         |    
         *    |     r0      n3           n4     r1      |
         * n1 o----XXXXX----o             o----XXXXX----o n6
         *    |             |             |             |
         *    |             |             |             |
         *   (|) u[0]      (-) iq[0]     (-) iq[1]     (|) u[1]
         *    |             |             |             |
         *    |             |     n2      |             |
         *    o n0          -------o-------             o n5
         *    |                    |                    |
         *   _|_ z[0]             _|_ z[1]             _|_ z[2]
         *
         */

        OpenOrigin[] z = new OpenOrigin[3];
        GeneralNode[] n = new GeneralNode[7];
        LinearDissipator[] r = new LinearDissipator[3];
        FlowSource[] iq = new FlowSource[2];
        EffortSource[] u = new EffortSource[2];

        for (int idx = 0; idx < z.length; idx++) { // init
            z[idx] = new OpenOrigin(PhysicalDomain.THERMAL);
            z[idx].setName("z" + idx);
        }
        for (int idx = 0; idx < n.length; idx++) { // init
            n[idx] = new GeneralNode(PhysicalDomain.THERMAL);
            n[idx].setName("n" + idx);
        }
        for (int idx = 0; idx < r.length; idx++) { // init
            r[idx] = new LinearDissipator(PhysicalDomain.THERMAL);
            r[idx].setResistanceParameter(100);
            r[idx].setName("r" + idx);
        }
        for (int idx = 0; idx < iq.length; idx++) { // init
            iq[idx] = new FlowSource(PhysicalDomain.THERMAL);
            iq[idx].setName("iq" + idx);;
        }
        for (int idx = 0; idx < u.length; idx++) { // init
            u[idx] = new EffortSource(PhysicalDomain.THERMAL);
            u[idx].setName("u" + idx);
        }

        z[0].connectToVia(u[0], n[0]);
        z[1].connectToVia(iq[0], n[2]);
        iq[1].connectTo(n[2]);
        z[2].connectToVia(u[1], n[5]);
        u[0].connectTo(n[1]);
        iq[0].connectTo(n[3]);
        iq[1].connectTo(n[4]);
        u[1].connectTo(n[6]);
        r[0].connectBetween(n[1], n[3]);
        r[1].connectBetween(n[4], n[6]);
        r[2].connectBetween(n[1], n[6]);

        // add everything to the transfer solver
        for (GeneralNode n1 : n) {
            instance.registerNode(n1);
        }
        for (OpenOrigin z1 : z) {
            instance.registerElement(z1);
        }
        for (LinearDissipator r1 : r) {
            instance.registerElement(r1);
        }
        for (FlowSource iq1 : iq) {
            instance.registerElement(iq1);
        }
        for (EffortSource u1 : u) {
            instance.registerElement(u1);
        }

        instance.setupTransferSubnet();

        // make 2 calculation runs
        instance.prepareCalculation();
        instance.doCalculation();
        assertTrue(instance.isCalculationFinished());

        instance.prepareCalculation();
        instance.doCalculation();
        assertTrue(instance.isCalculationFinished());
    }
}
