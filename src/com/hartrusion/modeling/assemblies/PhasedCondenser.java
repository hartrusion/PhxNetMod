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
package com.hartrusion.modeling.assemblies;

import com.hartrusion.modeling.PhysicalDomain;
import com.hartrusion.modeling.general.ClosedOrigin;
import com.hartrusion.modeling.general.EffortSource;
import com.hartrusion.modeling.general.GeneralNode;
import com.hartrusion.modeling.general.LinearDissipator;
import com.hartrusion.modeling.heatfluid.HeatNode;
import com.hartrusion.modeling.heatfluid.HeatThermalExchanger;
import com.hartrusion.modeling.phasedfluid.PhasedClosedSteamedReservoir;
import com.hartrusion.modeling.phasedfluid.PhasedElement;
import com.hartrusion.modeling.phasedfluid.PhasedFluidProperties;
import com.hartrusion.modeling.phasedfluid.PhasedNode;
import com.hartrusion.modeling.phasedfluid.PhasedThermalExchanger;
import com.hartrusion.modeling.phasedfluid.PhasedThermalVolumeHandler;

/**
 * Represents a condenser unit for phased fluid while the secondary loop is
 * implemented by using the heat fluid. A condenser is a combined thermal
 * exchanger and reservoir on the primary phased side.
 * <p>
 * It consists of three main elements: A primary heat exchanger for the phased
 * fluid, a reservoir of the phased fluid and a secondary heat exchanger. It
 * also contains a full thermal network between those which has to be detected
 * automatically when using the solving algorithm. The reservoir and the
 * condenser both are able to transfer thermal energy.
 * <pre>
 *          primary_in
 *               o
 *               |        thermalFlowCondenserResistance
 *   primary   -----    o---------XXXXX----
 *   condenser |   |    |                 |        secondary_out
 *             |   | = (|)                |            o
 *             |   |    |                 |            |
 *             -----    |                 o-----       |
 *         prim. |      ----              |    |     -----
 *         inner o          |   th flow   |    |     |   |  secondary
 *         node  |          |  res. resi  |   (|) =  |   |  side
 *             -----    o---------XXXXX----    |     |   |  heatFluid
 *             |   |    |   |                  |     -----  exchanger
 *     primary |   | = (|)  |    ---------------       |
 *   reservoir |   |    |   |    |                     |
 *             -----    |   |    |                     o secondary_in
 *               |      ----o----
 *  primary_Out  o          |
 *                         _|_ thermalOrigin
 * </pre>
 * <p>
 * The heat transfer is depending on the fill level of the phased fluid
 * reservoir. This has to be parametrized with the initCharacteristic method,
 * two fill levels have to be provided.
 * <p>
 * The mass in the primary condenser, which is supposed to be some kind of steam
 * mass, is constant. This is not that realistic but the only way to model it
 * properly. The thermal network is not using the nonlinear resistors which are
 * available for heat exchangers as those condensers do focus on condensing, not
 * on flow direction. This also allows easier solving.
 * <p>
 * The fill level will change the kTimesA distribution between the condenser and
 * the reservoir. The total kTimesA will be reduced down to 20 % if the heat
 * exchanger is filled completely. This leads to more heat going into the
 * reservoir, increasing the pressure and therefore acting as a self-regulation
 * mechanism if the condenser fills up too much. The behavior can be disabled by
 * setting a high level value on the low level property.
 *
 * @author Viktor Alexander Hartung
 */
public class PhasedCondenser {

    private final HeatThermalExchanger secondarySide
            = new HeatThermalExchanger();
    private final PhasedThermalExchanger primarySideCondenser;
    private final PhasedClosedSteamedReservoir primarySideReservoir;

    private final PhasedNode primaryInnerNode = new PhasedNode();

