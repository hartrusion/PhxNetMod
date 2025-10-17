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

import com.hartrusion.modeling.PhysicalDomain;
import com.hartrusion.modeling.exceptions.ModelErrorException;
import com.hartrusion.modeling.general.GeneralNode;
import com.hartrusion.modeling.general.Origin;

/**
 * An open connection out of the model boundaries for steam and only for steam.
 * It does not force anyhing on a port but allows any flow to flow out of the
 * model (like venting it into the air or something like this).
 *
 * @author Viktor Alexander Hartung
 */
public class SteamSink extends Origin {

    public SteamSink() {
        super(PhysicalDomain.STEAM);
    }

    @Override
    public boolean doCalculation() {
        // call the nodes steam property calcuation also from here
        return ((SteamNode) nodes.get(0)).doCalculateSteamProperties();
    }
    
    @Override
    public boolean isCalculationFinished() {
        return super.isCalculationFinished() ||
                ((SteamNode) nodes.get(0)).steamPropertiesUpdated(this);
    }

    @Override
    public void setFlow(double flow, GeneralNode source) {
        if (flow < 0.0) {
            throw new ModelErrorException("Flow forced out of steam sink. A"
                    + "steam sink only supports steam flow into the sink.");
        }
    }
    
    @Override
    public void setEffort(double effort, GeneralNode source) {
        // Any set effort is fine, contrary to the super class origin.
    }

    @Override
    public void setStepTime(double dt) {
        // no
    }

}
