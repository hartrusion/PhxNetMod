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
package com.hartrusion.modeling.solvers;

import com.hartrusion.modeling.PhysicalDomain;
import com.hartrusion.modeling.general.AbstractElement;
import com.hartrusion.modeling.general.ClosedOrigin;
import com.hartrusion.modeling.general.EffortSource;
import com.hartrusion.modeling.general.FlowSource;
import com.hartrusion.modeling.general.GeneralNode;
import com.hartrusion.modeling.general.LinearDissipator;
import com.hartrusion.modeling.exceptions.WrongSolverException;
import com.hartrusion.util.SimpleLogOut;
import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Specialized tests that trigger the {@link DualEffortCenterFlowSolver} class.
 * <p>
 * Code generated with Github Copilot using Opus 4.8, calculations made on
 * paper for validation outside of this computed world.
 *
 * @author Viktor Alexander Hartung
 */
public class DualEffortCenterFlowSolverTest {

    @BeforeClass
    public static void setUpClass() throws Exception {
        // Keep Log out clean during test run
        SimpleLogOut.configureLoggingWarningsOnly();
    }

    public DualEffortCenterFlowSolverTest() {
    }

    /**
     * Builds the network the {@link DualEffortCenterFlowSolver} was made for
     * and checks the effort on the center node as well as the flows at both
     * ends.
     * <pre>
     *
     *    n1     R1    nI     R2     n2
     *     o---XXXXXX---o---XXXXXX---o
     *     |            |            |
     *     |            |            |
     *    (|) U1       (-) Iq       (I) U2
     *     |            |            |
     *     |            |            |
     *     -------------o-------------
     *                  |  nGnd
     *                 _|_ gnd
     *
     * </pre>
     */
    @Test
    public void testStandardBehaviour() {
        LinearDissipator r1, r2;
        EffortSource u1, u2;
        FlowSource iQ;
        ClosedOrigin gnd;
        GeneralNode nGnd, n1, nI, n2;

        // Resistors from the outer effort sources towards the center node.
        r1 = new LinearDissipator(PhysicalDomain.ELECTRICAL);
        r1.setName("R1");
        r1.setResistanceParameter(470);
        r2 = new LinearDissipator(PhysicalDomain.ELECTRICAL);
        r2.setName("R2");
        r2.setResistanceParameter(680);

        // The two grounded effort sources forcing the outer node efforts.
        u1 = new EffortSource(PhysicalDomain.ELECTRICAL);
        u1.setName("U1");
        u1.setEffort(10);
        u2 = new EffortSource(PhysicalDomain.ELECTRICAL);
        u2.setName("U2");
        u2.setEffort(4);

        // The center current source injecting flow into the center node.
        iQ = new FlowSource(PhysicalDomain.ELECTRICAL);
        iQ.setName("Iq");
        iQ.setFlow(0.01);

        // Ground.
        gnd = new ClosedOrigin(PhysicalDomain.ELECTRICAL);
        gnd.setName("Zero");

        // Nodes.
        nGnd = new GeneralNode(PhysicalDomain.ELECTRICAL);
        nGnd.setName("nGnd");
        n1 = new GeneralNode(PhysicalDomain.ELECTRICAL);
        n1.setName("n1");
        nI = new GeneralNode(PhysicalDomain.ELECTRICAL);
        nI.setName("nI");
        n2 = new GeneralNode(PhysicalDomain.ELECTRICAL);
        n2.setName("n2");

        // Wire everything together. The connection order defines node index 0
        // and index 1 on each element, which the solver uses for direction.
        gnd.connectTo(nGnd);
        u1.connectTo(nGnd);
        u1.connectTo(n1);
        u2.connectTo(nGnd);
        u2.connectTo(n2);
        iQ.connectTo(nGnd);
        iQ.connectTo(nI);
        r1.connectTo(n1);
        r1.connectTo(nI);
        r2.connectTo(n2);
        r2.connectTo(nI);

        // Collect nodes and elements for the dedicated solver.
        List<GeneralNode> nodes = new ArrayList<>();
        nodes.add(nGnd);
        nodes.add(n1);
        nodes.add(nI);
        nodes.add(n2);
        List<AbstractElement> elements = new ArrayList<>();
        elements.add(gnd);
        elements.add(u1);
        elements.add(u2);
        elements.add(iQ);
        elements.add(r1);
        elements.add(r2);

        // Construct and run the dedicated solver. Constructing it validates that
        // the topology matches the network this solver was made for.
        DualEffortCenterFlowSolver solver = null;
        try {
            solver = new DualEffortCenterFlowSolver(nodes, elements);
        } catch (WrongSolverException ex) {
            fail("Solver does not accept circuit");
        }

        // Reset all node/element state before solving.
        for (AbstractElement e : elements) {
            e.prepareCalculation();
        }

        solver.solveNetwork();

        // calculation must be finished
        assertEquals(u1.isCalculationFinished(), true);
        assertEquals(u2.isCalculationFinished(), true);
        assertEquals(r1.isCalculationFinished(), true);
        assertEquals(r2.isCalculationFinished(), true);
        assertEquals(iQ.isCalculationFinished(), true);

        // Effort on the center node. Fill in the hand-calculated value.
        assertEquals(nI.getEffort(), 10.32665, 1e-3);

        // Flows at both ends (through the effort sources). Fill in the
        // hand-calculated values.
        assertEquals(u1.getFlow(), -0.000695, 1e-6);
        assertEquals(u2.getFlow(), -0.009305, 1e-6);
    }
    
