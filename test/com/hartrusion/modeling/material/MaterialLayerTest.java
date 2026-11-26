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
package com.hartrusion.modeling.material;

import com.hartrusion.modeling.PhysicalDomain;
import com.hartrusion.modeling.assemblies.HeatExchanger;
import com.hartrusion.modeling.exceptions.ModelErrorException;
import com.hartrusion.modeling.general.FlowSource;
import com.hartrusion.modeling.general.GeneralNode;
import com.hartrusion.modeling.general.LinearDissipator;
import com.hartrusion.modeling.general.OpenOrigin;
import com.hartrusion.modeling.general.SelfCapacitance;
import com.hartrusion.modeling.heatfluid.HeatFlowSource;
import com.hartrusion.modeling.heatfluid.HeatNode;
import com.hartrusion.modeling.heatfluid.HeatOrigin;
import com.hartrusion.modeling.heatfluid.HeatVolumizedFlowResistance;
import com.hartrusion.modeling.phasedfluid.PhasedClosedSteamedReservoir;
import com.hartrusion.modeling.phasedfluid.PhasedNode;
import com.hartrusion.modeling.phasedfluid.Water;
import com.hartrusion.modeling.solvers.DomainAnalogySolver;
import com.hartrusion.util.SimpleLogOut;
import static org.testng.Assert.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Tests the transport of material concentrations that are added with the
 * MaterialLayer to a hydraulic network made of general elements, which is
 * solved by the DomainAnalogySolver.
 *
 * <p>
 * The network has two origins with different concentrations whose flows are
 * driven by the flow sources fA and fB, meet in the node nMix and leave the
 * network through a common resistor:
 * <pre>
 *   srcA --nA-- fA --+
 *                    nMix -- rOut -- nOut -- sink
 *   srcB --nB-- fB --+
 * </pre>
 */
public class MaterialLayerTest {

    private static final String SALT = "salt";

    private OpenOrigin srcA, srcB, sink;
    private FlowSource fA, fB;
    private LinearDissipator rOut;
    private GeneralNode nA, nB, nMix, nOut;
    private DomainAnalogySolver solver;

    @BeforeClass
    public static void setUpClass() throws Exception {
        // Keep Log out clean during test run
        SimpleLogOut.configureLoggingWarningsOnly();
    }

    @BeforeMethod
    public void setUpMethod() throws Exception {
        srcA = new OpenOrigin(PhysicalDomain.HYDRAULIC);
        srcB = new OpenOrigin(PhysicalDomain.HYDRAULIC);
        sink = new OpenOrigin(PhysicalDomain.HYDRAULIC);
        fA = new FlowSource(PhysicalDomain.HYDRAULIC);
        fB = new FlowSource(PhysicalDomain.HYDRAULIC);
        rOut = new LinearDissipator(PhysicalDomain.HYDRAULIC);
        nA = new GeneralNode(PhysicalDomain.HYDRAULIC);
        nB = new GeneralNode(PhysicalDomain.HYDRAULIC);
        nMix = new GeneralNode(PhysicalDomain.HYDRAULIC);
        nOut = new GeneralNode(PhysicalDomain.HYDRAULIC);
        srcA.setName("srcA");
        srcB.setName("srcB");
        sink.setName("sink");
        fA.setName("fA");
        fB.setName("fB");
        rOut.setName("rOut");

        srcA.connectToVia(fA, nA);
        fA.connectTo(nMix);
        srcB.connectToVia(fB, nB);
        fB.connectTo(nMix);
        rOut.connectBetween(nMix, nOut);
        sink.connectTo(nOut);

        rOut.setResistanceParameter(1.0);
        fA.setFlow(1.0);
        fB.setFlow(3.0);

        MaterialLayer.add(SALT, nOut);
        MaterialLayer.setOriginConcentration(srcA, SALT, 2.0);
        MaterialLayer.setOriginConcentration(srcB, SALT, 8.0);

        solver = new DomainAnalogySolver();
        solver.addNetwork(nA);
    }

    private void runCycle() {
        solver.prepareCalculation();
        solver.doCalculation();
    }

