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

import com.hartrusion.modeling.PhysicalDomain;
import com.hartrusion.modeling.general.GeneralNode;
import com.hartrusion.modeling.hydraulic.HydraulicLinearValve;

/**
 *
 * @author Viktor Alexander Hartung
 */
public class PhasedLinearValve extends HydraulicLinearValve
        implements PhasedElement {

    private final PhasedSimpleHandler phasedHandler;

    public PhasedLinearValve() {
        this.phasedHandler = new PhasedSimpleHandler(this);
        physicalDomain = PhysicalDomain.PHASEDFLUID;
    }
    
    @Override
    public void prepareCalculation() {
        super.prepareCalculation();
        phasedHandler.preparePhasedCalculation();
    }
    
    @Override
    public boolean doCalculation() {
        boolean didSomething;
        PhasedNode  pn;
        
        // call super, this either sets directly zero flow if closes
        // or calls resistor calculation.
        didSomething = super.doCalculation();
        
        // Add call for thermalHandler calculation
        didSomething = phasedHandler.doPhasedCalculation() || didSomething;

        // call calculation on heat nodes - contrary to flow, it is not
        // possible to do this with the set-method of this class as it is 
        // unknown when that calculation will be possible.
        for (GeneralNode p : nodes) {
            pn = (PhasedNode) p;
            didSomething = pn.doCalculateHeatEnergy() || didSomething;
        }

        return didSomething;
    }
    
    @Override
    public boolean isCalculationFinished() {
        // Add call for phasedHandler isCalculationFinished
        return super.isCalculationFinished()
                && phasedHandler.isPhasedCalulationFinished();
    }
    
    @Override
    public void registerNode(GeneralNode p) {
        super.registerNode(p);
        // Node must be of extended type PhasedNode, therefore it is possible
        // to cast it and register it here during configuration.
        phasedHandler.registerPhasedNode((PhasedNode) p);
    }

    @Override
    public void setEffort(double effort, PhasedNode source) {
        setEffort(effort, (GeneralNode) source);
    }

    @Override
    public void setFlow(double flow, PhasedNode source) {
        setFlow(flow, (GeneralNode) source);
    }

    @Override
    public PhasedHandler getPhasedHandler() {
        return phasedHandler;
    }

    @Override
    public PhasedFluidProperties getPhasedFluidProperties() {
        return null;
    }

}