    @Test
    public void testR1Reversed() {
        LinearDissipator r1, r2;
        EffortSource u1, u2;
        FlowSource iQ;
        ClosedOrigin gnd;
        GeneralNode nGnd, n1, nI, n2;

        // Resistors from the outer effort sources towards the center node.
        r1 = new LinearDissipator(PhysicalDomain.ELECTRICAL);
        r1.setName("R1");
        r1.setResistanceParameter(470);
        r2 = new LinearDissipator(PhysicalDomain.ELECTRICAL);
        r2.setName("R2");
        r2.setResistanceParameter(680);

        // The two grounded effort sources forcing the outer node efforts.
        u1 = new EffortSource(PhysicalDomain.ELECTRICAL);
        u1.setName("U1");
        u1.setEffort(10);
        u2 = new EffortSource(PhysicalDomain.ELECTRICAL);
        u2.setName("U2");
        u2.setEffort(4);

        // The center current source injecting flow into the center node.
        iQ = new FlowSource(PhysicalDomain.ELECTRICAL);
        iQ.setName("Iq");
        iQ.setFlow(0.01);

        // Ground.
        gnd = new ClosedOrigin(PhysicalDomain.ELECTRICAL);
        gnd.setName("Zero");

        // Nodes.
        nGnd = new GeneralNode(PhysicalDomain.ELECTRICAL);
        nGnd.setName("nGnd");
        n1 = new GeneralNode(PhysicalDomain.ELECTRICAL);
        n1.setName("n1");
        nI = new GeneralNode(PhysicalDomain.ELECTRICAL);
        nI.setName("nI");
        n2 = new GeneralNode(PhysicalDomain.ELECTRICAL);
        n2.setName("n2");

        // Wire everything together. The connection order defines node index 0
        // and index 1 on each element, which the solver uses for direction.
        gnd.connectTo(nGnd);
        u1.connectTo(nGnd);
        u1.connectTo(n1);
        u2.connectTo(nGnd);
        u2.connectTo(n2);
        iQ.connectTo(nGnd);
        iQ.connectTo(nI);
        r1.connectTo(nI); // here's the difference
        r1.connectTo(n1);
        r2.connectTo(n2);
        r2.connectTo(nI);

        // Collect nodes and elements for the dedicated solver.
        List<GeneralNode> nodes = new ArrayList<>();
        nodes.add(nGnd);
        nodes.add(n1);
        nodes.add(nI);
        nodes.add(n2);
        List<AbstractElement> elements = new ArrayList<>();
        elements.add(gnd);
        elements.add(u1);
        elements.add(u2);
        elements.add(iQ);
        elements.add(r1);
        elements.add(r2);

        // Construct and run the dedicated solver. Constructing it validates that
        // the topology matches the network this solver was made for.
        DualEffortCenterFlowSolver solver = null;
        try {
            solver = new DualEffortCenterFlowSolver(nodes, elements);
        } catch (WrongSolverException ex) {
            fail("Solver does not accept circuit");
        }

        // Reset all node/element state before solving.
        for (AbstractElement e : elements) {
            e.prepareCalculation();
        }

        solver.solveNetwork();

        // calculation must be finished
        assertEquals(u1.isCalculationFinished(), true);
        assertEquals(u2.isCalculationFinished(), true);
        assertEquals(r1.isCalculationFinished(), true);
        assertEquals(r2.isCalculationFinished(), true);
        assertEquals(iQ.isCalculationFinished(), true);

        // Effort on the center node. Fill in the hand-calculated value.
        assertEquals(nI.getEffort(), 10.32665, 1e-3);

        // Flows at both ends (through the effort sources). Fill in the
        // hand-calculated values.
        assertEquals(u1.getFlow(), -0.000695, 1e-6);
        assertEquals(u2.getFlow(), -0.009305, 1e-6);
    }
    