    /**
     * The search assigns the material types according to the elements: origins
     * get fixed concentrations, sources pass them through.
     */
    @Test
    public void testAssignedTypes() {
        assertTrue(srcA.getMaterialElement() instanceof MaterialOrigin);
        assertTrue(sink.getMaterialElement() instanceof MaterialOrigin);
        assertTrue(fA.getMaterialElement() instanceof MaterialElementNoMass);
        assertTrue(rOut.getMaterialElement() instanceof MaterialElementNoMass);
        assertNotNull(nMix.getMaterialNode());
    }

    /**
     * A flow of 1.0 with 2.0 and a flow of 3.0 with 8.0 mix to 6.5 in nMix,
     * which is passed through rOut to nOut, while nA only carries the
     * concentration of srcA.
     */
    @Test
    public void testMixing() {
        runCycle();
        assertEquals(MaterialLayer.getConcentration(nA, SALT), 2.0, 1e-9);
        assertEquals(MaterialLayer.getConcentration(nB, SALT), 8.0, 1e-9);
        assertEquals(MaterialLayer.getConcentration(nMix, SALT), 6.5, 1e-9);
        assertEquals(MaterialLayer.getConcentration(nOut, SALT), 6.5, 1e-9);
        assertTrue(rOut.isMaterialCalculationFinished());
        assertTrue(sink.isMaterialCalculationFinished());
    }

    /**
     * Without flow through fB, only srcA defines the mixture and nB keeps its
     * last concentration instead of failing.
     */
    @Test
    public void testClosedConnectionKeepsLastValue() {
        runCycle();
        fB.setFlow(0.0);
        runCycle();
        assertEquals(nB.getFlow(fB), 0.0, 0.0);
        assertEquals(MaterialLayer.getConcentration(nB, SALT), 8.0, 1e-9);
        assertEquals(MaterialLayer.getConcentration(nMix, SALT), 2.0, 1e-9);
        assertEquals(MaterialLayer.getConcentration(nOut, SALT), 2.0, 1e-9);
        assertTrue(fB.isMaterialCalculationFinished());
    }

    /**
     * Adding the same material again does not duplicate it, a second material
     * extends the arrays and keeps the values of the first one.
     */
    @Test
    public void testAddSecondMaterial() {
        MaterialLayer.add(SALT, nA);
        assertEquals(nMix.getMaterialNode().getMaterialCount(), 1);
        MaterialLayer.add("iron", nMix);
        assertEquals(nMix.getMaterialNode().getMaterialCount(), 2);
        assertEquals(srcA.getMaterialElement().getMaterialCount(), 2);
        MaterialLayer.setOriginConcentration(srcA, "iron", 1.0);
        MaterialLayer.setOriginConcentration(srcB, "iron", 3.0);
        runCycle();
        assertEquals(MaterialLayer.getConcentration(nOut, SALT), 6.5, 1e-9);
        assertEquals(MaterialLayer.getConcentration(nOut, "iron"), 2.5, 1e-9);
    }

