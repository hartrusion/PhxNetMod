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
import com.hartrusion.modeling.general.OpenOrigin;

/**
 * Open boundary for a heat fluid system. This can serve as an entry or exit
 * node of a heat fluid system. If used as an entry, a fixed temperature will 
 * be applied.
 *
 * @author Viktor Alexander Hartung
 */
public class HeatOrigin extends OpenOrigin implements HeatElement {
    
    public HeatOrigin() {
        super(PhysicalDomain.HEATFLUID);
    }

    private double originTemperature = 298.15;

    @Override
    public HeatHandler getHeatHandler() {
        return null;
    }

    @Override
    public boolean doCalculation() {
        boolean didSomething = super.doCalculation();
        // if flow goes out of this origin, set the temperature value from
        // this origin to the flow, making it updated.
        if (nodes.get(0).flowUpdated(this)
                && !((HeatNode) nodes.get(0)).temperatureUpdated(this)) {
            if (nodes.get(0).getFlow(this) < 0.0) {
                ((HeatNode) nodes.get(0))
                        .setTemperature(originTemperature, this);
                didSomething = true;
            }
        }
        return didSomething;
    }

    /**
     * Sets the temperature which a fluid gets assigned if it comes out of this
     * origin. Default is 298.15 K (25 °C).
     *
     * @param originTemperature
     */
    public void setOriginTemperature(double originTemperature) {
        this.originTemperature = originTemperature;
    }

    @Override
    public void setEffort(double effort, HeatNode source) {
        setEffort(effort, (GeneralNode) source); // this will trigger except.
    }

    @Override
    public void setFlow(double flow, HeatNode source) {
        // nothing should happen.
    }

}
