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
package com.hartrusion.modeling.assemblies;

import com.hartrusion.modeling.heatfluid.HeatNoMassExchangerHandler;
import com.hartrusion.modeling.heatfluid.HeatNoMassExchangerResistance;
import com.hartrusion.modeling.heatfluid.HeatNode;

/**
 * A massless heat exchanger, used when the internal mass of fluids can be
 * negligible or must be neglected due to numerical issues. The mass based heat
 * exchanger is unstable as soon as the heat transfer per time step is high in a
 * way that its decrease will have an effect in the sign of the heat transfer in
 * the next cycle.
 * <p>
 * This assembly does not generate a separate thermal network, it's transferring
 * the heat energy directly though the heat handlers.
 *
 * @author Viktor Alexander Hartung
 */
public class HeatExchangerNoMass {

    HeatNoMassExchangerResistance primarySide
            = new HeatNoMassExchangerResistance();
    HeatNoMassExchangerResistance secondarySide
            = new HeatNoMassExchangerResistance();

    private boolean nodesGenerated = false;

    public static final int PRIMARY_IN = 1;
    public static final int PRIMARY_OUT = 2;
    public static final int SECONDARY_IN = 3;
    public static final int SECONDARY_OUT = 4;

    public HeatExchangerNoMass() {
        // link both sides. It must be possible for the heat handler instances 
        // to be casted to the interface of the no mass exchanger handler.
        primarySide.setOtherSide(
                (HeatNoMassExchangerHandler) secondarySide.getHeatHandler());
        secondarySide.setOtherSide(
                (HeatNoMassExchangerHandler) primarySide.getHeatHandler());

        // Make the connection known to the solver:
        primarySide.setCoupledElement(secondarySide);
        secondarySide.setCoupledElement(primarySide);
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
     * Sets the characteristic of the heat exchanger using the dimensionless
     * property NTU which is the number of transfer units. It describes how much
     * heat throughput there is per heat mass flow regarding to the lower value
     * of both flow values.
     *
     * @param ntu value from 0.2 to 5. With 5 being ultra effective.
     */
    public void initCharacteristic(double ntu) {
        primarySide.setNtu(ntu);
        secondarySide.setNtu(ntu);
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

    public HeatNoMassExchangerResistance getPrimarySide() {
        return primarySide;
    }

    public HeatNoMassExchangerResistance getSecondarySide() {
        return secondarySide;
    }
}
