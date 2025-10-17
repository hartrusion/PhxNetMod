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
 * exchanger and reservoir on the primaray phased side.
 *
 * <p>
 * It consists of three main elements: A primary heat exchanger for the phased
 * fluid, a reservoir of the phased fluid and a secondary heat exchanger.
 *
 * <p>
 * The heat transfer is depending on the fill level of the phased fluid
 * reservoir. This has to be parametrized with the initCharacteristic method,
 * two fill levels have to be provided.
 *
 * @author Viktor Alexander Hartung
 */
public class PhasedCondenser {

    HeatThermalExchanger secondarySide = new HeatThermalExchanger();
    PhasedThermalExchanger primarySideCondenser;
    PhasedClosedSteamedReservoir primarySideReservoir;

    PhasedNode primaryInnerNode = new PhasedNode();

    GeneralNode primaryCondenserThermalNode
            = new GeneralNode(PhysicalDomain.THERMAL);
    GeneralNode primaryReservoirThermalNode
            = new GeneralNode(PhysicalDomain.THERMAL);
    GeneralNode secondaryThermalNode
            = new GeneralNode(PhysicalDomain.THERMAL);
    EffortSource primaryCondenserTemperature
            = new EffortSource(PhysicalDomain.THERMAL);
    EffortSource primaryReservoirTemperature
            = new EffortSource(PhysicalDomain.THERMAL);
    EffortSource secondaryTemperature
            = new EffortSource(PhysicalDomain.THERMAL);
    GeneralNode thermalOriginNode
            = new GeneralNode(PhysicalDomain.THERMAL);
    ClosedOrigin thermalOrigin
            = new ClosedOrigin(PhysicalDomain.THERMAL);
    LinearDissipator thermalFlowCondenserResistance
            = new LinearDissipator(PhysicalDomain.THERMAL);
    LinearDissipator thermalFlowReservoirResistance
            = new LinearDissipator(PhysicalDomain.THERMAL);

    PhasedThermalVolumeHandler primaryReservoirHandler;

    private boolean nodesGenerated = false;

    public static final int PRIMARY_IN = 1;
    public static final int PRIMARY_OUT = 2;
    public static final int SECONDARY_IN = 3;
    public static final int SECONDARY_OUT = 4;

    private double fillLevelLow, fillLevelHigh, kTimesA;

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
        // has to be modified during calculation and we need some access here.
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
    }
    
    // gets called by the PhasedThermalExchanger
    public void prepareCalculation() {
        
    }

    /**
     *
     * @param primaryBaseArea Base area of the phased side.
     * @param primaryCondensingMass Constant heated mass inside the primary
     * phased side
     * @param secondaryMass Constant heated mass inside the secondary side
     * @param kTimesA Thermal conductance k * A
     * @param fillLevelLow Fill level of the reservoir with thermal exchanger
     * fully exposed
     * @param fillLevelHigh Fill level of the reservoir with thermal exchanger
     * completely covered.
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
    }

    /**
     * Generates a set ofof nodes that are connected to the heat exchangers
     * primary and secondary side. Those nodes can then be named with initName
     * and be accessed with getHeatNode or getPhasedNode.
     */
    public void initGenerateNodes() {
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
                name + "PhasedCondenserPrimarySideCondenser");
        primarySideReservoir.setName(
                name + "PhasedCondenserPrimarySideReservoir");
        secondarySide.setName(name + "PhasedCondenserSecondarySide");
        if (nodesGenerated) {
            getPhasedNode(PRIMARY_IN).setName(
                    name + "PhasedCondenserPrimaryIn");
            getPhasedNode(PRIMARY_OUT).setName(
                    name + "PhasedCondenserPrimaryOut");
            getHeatNode(SECONDARY_IN).setName(
                    name + "PhasedCondenserSecondaryIn");
            getHeatNode(SECONDARY_OUT).setName(
                    name + "PhasedCondenserSecondaryOut");
        }
        primaryInnerNode.setName(
            name + "PhasedCondenserMiddleNode");
        // Thermal network
        thermalOrigin.setName(name + "PhasedCondenserThermalOrigin");
        thermalOriginNode.setName(name + "PhasedCondenserThermalOrigin");
        primaryCondenserThermalNode.setName(
                name + "PhasedCondenserPrimaryCondenserThermalNode");
        primaryReservoirThermalNode.setName(
                name + "PhasedCondenserPrimaryReservoirThermalNode");
        secondaryThermalNode.setName(
                name + "PhasedCondenserSecondaryThermalNode");
        primaryCondenserTemperature.setName(
                name + "PhasedCondenserPrimaryCondenserTemperature");
        primaryReservoirTemperature.setName(
                name + "PhasedCondenserPrimaryReservoirTemperature");
        secondaryTemperature.setName(
                name + "PhasedCondenserSecondaryTemperature");
        thermalFlowCondenserResistance.setName(
                name + "PhasedCondenserThermalFlowCondenserResistance");
        thermalFlowReservoirResistance.setName(
                name + "PhasedCondenserThermalFlowReservoirResistance");
    }

    /**
     * Sets the fluid temperature on both sides (initial condition)
     *
     * @param primaryTemp
     * @param secondaryTemp
     */
    public void initConditions(double primaryTemp, double secondaryTemp) {
// Todo        
//primarySide.getHeatHandler().setInitialTemperature(primaryTemp);
        secondarySide.getHeatHandler().setInitialTemperature(secondaryTemp);
    }

    /**
     * Acess the heat nodes which are connected with this phased condenser.
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
     * Acess the phased nodes which are connected with this phased condenser.
     *
     * @param identifier can be PRIMARY_IN (1), PRIMARY_OUT (2)
     * @return
     */
    public PhasedNode getPhasedNode(int identifier) {
        switch (identifier) {
            case PRIMARY_IN:
                return (PhasedNode) primarySideCondenser.getNode(1);
            case PRIMARY_OUT:
                return (PhasedNode) primarySideReservoir.getNode(1);
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