    /**
     * A tank mixes the inflow with its content according to its mass, while a
     * mass of zero or below keeps the concentration constant.
     */
    @Test
    public void testMassElement() {
        OpenOrigin src = new OpenOrigin(PhysicalDomain.HYDRAULIC);
        LinearDissipator r = new LinearDissipator(PhysicalDomain.HYDRAULIC);
        SelfCapacitance tank = new SelfCapacitance(PhysicalDomain.HYDRAULIC);
        GeneralNode n1 = new GeneralNode(PhysicalDomain.HYDRAULIC);
        GeneralNode n2 = new GeneralNode(PhysicalDomain.HYDRAULIC);
        src.connectTo(n1);
        r.connectBetween(n1, n2);
        tank.connectTo(n2);
        src.setEffort(10.0);
        r.setResistanceParameter(1.0);
        tank.setTimeConstant(1000.0);
        tank.setStepTime(1.0);
        tank.setInitialEffort(0.0);

        MaterialLayer.add(SALT, n1);
        MaterialLayer.setStepTime(n1, 1.0);
        MaterialLayer.setOriginConcentration(src, SALT, 4.0);
        MaterialLayer.setInitialConcentration(tank, SALT, 1.0);
        MaterialElementMass tankMaterial
                = (MaterialElementMass) tank.getMaterialElement();
        DomainAnalogySolver tankSolver = new DomainAnalogySolver();
        tankSolver.addNetwork(n1);

        // Mass not set: the concentration stays constant.
        tankSolver.prepareCalculation();
        tankSolver.doCalculation();
        tankSolver.prepareCalculation();
        assertEquals(tankMaterial.getConcentration(SALT), 1.0, 1e-12);

        tankMaterial.setMass(-5.0);
        tankSolver.doCalculation();
        tankSolver.prepareCalculation();
        assertEquals(tankMaterial.getConcentration(SALT), 1.0, 1e-12);

        // With a mass, one step mixes the inflow amount with the content.
        tankMaterial.setMass(100.0);
        tankSolver.doCalculation();
        double inflow = n2.getFlow(tank);
        assertTrue(inflow > 0.0);
        tankSolver.prepareCalculation();
        double expected = (100.0 * 1.0 + inflow * 4.0) / (100.0 + inflow);
        assertEquals(tankMaterial.getConcentration(SALT), expected, 1e-9);
    }

    /**
     * A material manipulation on an element without mass is mixed instantly
     * into the real inflow: 4.0 with 6.5 and a virtual flow of 4.0 with 0.0
     * result in 3.25 after rOut, while nMix is not affected. Switching the
     * manipulation off restores the plain transport.
     */
    @Test
    public void testManipulationWithoutMass() {
        MaterialElement outMaterial = rOut.getMaterialElement();
        outMaterial.setManipulationFlow(SALT, 4.0);
        outMaterial.setManipulationTarget(SALT, 0.0);
        outMaterial.setMaterialManipulation(SALT, true);
        runCycle();
        assertEquals(MaterialLayer.getConcentration(nMix, SALT), 6.5, 1e-9);
        assertEquals(MaterialLayer.getConcentration(nOut, SALT), 3.25, 1e-9);
        outMaterial.setMaterialManipulation(SALT, false);
        runCycle();
        assertEquals(MaterialLayer.getConcentration(nOut, SALT), 6.5, 1e-9);
    }

    /**
     * A material added later starts with a switched off manipulation, while
     * the settings of the existing material are kept.
     */
    @Test
    public void testManipulationOfAddedMaterial() {
        MaterialElement outMaterial = rOut.getMaterialElement();
        outMaterial.setManipulationFlow(SALT, 4.0);
        outMaterial.setManipulationTarget(SALT, 1.0);
        outMaterial.setMaterialManipulation(SALT, true);
        MaterialLayer.add("iron", nA);
        assertFalse(outMaterial.isMaterialManipulation("iron"));
        assertEquals(outMaterial.getManipulationFlow("iron"), 0.0, 0.0);
        assertTrue(outMaterial.isMaterialManipulation(SALT));
        assertEquals(outMaterial.getManipulationFlow(SALT), 4.0, 0.0);
        assertEquals(outMaterial.getManipulationTarget(SALT), 1.0, 0.0);
    }

    /**
     * An origin defines a fixed concentration, so a manipulation can not be
     * switched on there, while switching it off is accepted.
     */
    @Test
    public void testManipulationOnOriginRejected() {
        srcA.getMaterialElement().setMaterialManipulation(SALT, false);
        assertThrows(ModelErrorException.class, ()
                -> srcA.getMaterialElement().setMaterialManipulation(SALT,
                        true));
    }

