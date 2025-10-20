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
import com.hartrusion.modeling.general.GeneralNode;
import com.hartrusion.modeling.hydraulic.HydraulicLinearValve;

/**
 *
 * @author Viktor Alexander Hartung
 */
public class SteamIsenthalpicValve
        extends HydraulicLinearValve
        implements SteamElement {
    
    private final SteamIsenthalpicExpansionHandler steamHandler;
    
    SteamTable propertyTable;
    
    public SteamIsenthalpicValve(SteamTable propertyTable) {
        steamHandler = new SteamIsenthalpicExpansionHandler(this);
        this.propertyTable = propertyTable;
        physicalDomain = PhysicalDomain.STEAM; // overwrite HYDRAULIC
    }
    
    @Override
    public void prepareCalculation() {
        super.prepareCalculation();
        steamHandler.prepareSteamCalculation();
    }
    
    @Override
    public boolean doCalculation() {
        boolean didSomething = super.doCalculation();
        SteamNode sn;

        // Add call for steamHandler calculation
        didSomething = steamHandler.doSteamCalculation() || didSomething;

        // call calulation on heat nodes - contrary to flow, it is not
        // possible to do this with the set-method of this class as it is 
        // unknown when that calculation will be possible.
        for (GeneralNode n : nodes) {
            sn = (SteamNode) n;
            didSomething = sn.doCalculateSteamProperties() || didSomething;
        }

        return didSomething;
    }

    @Override
    public boolean isCalculationFinished() {
        // Add call for heatHandler isCalculationFinished
        return super.isCalculationFinished()
                && steamHandler.isSteamCalulationFinished();
    }

    @Override
    public void setEffort(double effort, SteamNode source) {
        setEffort(effort, (GeneralNode) source);
    }

    @Override
    public void setFlow(double flow, SteamNode source) {
        setFlow(flow, (GeneralNode) source);
    }

    @Override
    public SteamHandler getSteamHandler() {
        return steamHandler;
    }

    @Override
    public SteamTable getSteamTable() {
        return propertyTable;
    }

    @Override
    public SteamNode getSteamNode(int idx) {
        return (SteamNode) nodes.get(idx);
    }

}