    private final GeneralNode primaryCondenserThermalNode
            = new GeneralNode(PhysicalDomain.THERMAL);
    private final GeneralNode primaryReservoirThermalNode
            = new GeneralNode(PhysicalDomain.THERMAL);
    private final GeneralNode secondaryThermalNode
            = new GeneralNode(PhysicalDomain.THERMAL);
    private final EffortSource primaryCondenserTemperature
            = new EffortSource(PhysicalDomain.THERMAL);
    private final EffortSource primaryReservoirTemperature
            = new EffortSource(PhysicalDomain.THERMAL);
    private final EffortSource secondaryTemperature
            = new EffortSource(PhysicalDomain.THERMAL);
    private final GeneralNode thermalOriginNode
            = new GeneralNode(PhysicalDomain.THERMAL);
    private final ClosedOrigin thermalOrigin
            = new ClosedOrigin(PhysicalDomain.THERMAL);
    private final LinearDissipator thermalFlowCondenserResistance
            = new LinearDissipator(PhysicalDomain.THERMAL);
    private final LinearDissipator thermalFlowReservoirResistance
            = new LinearDissipator(PhysicalDomain.THERMAL);

    private final PhasedThermalVolumeHandler primaryReservoirHandler;
    private final PhasedThermalVolumeHandler primaryCondenserHandler;

    private boolean nodesGenerated = false;

    public static final int PRIMARY_IN = 1;
    public static final int PRIMARY_OUT = 2;
    public static final int SECONDARY_IN = 3;
    public static final int SECONDARY_OUT = 4;
    public static final int PRIMARY_INNER = 5;

    private double fillLevelLow, fillLevelHigh, kTimesA;

    /**
     * Factor of how much kTimesA value is still used in total on both primary
     * elements if the condenser is flooded (level > fillLevelHigh)
     */
    private double floodedKTimesATotal = 0.3;

    private double km, kb, dm, db;

    public PhasedCondenser(PhasedFluidProperties fluidProperties) {
        // Generate elements that require the PhasedFluidProperties
        primarySideCondenser = new PhasedThermalExchanger(fluidProperties);
        primarySideReservoir
                = new PhasedClosedSteamedReservoir(fluidProperties);
        // Generate a different handler fot the reservoir and attach it to
        // the reservoir element. This handler has the ability to do thermal
        // transfer.
        primaryReservoirHandler = new PhasedThermalVolumeHandler(
                fluidProperties, (PhasedElement) primarySideReservoir);
        primarySideReservoir.setPhasedHandler(primaryReservoirHandler);

        // Build the thermal network - this will be done manually here as it 
        // has to be modified during calculation, and we need some access here.
        thermalOrigin.connectTo(thermalOriginNode);
        primaryCondenserTemperature.connectBetween(
                thermalOriginNode, primaryCondenserThermalNode);
        primaryReservoirTemperature.connectBetween(
                thermalOriginNode, primaryReservoirThermalNode);
        secondaryTemperature.connectBetween(
                thermalOriginNode, secondaryThermalNode);
        // Add resistances between the thermal effort sources
        thermalFlowCondenserResistance.connectBetween(
                primaryCondenserThermalNode, secondaryThermalNode);
        thermalFlowReservoirResistance.connectBetween(
                primaryReservoirThermalNode, secondaryThermalNode);

        // Connect the thermal effort sources to the phased and heat fluid 
        // network elements
        primarySideCondenser.setInnerThermalEffortSource(
                primaryCondenserTemperature);
        primaryReservoirHandler.setThermalEffortSource(
                primaryReservoirTemperature);
        secondarySide.setInnerThermalEffortSource(secondaryTemperature);

        // Connect condenser and reservoir on primary side.
        primarySideCondenser.connectToVia(
                primarySideReservoir, primaryInnerNode);

        // Make the assembly known to get the prepareCalculation call here.
        primarySideCondenser.setExternalPhasedCondenserAssembly(this);

        // Get the handler from the heat exchanger (it creates its own) and
        // write it to field, we need it here multiple times.
        primaryCondenserHandler
                = (PhasedThermalVolumeHandler) primarySideCondenser
                        .getPhasedHandler();
    }

