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
package com.hartrusion.modeling.thermal;

import com.hartrusion.modeling.exceptions.ModelErrorException;

/**
 * Implements a thermal flow depending on the thermal gradient with the
 * logarithmic temperature difference.This is used to model heat transfert for
 heat exchanging elements.<p>
 * The element is designed to represent a thermal resistance between two sides
 * of a heat exchanger, using the average logarithmic temperature gradient to
 * model a counterflow heat exchanger. <b>However, it is not usable for full
 * dynamic simulations as it does only work in a certain described region, not
 * for full dynamic simulation.</b> It was kept in the library after
 * implementing it just to have it here.
 *
 * <p>
 The element needs additional information about an inlet temp to a heat
 volumized handler. Calling initHeatExchangerConnections will get the
 references from the connected nodes.
 *
 * @author Viktor Alexander Hartung
 */
public class ThermalLogGradientResistance
        extends ThermalHeatExchangerResistance {

    @Override
    public boolean doCalculation() {
        boolean didSomething = false;
        double deltaTemp1, deltaTemp2, thetaM, flow;

        for (int idx = 0; idx < 2; idx++) {
            if (heatHandler[idx] == null) {
                throw new ModelErrorException("No heat handler for adjacent "
                        + "heat exchanger found. Did you use initHeatHandler?");
            }
            if (!inTempUpdated[idx]
                    && heatHandler[idx].inboundValuesUpdated()) {
                inTemp[idx] = heatHandler[idx].getInboundHeatFlow()
                        / heatHandler[idx].getInboundMassFlow();
                inTempUpdated[idx] = true;
                didSomething = true;
            }
        }
        if (nodes.get(0).flowUpdated(this) && nodes.get(1).flowUpdated(this)) {
            return didSomething; // calculation is finished, nothing to do here.
        }
        if (!inTempUpdated[0] || !inTempUpdated[1]) {
            return didSomething; // calculation not yet possibble
        }
        if (!nodes.get(0).effortUpdated() || !nodes.get(1).effortUpdated()) {
            return didSomething; // calulation not yet possible
        }
        deltaTemp1 = Math.abs(nodes.get(0).getEffort() - inTemp[1]);
        deltaTemp2 = Math.abs(nodes.get(1).getEffort() - inTemp[0]);
        if (deltaTemp1 > deltaTemp2) {
            thetaM = -(deltaTemp1 - deltaTemp2)
                    / Math.log(deltaTemp1 / deltaTemp2);
        } else {
            thetaM = (deltaTemp2 - deltaTemp1)
                    / Math.log(deltaTemp2 / deltaTemp1);
        }
        // non counterflow makes it linear. Uncomment for stable solution.
        // thetaM = nodes.get(0).getEffort() - nodes.get(1).getEffort();
        flow = kTimesA * thetaM;
        nodes.get(0).setFlow(flow, this, true);
        nodes.get(1).setFlow(-flow, this, true);
        return didSomething;
    }
}