    @Test
    public void testR2Reversed() {
        LinearDissipator r1, r2;
        EffortSource u1, u2;
        FlowSource iQ;
        ClosedOrigin gnd;
        GeneralNode nGnd, n1, nI, n2;

        // Resistors from the outer effort sources towards the center node.
        r1 = new LinearDissipator(PhysicalDomain.ELECTRICAL);
        r1.setName("R1");
        r1.setResistanceParameter(470);
        r2 = new LinearDissipator(PhysicalDomain.ELECTRICAL);
        r2.setName("R2");
        r2.setResistanceParameter(680);

        // The two grounded effort sources forcing the outer node efforts.
        u1 = new EffortSource(PhysicalDomain.ELECTRICAL);
        u1.setName("U1");
        u1.setEffort(10);
        u2 = new EffortSource(PhysicalDomain.ELECTRICAL);
        u2.setName("U2");
        u2.setEffort(4);

        // The center current source injecting flow into the center node.
        iQ = new FlowSource(PhysicalDomain.ELECTRICAL);
        iQ.setName("Iq");
        iQ.setFlow(0.01);

        // Ground.
        gnd = new ClosedOrigin(PhysicalDomain.ELECTRICAL);
        gnd.setName("Zero");

        // Nodes.
        nGnd = new GeneralNode(PhysicalDomain.ELECTRICAL);
        nGnd.setName("nGnd");
        n1 = new GeneralNode(PhysicalDomain.ELECTRICAL);
        n1.setName("n1");
        nI = new GeneralNode(PhysicalDomain.ELECTRICAL);
        nI.setName("nI");
        n2 = new GeneralNode(PhysicalDomain.ELECTRICAL);
        n2.setName("n2");

        // Wire everything together. The connection order defines node index 0
        // and index 1 on each element, which the solver uses for direction.
        gnd.connectTo(nGnd);
        u1.connectTo(nGnd);
        u1.connectTo(n1);
        u2.connectTo(nGnd);
        u2.connectTo(n2);
        iQ.connectTo(nGnd);
        iQ.connectTo(nI);
        r1.connectTo(n1);
        r1.connectTo(nI);
        r2.connectTo(nI); // this one reversed
        r2.connectTo(n2);
        
        // Collect nodes and elements for the dedicated solver.
        List<GeneralNode> nodes = new ArrayList<>();
        nodes.add(nGnd);
        nodes.add(n1);
        nodes.add(nI);
        nodes.add(n2);
        List<AbstractElement> elements = new ArrayList<>();
        elements.add(gnd);
        elements.add(u1);
        elements.add(u2);
        elements.add(iQ);
        elements.add(r1);
        elements.add(r2);

        // Construct and run the dedicated solver. Constructing it validates that
        // the topology matches the network this solver was made for.
        DualEffortCenterFlowSolver solver = null;
        try {
            solver = new DualEffortCenterFlowSolver(nodes, elements);
        } catch (WrongSolverException ex) {
            fail("Solver does not accept circuit");
        }

        // Reset all node/element state before solving.
        for (AbstractElement e : elements) {
            e.prepareCalculation();
        }

        solver.solveNetwork();

        // calculation must be finished
        assertEquals(u1.isCalculationFinished(), true);
        assertEquals(u2.isCalculationFinished(), true);
        assertEquals(r1.isCalculationFinished(), true);
        assertEquals(r2.isCalculationFinished(), true);
        assertEquals(iQ.isCalculationFinished(), true);

        // Effort on the center node. Fill in the hand-calculated value.
        assertEquals(nI.getEffort(), 10.32665, 1e-3);

        // Flows at both ends (through the effort sources). Fill in the
        // hand-calculated values.
        assertEquals(u1.getFlow(), -0.000695, 1e-6);
        assertEquals(u2.getFlow(), -0.009305, 1e-6);
    }
    
