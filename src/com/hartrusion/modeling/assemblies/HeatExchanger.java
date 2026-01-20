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
import com.hartrusion.modeling.general.GeneralNode;
import com.hartrusion.modeling.heatfluid.HeatNode;
import com.hartrusion.modeling.heatfluid.HeatThermalExchanger;
import com.hartrusion.modeling.thermal.ThermalInflowAdjustedResistance;

/**
 * Implements a dynamic counterflow heat exhanger. Crossflow is given if flow
 * goes in node 0 and out of node 1 on one side and into node 1 and out of node
 * 0 on the other side. If both have flow in equal direction, it will behave as
 * a equal flow heat exchanger.
 * <p>
 * Usage:
 * <ul>
 * <li>Create instance</li>
 * <li>Call initGenerateHeatNodes() to generate nodes or use getPrimary and
 * getSecondary to make connections</li>
 * <li>Call initName() to name the elements (optional but recommended)</li>
 * <li>Call initCharacteristic to set dynamics</li>
 * <li>Call initConditions to set initial conditions</li>
 * </ul>
 *
 * @author Viktor Alexander Hartung
 */
public class HeatExchanger {

    HeatThermalExchanger primarySide = new HeatThermalExchanger();
    HeatThermalExchanger secondarySide = new HeatThermalExchanger();

    GeneralNode primaryThermalNode = new GeneralNode(PhysicalDomain.THERMAL);
    GeneralNode secondaryThermalNode = new GeneralNode(PhysicalDomain.THERMAL);
    ThermalInflowAdjustedResistance thermalFlowResistance
            = new ThermalInflowAdjustedResistance();

    private boolean nodesGenerated = false;

    public static final int PRIMARY_IN = 1;
    public static final int PRIMARY_OUT = 2;
    public static final int SECONDARY_IN = 3;
    public static final int SECONDARY_OUT = 4;

    public HeatExchanger() {
        primarySide.initComponent();
        secondarySide.initComponent();

        primarySide.getInnerThermalEffortSource()
                .connectTo(primaryThermalNode);
        secondarySide.getInnerThermalEffortSource()
                .connectTo(secondaryThermalNode);

        thermalFlowResistance.connectBetween(
                primaryThermalNode, secondaryThermalNode);
        
        // Perform initialization on the nonlinear resistance element
        thermalFlowResistance.initHeatHandler(primarySide);
        thermalFlowResistance.initHeatHandler(secondarySide);
    }

    /**
     *
     * @param primaryMass Heated mass inside the primary side
     * @param secondaryMass Heated mass inside the secondary side
     * @param kTimesA Thermal conductance k * A
     */
    public void initCharacteristic(double primaryMass, double secondaryMass,
            double kTimesA) {
        thermalFlowResistance.setThermalConductance(kTimesA);
        primarySide.getHeatHandler().setInnerThermalMass(primaryMass);
        secondarySide.getHeatHandler().setInnerThermalMass(secondaryMass);
    }

    /**
     * Generates a set of heat nodes that are connected to the heat exchangers
     * primary and secondary side. Those nodes can then be named with initName
     * and be accessed with getHeatNode.
     */
    public void initGenerateNodes() {
        nodesGenerated = true;
        HeatNode node;
        node = new HeatNode();
        primarySide.connectTo(node);
        node = new HeatNode();
        primarySide.connectTo(node);
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
        primarySide.setName(name + "HeatExchangerPrimarySide");
        secondarySide.setName(name + "HeatExchangerSecondarySide");
        primarySide.getInnerThermalOrigin().setName(
                name + "HeatExchangerPrimaryInnerThermalOrigin");
        primarySide.getInnerThermalNode().setName(
                name + "HeatExchangerPrimaryInnerThermalNode");
        primarySide.getInnerThermalEffortSource().setName(
                name + "HeatExchangerPrimaryThermalEffortSource");
        secondarySide.getInnerThermalOrigin().setName(
                name + "HeatExchangerSecondaryInnerThermalOrigin");
        secondarySide.getInnerThermalNode().setName(
                name + "HeatExchangerSecondaryInnerThermalNode");
        secondarySide.getInnerThermalEffortSource().setName(
                name + "HeatExchangerSecondaryThermalEffortSource");
        primaryThermalNode.setName(name + "HeatExchangerPrimaryThermalNode");
        secondaryThermalNode.setName(
                name + "HeatExchangerSecondaryThermalNode");
        thermalFlowResistance.setName(
                name + "HeatExchangerThermalFlowResistance");
        if (nodesGenerated) {
            getHeatNode(PRIMARY_IN).setName(
                    name + "HeatExchangerPrimaryIn");
            getHeatNode(PRIMARY_OUT).setName(
                    name + "HeatExchangerPrimaryOut");
            getHeatNode(SECONDARY_IN).setName(
                    name + "HeatExchangerSecondaryIn");
            getHeatNode(SECONDARY_OUT).setName(
                    name + "HeatExchangerSecondaryOut");
        }
    }

    /**
     * Sets the fluid temperature on both sides (iniital condition)
     *
     * @param primaryTemp
     * @param secondaryTemp
     */
    public void initConditions(double primaryTemp, double secondaryTemp) {
        primarySide.getHeatHandler().setInitialTemperature(primaryTemp);
        secondarySide.getHeatHandler().setInitialTemperature(secondaryTemp);
    }

    /**
     * Access the heat nodes which are connected with this heat exchanger.
     *
     * @param identifier can be PRIMARY_IN (1), PRIMARY_OUT (2), SECONDARY_IN
     * (3), SECONDARY_OUT (4)
     * @return
     */
    public HeatNode getHeatNode(int identifier) {
        switch (identifier) {
            case PRIMARY_IN:
                return (HeatNode) primarySide.getNode(0);
            case PRIMARY_OUT:
                return (HeatNode) primarySide.getNode(1);
            case SECONDARY_IN:
                return (HeatNode) secondarySide.getNode(0);
            case SECONDARY_OUT:
                return (HeatNode) secondarySide.getNode(1);
        }
        return null;
    }

    public HeatThermalExchanger getPrimarySide() {
        return primarySide;
    }

    public HeatThermalExchanger getSecondarySide() {
        return secondarySide;
    }

}
