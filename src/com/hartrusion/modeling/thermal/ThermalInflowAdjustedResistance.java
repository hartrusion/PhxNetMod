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
 * Represents a non-linear resitance that is beeing corrected by determining the
 * flow with a correction of the inlet flows and temperatures for a heat
 * exchanger.
 *
 * <p>
 * If a thermal resistance is connected between two heat exchanger volumes, it
 * will be unable to represent a counterflow heat exchanger. It would not
 * consider the inlet temperatures which do a direct heat transfer to the
 * outputs. Usually such a heat exchanger uses the average logarithmic
 * temperature differences and therefore consideres in and out temperatures for
 * both sides. This is implemented by using the ThermalLogGradientResistance,
 * but due to the math and the limitation to the modeling of a fixed operation
 * point, this does not work for full dynamics simulation. Large flow value
 * differences, especially a flow of zero, can not be modeled with such
 * equations. This is where this class comes into place.
 *
 * <p>
 * The main equation behind thermal power (heat) transfer in heat exchangers is
 * P = k * A * dThetaM where dThetaM is the temperature difference driving the
 * thermal power P through the heat exhcanger wall with the area A. Using only
 * the inner masses temperatures to get dThetaM is okay for equal flow direction
 * heat exchangers but not for counter flow, where dTheta_M has either to be
 * caclulated with the mentioned logarithmic equation or you have to model the
 * heat exchanger out of many single heat exchanger elements what would increase
 * computation time.
 *
 * <p>
 * This class introduces a so called equality flow factor which has a value of 0
 * for a perfect conter flow with both flow rates equally in counter direction.
 * For same flow rate direction, the equality factor will be either 1 or -1
 * depending on the direction. This variable is then translated to a
 * counterFlowFactor which is between 0 and 1. It describes the overall weighted
 * influence ot the inlet temperature to the dThetaM variable. This is not as
 * precise as the logarithmic approach (which is scientifically proven btw) but
 * it allows individual flow rates, inculding zero and any wanted direction.
 *
 * @author Viktor Alexander Hartung
 */
public class ThermalInflowAdjustedResistance
        extends ThermalHeatExchangerResistance {

    @Override
    public boolean doCalculation() {
        boolean didSomething = false;
        double dThetaD, dThetaF, flow, equality, counterFlowFactor,
                dThetaM, theta01, theta10;

        for (int idx = 0; idx < 2; idx++) {
            if (heatHandler[idx] == null) {
                throw new ModelErrorException("No heat handler for adjacent "
                        + "heat exchanger found. Did you use initHeatHandler?");
            }
            if (!inTempUpdated[idx]
                    && heatHandler[idx].inboundValuesUpdated()) {
                if (heatHandler[idx].hasNoInboundFlows()) {
                    inTemp[idx] = 0.0;
                    noInTempValue[idx] = true;
                } else {
                    // get average inlet temperature. Is useless now but allows
                    // to use this for nonflowthrough later maybe.
                    inTemp[idx] = heatHandler[idx].getInboundHeatFlow()
                            / heatHandler[idx].getInboundMassFlow();
                }
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
        // Temp. Difference between the two heat fluid masses:
        dThetaD = nodes.get(0).getEffort() - nodes.get(1).getEffort();
        /* The reference heat exchanger example has a differece of +45 K in
         * operation while the temperature differeces of the inner masses is
         * just -9. The +45 K are achieved by using the average logarithmic temp
         * difference value. However, this does not work for our full model, as
         * it is not valid for zero or any individual flow rates.
         */
        if (noInTempValue[0] || noInTempValue[1]) {
            // First, special occasion with one or both flows zero, therefore
            // not having any inlet temperature value. There will only be the
            // passive heat transfer between the masses, no correction for 
            // counterflow at all. The counterFlowFactor would be 0.0 anyway
            // for having only one flow and the other beeing zero. But we would
            // not be able to calculate as one temperature value is missing.
            dThetaF = 0.0;
        } else {
            equality = (heatHandler[0].getFlow() + heatHandler[1].getFlow())
                    / (Math.abs(heatHandler[0].getFlow())
                    + Math.abs(heatHandler[1].getFlow()));
            if (equality > 0.0) {
                counterFlowFactor = 1 - equality;
            } else if (equality < 0.0) {
                counterFlowFactor = 1 + equality;
            } else {
                counterFlowFactor = 1;
            }
            theta01 = inTemp[0] - nodes.get(1).getEffort();
            theta10 = inTemp[1] - nodes.get(0).getEffort();

            dThetaF = (theta01 - theta10) * counterFlowFactor * 0.5;
        }

        dThetaM = flow = kTimesA * (dThetaF + dThetaD);
        nodes.get(0).setFlow(flow, this, true);
        nodes.get(1).setFlow(-flow, this, true);
        return didSomething;
    }
}