    /**
     * A tank without any flow approaches the target concentration of an
     * active manipulation with each step, keeps its concentration while the
     * manipulation is switched off and continues when it is switched on
     * again. With a flow, the virtual flow is added to the real inflow.
     */
    @Test
    public void testManipulationWithMass() {
        OpenOrigin src = new OpenOrigin(PhysicalDomain.HYDRAULIC);
        LinearDissipator r = new LinearDissipator(PhysicalDomain.HYDRAULIC);
        SelfCapacitance tank = new SelfCapacitance(PhysicalDomain.HYDRAULIC);
        GeneralNode n1 = new GeneralNode(PhysicalDomain.HYDRAULIC);
        GeneralNode n2 = new GeneralNode(PhysicalDomain.HYDRAULIC);
        src.connectTo(n1);
        r.connectBetween(n1, n2);
        tank.connectTo(n2);
        // Same effort on both sides, so there is no flow.
        src.setEffort(0.0);
        r.setResistanceParameter(1.0);
        tank.setTimeConstant(1000.0);
        tank.setStepTime(1.0);
        tank.setInitialEffort(0.0);

        MaterialLayer.add(SALT, n1);
        MaterialLayer.setStepTime(n1, 1.0);
        MaterialLayer.setOriginConcentration(src, SALT, 4.0);
        MaterialLayer.setInitialConcentration(tank, SALT, 1.0);
        MaterialElementMass tankMaterial
                = (MaterialElementMass) tank.getMaterialElement();
        tankMaterial.setMass(100.0);
        tankMaterial.setManipulationFlow(SALT, 10.0);
        tankMaterial.setManipulationTarget(SALT, 0.0);
        tankMaterial.setMaterialManipulation(SALT, true);
        DomainAnalogySolver tankSolver = new DomainAnalogySolver();
        tankSolver.addNetwork(n1);

        double expected = 100.0 / 110.0;
        tankSolver.prepareCalculation();
        tankSolver.doCalculation();
        assertEquals(n2.getFlow(tank), 0.0, 0.0);
        tankSolver.prepareCalculation();
        assertEquals(tankMaterial.getConcentration(SALT), expected, 1e-12);

        tankMaterial.setMaterialManipulation(SALT, false);
        tankSolver.doCalculation();
        tankSolver.prepareCalculation();
        assertEquals(tankMaterial.getConcentration(SALT), expected, 1e-12);

        tankMaterial.setMaterialManipulation(SALT, true);
        tankSolver.doCalculation();
        tankSolver.prepareCalculation();
        expected = expected * 100.0 / 110.0;
        assertEquals(tankMaterial.getConcentration(SALT), expected, 1e-12);

        // With a flow into the tank, the virtual flow adds to the inflow.
        src.setEffort(10.0);
        double start = tankMaterial.getConcentration(SALT);
        tankSolver.prepareCalculation();
        tankSolver.doCalculation();
        double inflow = n2.getFlow(tank);
        assertTrue(inflow > 0.0);
        tankSolver.prepareCalculation();
        expected = (100.0 * start + inflow * 4.0 + 10.0 * 0.0)
                / (100.0 + inflow + 10.0);
        assertEquals(tankMaterial.getConcentration(SALT), expected, 1e-9);
    }

    /**
     * The steam drum gets a splitter instead of a plain mass element.
     */
    @Test
    public void testSplitterAssignedToSteamedReservoir() {
        PhasedClosedSteamedReservoir drum
                = new PhasedClosedSteamedReservoir(Water.INSTANCE);
        PhasedNode water = new PhasedNode();
        PhasedNode steam = new PhasedNode();
        drum.connectTo(water);
        drum.connectTo(steam);
        MaterialLayer.add(SALT, water);
        assertTrue(drum.getMaterialElement()
                instanceof MaterialElementMassSplitter);
        MaterialLayer.setOutflowLimit(drum, steam, SALT, 0.1);
        assertEquals(((MaterialElementMassSplitter) drum.getMaterialElement())
                .getOutflowLimit(steam, SALT), 0.1, 0.0);
        assertThrows(ModelErrorException.class, ()
                -> MaterialLayer.setOutflowLimit(rOut, nOut, SALT, 0.1));
    }

