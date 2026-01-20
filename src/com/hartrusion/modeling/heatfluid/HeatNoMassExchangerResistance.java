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
package com.hartrusion.modeling.heatfluid;

/**
 * One side of a heat exchanger that does not have any mass. Use for heat
 * transfer with a flow to mass to transfer ratio that would otherwise be
 * unstable. See the HeatExchangerNoMass assembly class for more details.
 *
 * @author Viktor Alexander Hartung
 */
public class HeatNoMassExchangerResistance extends HeatAbstractFlowResistance {

    private final HeatNoMassExchangerHandler heatExchangeHandler
            = new HeatNoMassExchangerHandler(this);

    public HeatNoMassExchangerResistance() {
        // set to interface of super class
        heatHandler = heatExchangeHandler;
    }

    /**
     * Heat exchangers are always consisting of a pair of flow through elements,
     * while the volumized common heat exchangers are build in an abstract way
     * that would allow to have n volumes, this more specific no-mass heat
     * exchanger only supports exactly two sides for heat exchange. This method
     * needs to be called to connect those heat exchangers, it has to be called
     * on both elements to make the other element known to it. This will set up
     * the necessary link in the heatHandler that does the heat transfer.
     */
    public void setOtherSide(HeatNoMassExchangerResistance otherSide) {
        // just cast and set - will fail horribly if this is not possible.
        heatExchangeHandler.setOtherSideHandler(
                (HeatNoMassExchangerHandler) otherSide.getHeatHandler());
    }
    
    public void setNtu(double kTimesA) {
        heatExchangeHandler.setNtu(kTimesA);
    }

}
