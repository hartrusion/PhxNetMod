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

import com.hartrusion.modeling.converters.PhasedEnergyExchangerHandler;
import com.hartrusion.modeling.general.GeneralNode;
import com.hartrusion.modeling.heatfluid.HeatNoMassEnergyExchangerResistance;
import com.hartrusion.modeling.heatfluid.HeatNode;
import com.hartrusion.modeling.phasedfluid.PhasedFluidProperties;
import com.hartrusion.modeling.phasedfluid.PhasedNoMassExchangerElement;
import com.hartrusion.modeling.phasedfluid.PhasedNode;

/**
 * Implements a thermal energy exchanger between heat and phased domain without
 * masses on both elements.
 * <pre>
 *  *          primary_in
 *               o          o   secondary_out
 *               |          |
 *    primary   ---        ---
 *    Side      | |        | |
 *              | |========| |  secondarySide
 *              | |        | |
 *              ---        ---
 *         prim. |          |
 *         inner o          o secondary_in
 * </pre>
 * <p>
 * As this element has no energy storage capabilities, it has no initial
 * conditions.
 *
 * @author Viktor Alexander Hartung
 */
public class PhasedHeatExchangerNoMass {

    private final HeatNoMassEnergyExchangerResistance secondarySide
            = new HeatNoMassEnergyExchangerResistance();
    private final PhasedNoMassExchangerElement primarySide;

    private boolean nodesGenerated = false;

    public static final int PRIMARY_IN = 1;
    public static final int PRIMARY_OUT = 2;
    public static final int SECONDARY_IN = 3;
    public static final int SECONDARY_OUT = 4;

    public PhasedHeatExchangerNoMass(PhasedFluidProperties fluidProperties) {
        // Generate element that require the PhasedFluidProperties
        primarySide = new PhasedNoMassExchangerElement(fluidProperties);

        // link both no-mass sides (a cast must be possible here)
        primarySide.setOtherSide(
                (PhasedEnergyExchangerHandler) secondarySide.getHeatHandler());
        secondarySide.setOtherSide(
                (PhasedEnergyExchangerHandler) primarySide.getPhasedHandler());

        primarySide.setCoupledElement(secondarySide);
        secondarySide.setCoupledElement(primarySide);
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
        primarySide.connectTo(node);
        node = new PhasedNode();
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
        primarySide.setName(
                name + "PrimarySide");
        secondarySide.setName(
                name + "SecondarySide");
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
                return (PhasedNode) primarySide.getNode(0);
            case PRIMARY_OUT:
                return (PhasedNode) primarySide.getNode(1);
        }
        return null;
    }

    public PhasedNoMassExchangerElement getPrimarySide() {
        return primarySide;
    }

    public HeatNoMassEnergyExchangerResistance getSecondarySide() {
        return secondarySide;
    }

}