    /**
     * A tank with an inflow of 5.0 with 4.0 and an outflow of 5.0 through a
     * node limited to 0.25 lets the outflow leave with 0.25, while the held
     * back material stays in the tank. Without a limit, the result is the
     * same as for a plain mass element.
     */
    @Test
    public void testSplitterLimitsOutflow() {
        OpenOrigin src = new OpenOrigin(PhysicalDomain.HYDRAULIC);
        OpenOrigin snk = new OpenOrigin(PhysicalDomain.HYDRAULIC);
        LinearDissipator rIn = new LinearDissipator(PhysicalDomain.HYDRAULIC);
        LinearDissipator rOutTank
                = new LinearDissipator(PhysicalDomain.HYDRAULIC);
        SelfCapacitance tank = new SelfCapacitance(PhysicalDomain.HYDRAULIC);
        GeneralNode n1 = new GeneralNode(PhysicalDomain.HYDRAULIC);
        GeneralNode n2 = new GeneralNode(PhysicalDomain.HYDRAULIC);
        GeneralNode n3 = new GeneralNode(PhysicalDomain.HYDRAULIC);
        GeneralNode n4 = new GeneralNode(PhysicalDomain.HYDRAULIC);
        src.connectTo(n1);
        rIn.connectBetween(n1, n2);
        tank.connectTo(n2);
        tank.connectTo(n3);
        rOutTank.connectBetween(n3, n4);
        snk.connectTo(n4);
        src.setEffort(10.0);
        snk.setEffort(0.0);
        rIn.setResistanceParameter(1.0);
        rOutTank.setResistanceParameter(1.0);
        tank.setTimeConstant(1e9);
        tank.setStepTime(1.0);
        tank.setInitialEffort(5.0);

        MaterialLayer.add(SALT, n1);
        // A plain tank gets no splitter, so it is replaced here to test the
        // splitter without the phased fluid model of a steam drum.
        MaterialElementMassSplitter splitter
                = new MaterialElementMassSplitter(tank);
        tank.setMaterialElement(splitter);
        splitter.addMaterial(SALT);
        splitter.setMass(100.0);
        splitter.setConcentration(SALT, 1.0);
        MaterialLayer.setStepTime(n1, 1.0);
        MaterialLayer.setOriginConcentration(src, SALT, 4.0);
        assertEquals(splitter.getOutflowLimit(n3, SALT),
                Double.POSITIVE_INFINITY, 0.0);
        splitter.setOutflowLimit(n3, SALT, 0.25);
        DomainAnalogySolver tankSolver = new DomainAnalogySolver();
        tankSolver.addNetwork(n1);

        tankSolver.prepareCalculation();
        tankSolver.doCalculation();
        // Positive flows go from the node into the tank.
        assertEquals(n2.getFlow(tank), 5.0, 1e-6);
        assertEquals(n3.getFlow(tank), -5.0, 1e-6);
        assertEquals(MaterialLayer.getConcentration(n3, SALT), 0.25, 1e-12);
        assertEquals(MaterialLayer.getConcentration(n4, SALT), 0.25, 1e-12);
        tankSolver.prepareCalculation();
        double expected = (100.0 * 1.0 + 5.0 * 4.0 + 5.0 * (1.0 - 0.25))
                / (100.0 + 5.0);
        assertEquals(splitter.getConcentration(SALT), expected, 1e-6);

        // Without a limit, it behaves like a plain mass element.
        splitter.setOutflowLimit(n3, SALT, Double.POSITIVE_INFINITY);
        double start = splitter.getConcentration(SALT);
        tankSolver.doCalculation();
        assertEquals(MaterialLayer.getConcentration(n3, SALT), start, 1e-12);
        tankSolver.prepareCalculation();
        expected = (100.0 * start + 5.0 * 4.0) / (100.0 + 5.0);
        assertEquals(splitter.getConcentration(SALT), expected, 1e-6);
    }