    @Test
    public void testUReversed() {
        LinearDissipator r1, r2;
        EffortSource u1, u2;
        FlowSource iQ;
        ClosedOrigin gnd;
        GeneralNode nGnd, n1, nI, n2;

        // Resistors from the outer effort sources towards the center node.
        r1 = new LinearDissipator(PhysicalDomain.ELECTRICAL);
        r1.setName("R1");
        r1.setResistanceParameter(470);
        r2 = new LinearDissipator(PhysicalDomain.ELECTRICAL);
        r2.setName("R2");
        r2.setResistanceParameter(680);

        // The two grounded effort sources forcing the outer node efforts.
        u1 = new EffortSource(PhysicalDomain.ELECTRICAL);
        u1.setName("U1");
        u1.setEffort(-10);
        u2 = new EffortSource(PhysicalDomain.ELECTRICAL);
        u2.setName("U2");
        u2.setEffort(4);

        // The center current source injecting flow into the center node.
        iQ = new FlowSource(PhysicalDomain.ELECTRICAL);
        iQ.setName("Iq");
        iQ.setFlow(0.01);

        // Ground.
        gnd = new ClosedOrigin(PhysicalDomain.ELECTRICAL);
        gnd.setName("Zero");

        // Nodes.
        nGnd = new GeneralNode(PhysicalDomain.ELECTRICAL);
        nGnd.setName("nGnd");
        n1 = new GeneralNode(PhysicalDomain.ELECTRICAL);
        n1.setName("n1");
        nI = new GeneralNode(PhysicalDomain.ELECTRICAL);
        nI.setName("nI");
        n2 = new GeneralNode(PhysicalDomain.ELECTRICAL);
        n2.setName("n2");

        // Wire everything together. The connection order defines node index 0
        // and index 1 on each element, which the solver uses for direction.
        gnd.connectTo(nGnd);
        u1.connectTo(n1);
        u1.connectTo(nGnd);
        u2.connectTo(nGnd);
        u2.connectTo(n2);
        iQ.connectTo(nGnd);
        iQ.connectTo(nI);
        r1.connectTo(n1);
        r1.connectTo(nI);
        r2.connectTo(n2);
        r2.connectTo(nI);

        // Collect nodes and elements for the dedicated solver.
        List<GeneralNode> nodes = new ArrayList<>();
        nodes.add(nGnd);
        nodes.add(n1);
        nodes.add(nI);
        nodes.add(n2);
        List<AbstractElement> elements = new ArrayList<>();
        elements.add(gnd);
        elements.add(u1);
        elements.add(u2);
        elements.add(iQ);
        elements.add(r1);
        elements.add(r2);

        // Construct and run the dedicated solver. Constructing it validates that
        // the topology matches the network this solver was made for.
        DualEffortCenterFlowSolver solver = null;
        try {
            solver = new DualEffortCenterFlowSolver(nodes, elements);
        } catch (WrongSolverException ex) {
            fail("Solver does not accept circuit");
        }

        // Reset all node/element state before solving.
        for (AbstractElement e : elements) {
            e.prepareCalculation();
        }

        solver.solveNetwork();

        // calculation must be finished
        assertEquals(u1.isCalculationFinished(), true);
        assertEquals(u2.isCalculationFinished(), true);
        assertEquals(r1.isCalculationFinished(), true);
        assertEquals(r2.isCalculationFinished(), true);
        assertEquals(iQ.isCalculationFinished(), true);

        // Effort on the center node. Fill in the hand-calculated value.
        assertEquals(nI.getEffort(), 10.32665, 1e-3);

        // Flows at both ends (through the effort sources). Fill in the
        // hand-calculated values.
        assertEquals(u1.getFlow(), 0.000695, 1e-6);
        assertEquals(u2.getFlow(), -0.009305, 1e-6);
    }
    
