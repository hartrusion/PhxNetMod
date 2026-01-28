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
import com.hartrusion.modeling.phasedfluid.PhasedFluidProperties;
import com.hartrusion.modeling.phasedfluid.PhasedNoMassExchangerElement;
import com.hartrusion.modeling.phasedfluid.PhasedNode;

/**
 *
 * @author Viktor Alexander Hartung
 */
public class PhasedExchangerNoMass {

    PhasedNoMassExchangerElement primarySide;
    PhasedNoMassExchangerElement secondarySide;

    private boolean nodesGenerated = false;

    public static final int PRIMARY_IN = 1;
    public static final int PRIMARY_OUT = 2;
    public static final int SECONDARY_IN = 3;
    public static final int SECONDARY_OUT = 4;

    public PhasedExchangerNoMass(PhasedFluidProperties fluidProperties) {
        primarySide
                = new PhasedNoMassExchangerElement(fluidProperties);
        secondarySide
                = new PhasedNoMassExchangerElement(fluidProperties);

        // link both sides. It must be possible for the heat handler instances 
        // to be casted to the interface of the no mass exchanger handler.
        primarySide.setOtherSide(
                (PhasedEnergyExchangerHandler) secondarySide.getPhasedHandler());
        secondarySide.setOtherSide(
                (PhasedEnergyExchangerHandler) primarySide.getPhasedHandler());

        // Make the connection known to the solver:
        primarySide.setCoupledElement(secondarySide);
        secondarySide.setCoupledElement(primarySide);
    }

    /**
     * Generates a set of heat nodes that are connected to the heat exchangers
     * primary and secondary side. Those nodes can then be named with initName
     * and be accessed with getPhasedNode.
     */
    public void initGenerateNodes() {
        nodesGenerated = true;
        PhasedNode node;
        node = new PhasedNode();
        primarySide.connectTo(node);
        node = new PhasedNode();
        primarySide.connectTo(node);
        node = new PhasedNode();
        secondarySide.connectTo(node);
        node = new PhasedNode();
        secondarySide.connectTo(node);
    }

    /**
     * Names elements and nodes of this assembly with the given name prefix. The
     * heat nodes connected to the primary and secondary sides will only be
     * named here if they were created using initGeneratePhasedNodes().
     *
     * @param name Desired prefix for all nodes and elements
     */
    public void initName(String name) {
        primarySide.setName(name + "PhasedExchangerPrimarySide");
        secondarySide.setName(name + "PhasedExchangerSecondarySide");
        if (nodesGenerated) {
            getPhasedNode(PRIMARY_IN).setName(
                    name + "PhasedExchangerPrimaryIn");
            getPhasedNode(PRIMARY_OUT).setName(
                    name + "PhasedExchangerPrimaryOut");
            getPhasedNode(SECONDARY_IN).setName(
                    name + "PhasedExchangerSecondaryIn");
            getPhasedNode(SECONDARY_OUT).setName(
                    name + "PhasedExchangerSecondaryOut");
        }
    }

    /**
     * Access the heat nodes which are connected with this heat exchanger.
     *
     * @param identifier can be PRIMARY_IN (1), PRIMARY_OUT (2), SECONDARY_IN
     * (3), SECONDARY_OUT (4)
     * @return
     */
    public PhasedNode getPhasedNode(int identifier) {
        switch (identifier) {
            case PRIMARY_IN:
                return (PhasedNode) primarySide.getNode(0);
            case PRIMARY_OUT:
                return (PhasedNode) primarySide.getNode(1);
            case SECONDARY_IN:
                return (PhasedNode) secondarySide.getNode(0);
            case SECONDARY_OUT:
                return (PhasedNode) secondarySide.getNode(1);
        }
        return null;
    }

    public PhasedNoMassExchangerElement getPrimarySide() {
        return primarySide;
    }

    public PhasedNoMassExchangerElement getSecondarySide() {
        return secondarySide;
    }
}