    /**
     * An element that implements ConstantMassElement provides its mass to the
     * material layer, so the mass set on the element is used for the mixing
     * without setting it on the material element.
     */
    @Test
    public void testMassFromConstantMassElement() {
        HeatOrigin src = new HeatOrigin();
        HeatOrigin snk = new HeatOrigin();
        HeatFlowSource flow = new HeatFlowSource();
        HeatVolumizedFlowResistance pipe = new HeatVolumizedFlowResistance();
        HeatNode n1 = new HeatNode();
        HeatNode n2 = new HeatNode();
        HeatNode n3 = new HeatNode();
        src.connectTo(n1);
        flow.connectBetween(n1, n2);
        pipe.connectBetween(n2, n3);
        snk.connectTo(n3);
        flow.setFlow(5.0);
        src.setOriginTemperature(300.0);
        snk.setOriginTemperature(300.0);
        pipe.setResistanceParameter(1.0);
        pipe.setStepTime(1.0);
        pipe.getHeatHandler().setInitialTemperature(300.0);
        pipe.setMass(100.0);

        MaterialLayer.add(SALT, n1);
        MaterialLayer.setStepTime(n1, 1.0);
        // Both origins provide the same value so the flow direction does not
        // matter for the expected result.
        MaterialLayer.setOriginConcentration(src, SALT, 4.0);
        MaterialLayer.setOriginConcentration(snk, SALT, 4.0);
        MaterialLayer.setInitialConcentration(pipe, SALT, 1.0);
        MaterialElementMass pipeMaterial
                = (MaterialElementMass) pipe.getMaterialElement();
        assertEquals(pipeMaterial.getMass(), 100.0, 1e-12);

        DomainAnalogySolver pipeSolver = new DomainAnalogySolver();
        pipeSolver.addNetwork(n1);
        pipeSolver.prepareCalculation();
        pipeSolver.doCalculation();
        double inflow = Math.abs(n2.getFlow(pipe));
        assertEquals(inflow, 5.0, 1e-9);
        pipeSolver.prepareCalculation();
        double expected = (100.0 * 1.0 + inflow * 4.0) / (100.0 + inflow);
        assertEquals(pipeMaterial.getConcentration(SALT), expected, 1e-9);
    }

    /**
     * The search does not follow coupled elements, so a material added to the
     * primary side of a heat exchanger does not reach the secondary side or
     * the thermal network between them.
     */
    @Test
    public void testCoupledElementsNotFollowed() {
        HeatExchanger hx = new HeatExchanger();
        HeatOrigin priSrc = new HeatOrigin();
        HeatOrigin priSink = new HeatOrigin();
        HeatOrigin secSrc = new HeatOrigin();
        HeatOrigin secSink = new HeatOrigin();
        HeatNode priSrcNode = new HeatNode();
        HeatNode secSrcNode = new HeatNode();
        HeatFlowSource priFlow = new HeatFlowSource();
        HeatFlowSource secFlow = new HeatFlowSource();
        hx.initGenerateNodes();
        hx.initName("hx");
        hx.initCharacteristic(100.0, 100.0, 10.0);
        hx.initConditions(300.0, 300.0);
        priSrc.connectToVia(priFlow, priSrcNode);
        priFlow.connectTo(hx.getHeatNode(HeatExchanger.PRIMARY_IN));
        priSink.connectTo(hx.getHeatNode(HeatExchanger.PRIMARY_OUT));
        secSrc.connectToVia(secFlow, secSrcNode);
        secFlow.connectTo(hx.getHeatNode(HeatExchanger.SECONDARY_IN));
        secSink.connectTo(hx.getHeatNode(HeatExchanger.SECONDARY_OUT));
        priFlow.setFlow(10.0);
        secFlow.setFlow(10.0);
        priSrc.setOriginTemperature(350.0);
        secSrc.setOriginTemperature(300.0);

        MaterialLayer.add(SALT, priSrcNode);
        MaterialLayer.setOriginConcentration(priSrc, SALT, 3.0);

        assertTrue(hx.getPrimarySide().getMaterialElement()
                instanceof MaterialElementMass);
        assertNull(hx.getSecondarySide().getMaterialElement());
        assertNull(hx.getHeatNode(HeatExchanger.SECONDARY_IN)
                .getMaterialNode());
        assertNull(secSrc.getMaterialElement());
        assertNull(hx.getPrimarySide().getInnerThermalNode()
                .getMaterialNode());

        DomainAnalogySolver hxSolver = new DomainAnalogySolver();
        hxSolver.addNetwork(priSrcNode);
        hxSolver.prepareCalculation();
        hxSolver.doCalculation();
        assertEquals(MaterialLayer.getConcentration(
                hx.getHeatNode(HeatExchanger.PRIMARY_IN), SALT), 3.0, 1e-9);
        assertTrue(priSink.isMaterialCalculationFinished());
    }
}