    /**
     * This method is called by the PhasedThermalExchanger which was modified in
     * a way to do exactly this. This call is needed to make the kTimesA set
     * depending on the fill height.
     */
    public void prepareCalculation() {
        double kA, kDist, level;
        level = primarySideReservoir.getFillHeight();
        // kTimesA effective on both elements
        // kDist from 0 to 1 is the part that is applied to the reservoir
        if (level <= fillLevelLow) {
            kA = kTimesA;
            kDist = 0.0;
            thermalFlowCondenserResistance.setResistanceParameter(kA);
            thermalFlowReservoirResistance.setOpenConnection();
        } else if (level >= fillLevelHigh) {
            kA = kTimesA * floodedKTimesATotal;
            kDist = 1.0;
            thermalFlowCondenserResistance.setOpenConnection();
            thermalFlowReservoirResistance.setResistanceParameter(kA);
        } else { // interpolate
            kA = km * level + kb;
            kDist = dm * level + db;
            // Apply kA total and distribution value:
            thermalFlowCondenserResistance
                    .setResistanceParameter(kA * (1.0 - kDist));
            thermalFlowReservoirResistance
                    .setResistanceParameter(kA * kDist);
        }
    }

    /**
     *
     * @param primaryBaseArea Base area of the phased side (m²)
     * @param primaryCondensingMass Constant heated mass inside the primary
     * phased side (kg)
     * @param secondaryMass Constant heated mass inside the secondary side (kg)
     * @param kTimesA Thermal conductance k * A in W/K
     * @param fillLevelLow Fill level of the reservoir with thermal exchanger
     * fully exposed (meters)
     * @param fillLevelHigh Fill level of the reservoir with thermal exchanger
     * completely covered (meters)
     */
    public void initCharacteristic(double primaryBaseArea,
            double primaryCondensingMass,
            double secondaryMass, double kTimesA,
            double fillLevelLow, double fillLevelHigh) {
        primarySideReservoir.setBaseArea(primaryBaseArea);
        primarySideCondenser.getPhasedHandler().setInnerHeatedMass(
                primaryCondensingMass);
        this.fillLevelLow = fillLevelLow;
        this.fillLevelHigh = fillLevelHigh;
        this.kTimesA = kTimesA;
        secondarySide.getHeatHandler().setInnerThermalMass(secondaryMass);
        // Calculate some factors once for kTimesA calculation:
        km = (floodedKTimesATotal - 1.0) / (fillLevelHigh - fillLevelLow);
        kb = 1.0 - km * fillLevelLow;
        dm = (1.0) / (fillLevelHigh - fillLevelLow);
        db = -dm * fillLevelLow;
    }

    /**
     * Generates a set of nodes that are connected to the heat exchangers
     * primary and secondary side. Those nodes can then be named with initName
     * and be accessed with getHeatNode or getPhasedNode.
     * <p>
     * This is not required for the assembly to work, it's just a convenient
     * method that allows generating the required nodes instead of generating
     * them outside and connect them.
     * <p>
     * Nodes can be accessed by using getHeatNode or getPhasedNode, no matter if
     * they were created using this method or outside this assembly.
     */
    public void initGenerateNodes() {
        if (nodesGenerated) {
            throw new IllegalStateException("Nodes were already generated, "
                    + "this must only be called once.");
        }
        nodesGenerated = true;
        GeneralNode node;
        node = new PhasedNode();
        primarySideCondenser.connectTo(node);
        node = new PhasedNode();
        primarySideReservoir.connectTo(node);
        node = new HeatNode();
        secondarySide.connectTo(node);
        node = new HeatNode();
        secondarySide.connectTo(node);
    }