    @Test
    public void testIReversed() {
        LinearDissipator r1, r2;
        EffortSource u1, u2;
        FlowSource iQ;
        ClosedOrigin gnd;
        GeneralNode nGnd, n1, nI, n2;

        // Resistors from the outer effort sources towards the center node.
        r1 = new LinearDissipator(PhysicalDomain.ELECTRICAL);
        r1.setName("R1");
        r1.setResistanceParameter(470);
        r2 = new LinearDissipator(PhysicalDomain.ELECTRICAL);
        r2.setName("R2");
        r2.setResistanceParameter(680);

        // The two grounded effort sources forcing the outer node efforts.
        u1 = new EffortSource(PhysicalDomain.ELECTRICAL);
        u1.setName("U1");
        u1.setEffort(-10);
        u2 = new EffortSource(PhysicalDomain.ELECTRICAL);
        u2.setName("U2");
        u2.setEffort(4);

        // The center current source injecting flow into the center node.
        iQ = new FlowSource(PhysicalDomain.ELECTRICAL);
        iQ.setName("Iq");
        iQ.setFlow(-0.01);

        // Ground.
        gnd = new ClosedOrigin(PhysicalDomain.ELECTRICAL);
        gnd.setName("Zero");

        // Nodes.
        nGnd = new GeneralNode(PhysicalDomain.ELECTRICAL);
        nGnd.setName("nGnd");
        n1 = new GeneralNode(PhysicalDomain.ELECTRICAL);
        n1.setName("n1");
        nI = new GeneralNode(PhysicalDomain.ELECTRICAL);
        nI.setName("nI");
        n2 = new GeneralNode(PhysicalDomain.ELECTRICAL);
        n2.setName("n2");

        // Wire everything together. The connection order defines node index 0
        // and index 1 on each element, which the solver uses for direction.
        gnd.connectTo(nGnd);
        u1.connectTo(n1);
        u1.connectTo(nGnd);
        u2.connectTo(nGnd);
        u2.connectTo(n2);
        iQ.connectTo(nI); // this one is connected in reverse order
        iQ.connectTo(nGnd);
        r1.connectTo(n1);
        r1.connectTo(nI);
        r2.connectTo(n2);
        r2.connectTo(nI);

        // Collect nodes and elements for the dedicated solver.
        List<GeneralNode> nodes = new ArrayList<>();
        nodes.add(nGnd);
        nodes.add(n1);
        nodes.add(nI);
        nodes.add(n2);
        List<AbstractElement> elements = new ArrayList<>();
        elements.add(gnd);
        elements.add(u1);
        elements.add(u2);
        elements.add(iQ);
        elements.add(r1);
        elements.add(r2);

        // Construct and run the dedicated solver. Constructing it validates that
        // the topology matches the network this solver was made for.
        DualEffortCenterFlowSolver solver = null;
        try {
            solver = new DualEffortCenterFlowSolver(nodes, elements);
        } catch (WrongSolverException ex) {
            fail("Solver does not accept circuit");
        }

        // Reset all node/element state before solving.
        for (AbstractElement e : elements) {
            e.prepareCalculation();
        }

        solver.solveNetwork();

        // calculation must be finished
        assertEquals(u1.isCalculationFinished(), true);
        assertEquals(u2.isCalculationFinished(), true);
        assertEquals(r1.isCalculationFinished(), true);
        assertEquals(r2.isCalculationFinished(), true);
        assertEquals(iQ.isCalculationFinished(), true);

        // Effort on the center node. Fill in the hand-calculated value.
        assertEquals(nI.getEffort(), 10.32665, 1e-3);

        // Flows at both ends (through the effort sources). Fill in the
        // hand-calculated values.
        assertEquals(u1.getFlow(), 0.000695, 1e-6);
        assertEquals(u2.getFlow(), -0.009305, 1e-6);
    }
}
