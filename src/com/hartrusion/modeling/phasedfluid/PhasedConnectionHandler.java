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
package com.hartrusion.modeling.phasedfluid;

import com.hartrusion.modeling.general.AbstractElement;

/**
 *
 * @author Viktor Alexander Hartung
 */
public class PhasedConnectionHandler implements PhasedHandler {

    private PhasedNode node;

    private final PhasedElement element;

    public PhasedConnectionHandler(PhasedElement parent) {
        element = parent;
    }

    @Override
    public boolean registerPhasedNode(PhasedNode hn) {
        if (node == null) {
            node = hn;
            return true;
        }
        return false;
    }

    @Override
    public void preparePhasedCalculation() {
        // nothing will happen here
    }

    @Override
    public boolean doPhasedCalculation() {
        return false;
    }

    /**
     * Sets the phased fluid energy property to the connected node. This is the
     * main thing this class is doing. Gets called from PhasedHeatFluidConverter
     * for example.
     * 
     * <p>
     * It is assumed that the fluid is in non-vapour state.
     *
     * @param temperature Temperature value from heat fluid.
     */
    public void setPhasedFromConverter(double temperature) {
        node.setHeatEnergy(
                element.getPhasedFluidProperties().getSpecificHeatCapacity()
                * temperature,
                (AbstractElement) element);
    }

    @Override
    public boolean isPhasedCalulationFinished() {
        return node.heatEnergyUpdated((AbstractElement) element);
    }

    @Override
    public void setInitialHeatEnergy(double heatEnergy) {
        node.setHeatEnergy(heatEnergy, (AbstractElement) element);
    }

    @Override
    public double getHeatEnergy() {
        throw new UnsupportedOperationException(
                "This phased handler does not contain own properties.");
    }

    @Override
    public void setInnerHeatedMass(double heatedMass) {
        throw new UnsupportedOperationException(
                "This phased handler does not support having a mass.");
    }

}
