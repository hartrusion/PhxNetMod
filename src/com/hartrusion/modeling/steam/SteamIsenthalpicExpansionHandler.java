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
package com.hartrusion.modeling.steam;

import com.hartrusion.modeling.exceptions.ModelErrorException;
import com.hartrusion.modeling.general.AbstractElement;

/**
 * Handles isenthalpic expansion over an element between two nodes. This is most
 * likely used for steam flow resistances or valves.
 *
 * @author Viktor Alexander Hartung
 */
public class SteamIsenthalpicExpansionHandler implements SteamHandler {

    /**
     * Reference to the element which is using this heat handler instance.
     */
    private final SteamElement element;

    /**
     * Holds the two steam nodes from the element.
     */
    private final SteamNode[] nodes = new SteamNode[2];

    SteamIsenthalpicExpansionHandler(SteamElement parent) {
        element = parent;
    }

    @Override
    public boolean registerSteamPort(SteamNode sp) {
        if (nodes[0] == null) {
            nodes[0] = sp;
            return true;
        } else if (nodes[0] != sp) {
            if (nodes[1] == null) {
                nodes[1] = sp;
                return true;
            } else if (nodes[1] != sp) {
                throw new ModelErrorException("Cannot register three "
                        + "different nodes on this handler!");
            }
        }
        return false;
    }

    @Override
    public void prepareSteamCalculation() {
        nodes[0].prepareCalculation();
        nodes[1].prepareCalculation();
    }

    @Override
    public boolean doSteamCalculation() {
        boolean didSomething = false;
        boolean allFlowsUpdated, allFlowsZero;
        SteamNode updatedNode = null, nonUpdatedNode = null;
        double enthalpy;
        // allow easy access to the casted element
        AbstractElement e = (AbstractElement) element;
        allFlowsUpdated = nodes[0].flowUpdated(e)
                && nodes[1].flowUpdated(e);
        if (!allFlowsUpdated) {
            return false; //no direction without flow - no calculations.
        }
        // Check if we already did this and exit method immediatelly.
        if (nodes[0].steamPropertiesUpdated(e)
                && nodes[1].steamPropertiesUpdated(e)) {
            return false; // finished - nothing to do.
        }
        // detect the no-flow case. Use OR cause of numeric issues.
        allFlowsZero = nodes[0].getFlow(e) == 0.0 || nodes[1].getFlow(e) == 0.0;
        if (allFlowsZero) {
            if (!nodes[0].steamPropertiesUpdated(e)) {
                nodes[0].setNoSteamProperties(e);
                didSomething = true;
            }
            if (!nodes[1].steamPropertiesUpdated(e)) {
                nodes[1].setNoSteamProperties(e);
                didSomething = true;
            }
            return didSomething;
        }
        // for further calculations, both efforts (pressures) on both sides
        // need to be known.
        if (!nodes[0].effortUpdated() || !nodes[1].effortUpdated()) {
            return false;
        }
        // We need 1 of 2 ports in updated state. We already returned if there
        // are both already updated above.
        if (!nodes[0].steamPropertiesUpdated(e)
                && !nodes[1].steamPropertiesUpdated(e)) {
            return false;
        }
        // check which port is the updated and which one is to be updated:
        if (nodes[0].steamPropertiesUpdated(e)
                && !nodes[1].steamPropertiesUpdated(e)) {
            updatedNode = nodes[0];
            nonUpdatedNode = nodes[1];

        } else if (nodes[1].steamPropertiesUpdated(e)
                && !nodes[0].steamPropertiesUpdated(e)) {
            updatedNode = nodes[1];
            nonUpdatedNode = nodes[0];
        }
        // The Isenthalpic Process means that the spec enthalpy on both nodes
        // stays the same. That's all we need to know to calculate the steam
        // properties for the other side.
        if (updatedNode == null || nonUpdatedNode == null) {
            return false; // supress compiler warnings :)
        }
        enthalpy = updatedNode.getSteamProperty(1, e);
        nonUpdatedNode.setSteamProperties(
                element.getSteamTable().get(
                        "T_ph", updatedNode.getEffort(), enthalpy),
                enthalpy,
                element.getSteamTable().get(
                        "s_ph", updatedNode.getEffort(), enthalpy),
                element.getSteamTable().get(
                        "x_ph", updatedNode.getEffort(), enthalpy),
                e); // this little "e" here is the source argument btw.
        return true;
    }

    @Override
    public boolean isSteamCalulationFinished() {
        return nodes[0].steamPropertiesUpdated((AbstractElement) element)
                && nodes[1].steamPropertiesUpdated((AbstractElement) element);
    }

    @Override
    public void setSteamProperty(int index, double value) {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public double getSteamProperty(int index) {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public void setTotalMass(double mass) {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public double getTotalMass() {
        throw new UnsupportedOperationException("Not supported.");
    }

}
