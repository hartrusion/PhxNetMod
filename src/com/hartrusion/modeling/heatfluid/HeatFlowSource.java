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
package com.hartrusion.modeling.heatfluid;

import com.hartrusion.modeling.PhysicalDomain;
import com.hartrusion.modeling.general.FlowSource;
import com.hartrusion.modeling.general.GeneralNode;

/**
 *
 * @author Viktor Alexander Hartung
 */
public class HeatFlowSource extends FlowSource implements HeatElement {

    private final HeatSimpleHandler heatHandler;

    public HeatFlowSource() {
        super(PhysicalDomain.HEATFLUID);
        this.heatHandler = new HeatSimpleHandler(this);
    }

    @Override
    public void prepareCalculation() {
        super.prepareCalculation();
        heatHandler.prepareHeatCalculation();
    }

    @Override
    public boolean doCalculation() {
        boolean didSomething = super.doCalculation();
        HeatNode tp;

        // Add call for thermalhandler calculation
        didSomething = heatHandler.doThermalCalculation() || didSomething;

        // call calulation on heat nodes - contrary to flow, it is not
        // possible to do this with the set-method of this class as it is 
        // unknown when that calculation will be possible.
        for (GeneralNode p : nodes) {
            tp = (HeatNode) p;
            didSomething = tp.doCalculateTemperature() || didSomething;
        }

        return didSomething;
    }

    @Override
    public boolean isCalculationFinished() {
        // Add call for heatHandler isCalculationFinished
        return super.isCalculationFinished()
                && heatHandler.isHeatCalulationFinished();
    }

    @Override
    public void registerNode(GeneralNode n) {
        super.registerNode(n);
        // Node must be of extendet type HeatNode, therefore it is possible
        // to cast it and register it here during configuration.
        heatHandler.registerHeatNode((HeatNode) n);
    }

    @Override
    public HeatHandler getHeatHandler() {
        return heatHandler;
    }

    @Override
    public void setEffort(double effort, HeatNode source) {
        setEffort(effort, (GeneralNode) source);
    }

    @Override
    public void setFlow(double flow, HeatNode source) {
        setFlow(flow, (GeneralNode) source); // this might trigger except :)
    }
}