    /**
     * Names elements and nodes of this assembly with the given name prefix. The
     * heat nodes connected to the primary and secondary sides will only be
     * named here if they were created using initGenerateHeatNodes().
     *
     * @param name Desired prefix for all nodes and elements
     */
    public void initName(String name) {
        primarySideCondenser.setName(
                name + "PrimarySideCondenser");
        primarySideReservoir.setName(
                name + "PrimarySideReservoir");
        secondarySide.setName(name + "SecondarySide");
        if (nodesGenerated) {
            getPhasedNode(PRIMARY_IN).setName(
                    name + "PrimaryIn");
            getPhasedNode(PRIMARY_OUT).setName(
                    name + "PrimaryOut");
            getHeatNode(SECONDARY_IN).setName(
                    name + "SecondaryIn");
            getHeatNode(SECONDARY_OUT).setName(
                    name + "SecondaryOut");
        }
        primaryInnerNode.setName(
                name + "MiddleNode");
        // Thermal network
        thermalOrigin.setName(name + "ThermalOrigin");
        thermalOriginNode.setName(name + "ThermalOrigin");
        primaryCondenserThermalNode.setName(
                name + "PrimaryCondenserThermalNode");
        primaryReservoirThermalNode.setName(
                name + "PrimaryReservoirThermalNode");
        secondaryThermalNode.setName(
                name + "SecondaryThermalNode");
        primaryCondenserTemperature.setName(
                name + "PrimaryCondenserTemperature");
        primaryReservoirTemperature.setName(
                name + "PrimaryReservoirTemperature");
        secondaryTemperature.setName(
                name + "SecondaryTemperature");
        thermalFlowCondenserResistance.setName(
                name + "ThermalFlowCondenserResistance");
        thermalFlowReservoirResistance.setName(
                name + "ThermalFlowReservoirResistance");
    }

    /**
     * Sets the fluid temperature on both sides (initial condition). This also
     * sets the pressure on the primary side
     *
     * @param primaryTemp in Kelvin
     * @param secondaryTemp in Kelvin
     * @param fillHeight fill level of the primary reservoir (meters)
     */
    public void initConditions(double primaryTemp, double secondaryTemp,
            double fillHeight) {
        // get properties from the element that knows it.
        PhasedFluidProperties fp
                = primarySideReservoir.getPhasedFluidProperties();
        // Primary condenser:
        primaryCondenserHandler.setInitialHeatEnergy(
                primaryTemp * fp.getSpecificHeatCapacity());
        // Primary reservoir:
        primaryReservoirHandler.setInitialHeatEnergy(
                primaryTemp * fp.getSpecificHeatCapacity());
        primarySideReservoir.setInitialEffort(
                fp.getSaturationEffort(primaryTemp));
        secondarySide.getHeatHandler().setInitialTemperature(secondaryTemp);
        primarySideReservoir.setInitialState(
                primarySideReservoir.getMassForHeight(fillHeight, primaryTemp)
                , primaryTemp);
    }

    /**
     * Access the heat nodes which are connected with this phased condenser.
     *
     * @param identifier can be SECONDARY_IN (3), SECONDARY_OUT (4)
     * @return
     */
    public HeatNode getHeatNode(int identifier) {
        switch (identifier) {
            case SECONDARY_IN:
                return (HeatNode) secondarySide.getNode(0);
            case SECONDARY_OUT:
                return (HeatNode) secondarySide.getNode(1);
        }
        return null;
    }

    /**
     * Access the phased nodes which are connected with this phased condenser.
     *
     * @param identifier can be PRIMARY_IN (1), PRIMARY_OUT (2)
     * @return PhasedNode
     */
    public PhasedNode getPhasedNode(int identifier) {
        switch (identifier) {
            case PRIMARY_IN:
                return (PhasedNode) primarySideCondenser.getNode(1);
            case PRIMARY_OUT:
                return (PhasedNode) primarySideReservoir.getNode(1);
            case PRIMARY_INNER:
                return primaryInnerNode;
        }
        return null;
    }

    public PhasedThermalExchanger getPrimarySideCondenser() {
        return primarySideCondenser;
    }

    public PhasedClosedSteamedReservoir getPrimarySideReservoir() {
        return primarySideReservoir;
    }

    public HeatThermalExchanger getSecondarySide() {
        return secondarySide;
    }
}
