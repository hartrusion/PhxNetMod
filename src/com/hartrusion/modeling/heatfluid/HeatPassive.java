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
import com.hartrusion.modeling.general.GeneralNode;
import com.hartrusion.modeling.general.FlowThrough;

/**
 * Base class for heat change. Just passes flow and effort through while
 * providing base functionality with thermalHandler. Can be further extended to
 * emelents for only changing the heat energy transfer. If not extended, it will
 * not do anything but provide a thermal volume.
 *
 * <p>
 * Its purpose is to provide basic hydraulic properties that are non present,
 * the element has no resistance and no pressure changes or whatever.
 *
 * <p>
 * It is required for the deriving class to set the heatHandlerInterface
 * variable to the implementation of a heat handler.
 *
 * @author Viktor Alexander Hartung
 */
public abstract class HeatPassive extends FlowThrough implements HeatElement {

    protected HeatHandler heatHandlerInterface;

    public HeatPassive() {
        super(PhysicalDomain.HEATFLUID);
    }

    @Override
    public void prepareCalculation() {
        super.prepareCalculation();
        heatHandlerInterface.prepareHeatCalculation();
    }

    @Override
    public boolean doCalculation() {
        boolean didSomething = false;
        HeatNode tp;

        // Call heathandler calculation
        didSomething = heatHandlerInterface.doThermalCalculation() 
                || didSomething;

        // call calulation on heat nodes - contrary to flow, it is not
        // possible to do this with the set-operation as it is unknown when 
        // that calculation will be possible.
        for (GeneralNode p : nodes) {
            tp = (HeatNode) p;
            didSomething = tp.doCalculateTemperature() || didSomething;
        }

        return didSomething;
    }

    @Override
    public boolean isCalculationFinished() {
        // Add call for thermalhandler calculationfinished
        return super.isCalculationFinished()
                && heatHandlerInterface.isHeatCalulationFinished();
    }

    @Override
    public void registerNode(GeneralNode p) {
        super.registerNode(p);
        // Node is of class HeatNode so it can be casted and registered.
        // otherwise model will not work.
        heatHandlerInterface.registerHeatNode((HeatNode) p);
    }

    @Override
    public void setEffort(double effort, GeneralNode source) {
        // there is no change in effort, therefore just pass it through directly
        int idx = nodes.indexOf(source);
        idx = -idx + 1; // switch 0 and 1
        nodes.get(idx).setEffort(effort, this, true);
    }

    @Override
    public void setEffort(double effort, HeatNode source) {
        // there is no change in effort, therefore just pass it through directly
        int idx = nodes.indexOf(source);
        idx = -idx + 1; // switch 0 and 1
        nodes.get(idx).setEffort(effort, this, true);
    }

    @Override
    public void setFlow(double flow, HeatNode source) {
        // if flow is set, just pass it through this Element.
        int idx = nodes.indexOf(source);
        idx = -idx + 1; // switch 0 and 1
        if (flow == 0.0 && Double.compare(flow, 0.0) != 0) {
            // prevent a negative zero flow. Its not bad but its extra work.
            if (!nodes.get(idx).flowUpdated(this)) {
                nodes.get(idx).setFlow(0.0, this, true);
            }
        } else {
            if (!nodes.get(idx).flowUpdated(this)) {
                // what comes IN, goes OUT
                nodes.get(idx).setFlow(-flow, this, true);
            }
        }
    }

    @Override
    public HeatHandler getHeatHandler() {
        return heatHandlerInterface;
    }
}
