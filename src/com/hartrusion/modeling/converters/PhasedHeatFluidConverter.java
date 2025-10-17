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
package com.hartrusion.modeling.converters;

import com.hartrusion.modeling.ElementType;
import com.hartrusion.modeling.PhysicalDomain;
import com.hartrusion.modeling.exceptions.CalculationException;
import com.hartrusion.modeling.exceptions.ModelErrorException;
import com.hartrusion.modeling.general.FlowThrough;
import com.hartrusion.modeling.general.GeneralNode;
import com.hartrusion.modeling.heatfluid.HeatConnectionHandler;
import com.hartrusion.modeling.heatfluid.HeatElement;
import com.hartrusion.modeling.heatfluid.HeatHandler;
import com.hartrusion.modeling.heatfluid.HeatNode;
import com.hartrusion.modeling.phasedfluid.PhasedConnectionHandler;
import com.hartrusion.modeling.phasedfluid.PhasedElement;
import com.hartrusion.modeling.phasedfluid.PhasedFluidProperties;
import com.hartrusion.modeling.phasedfluid.PhasedHandler;
import com.hartrusion.modeling.phasedfluid.PhasedNode;

/**
 *
 * @author Viktor Alexander Hartung
 */
public class PhasedHeatFluidConverter extends FlowThrough
        implements HeatElement, PhasedElement {

    PhasedNode phasedNode;
    HeatNode heatNode;

    private int phasedNodeIndex = -1;
    private int heatNodeIndex = -1;

    PhasedFluidProperties phasedProperties;

    HeatConnectionHandler heatHandler = new HeatConnectionHandler(this);
    PhasedConnectionHandler phasedHandler = new PhasedConnectionHandler(this);

    public PhasedHeatFluidConverter(PhasedFluidProperties phasedProperties) {
        super(PhysicalDomain.MULTIDOMAIN);
        setElementType(ElementType.BRIDGED);
        this.phasedProperties = phasedProperties;
    }

    @Override
    public void registerNode(GeneralNode p) {
        // only one heatnode and one phasednode are allowed here, check this
        // and save array indizies.
        if (p instanceof PhasedNode && phasedNodeIndex == -1) {
            phasedNodeIndex = nodes.size();
            phasedNode = (PhasedNode) p;
            phasedHandler.registerPhasedNode(phasedNode);
        } else if (p instanceof HeatNode && heatNodeIndex == -1) {
            heatNodeIndex = nodes.size();
            heatNode = (HeatNode) p;
            heatHandler.registerHeatNode(heatNode);
        } else {
            throw new ModelErrorException(
                    "Node seems to be of wrong type or type already added.");
        }
        super.registerNode(p);
    }

    @Override
    public boolean doCalculation() {
        boolean didSomething = false;
        // check if only one of 2 effort values are updated. update the other 
        // one if necessary
        if (heatNode.effortUpdated() && !phasedNode.effortUpdated()) {
            phasedNode.setEffort(heatNode.getEffort());
            didSomething = true;
        } else if (!heatNode.effortUpdated() && phasedNode.effortUpdated()) {
            heatNode.setEffort(phasedNode.getEffort());
            didSomething = true;
        }

        // usually we would put the whole calculation in the heat and steam
        // handler but since we have two one-port handlers here, a few things
        // are handled here and the doHandlerCalculation methods will only get
        // called if needed.
        didSomething = didSomething || converterCalculation();

        didSomething = didSomething || phasedNode.doCalculateHeatEnergy();
        didSomething = didSomething || heatNode.doCalculateTemperature();

        return didSomething;
    }

    private boolean converterCalculation() {
        boolean didSomething = false;

        // check if flow is known
        if (!heatNode.flowUpdated(this) && !phasedNode.flowUpdated(this)) {
            return false; // at least one flow needs to be known
        }

        // if one flow is missing, set the other one - this should never
        // be needed as the flow should set itself through automatically
        // but we do not know if reare cases happen where the nodes get
        // some flow assigned by a slover, so we will add a few lines here
        // to handle such occasions
        if (heatNode.flowUpdated(this) && !phasedNode.flowUpdated(this)) {
            phasedNode.setFlow(-heatNode.getFlow(this), this, false);
            didSomething = true;
        } else if (!heatNode.flowUpdated(this) && phasedNode.flowUpdated(this)) {
            heatNode.setFlow(-phasedNode.getFlow(this), this, false);
            didSomething = true;
        }

        if (heatNode.getFlow(this) == 0.0 && phasedNode.getFlow(this) == 0.0) {
            // Special case: Zero flow.
            if (!heatNode.temperatureUpdated(this)) {
                heatNode.setNoTemperature(this);
                didSomething = true;
            }
            if (!phasedNode.heatEnergyUpdated(this)) {
                phasedNode.setNoHeatEnergy(this);
                didSomething = true;
            }
            return didSomething;
        }

        if (heatNode.getFlow(this) > 0.0 && phasedNode.getFlow(this) < 0.0
                && heatNode.effortUpdated()
                && !phasedHandler.isPhasedCalulationFinished()) {
            // flow comes from heat and goes to phased domain.
            if (heatNode.temperatureUpdated(this)
                    && !phasedNode.heatEnergyUpdated(this)) {
                // It is possible, even if a flow is there, that there is a 
                // no-temperature flag due to flow not beeing exactly zero.
                if (heatNode.noTemperature(this)) {
                    phasedNode.setNoHeatEnergy(this);
                } else {
                    phasedHandler.setPhasedFromConverter(
                            heatNode.getTemperature(this));
                }
                // usually this should return true as calculation of handler
                // should be finished now but we cant say we wont change the
                // implementation later, so lets still request the state
                // from handler.
                return didSomething
                        || phasedHandler.isPhasedCalulationFinished();
            }
        } else if (heatNode.getFlow(this) < 0.0
                && phasedNode.getFlow(this) > 0.0
                && !heatHandler.isHeatCalulationFinished()) {
            // flow goes from phased domain to heat domain
            if (!heatNode.temperatureUpdated(this)
                    && phasedNode.heatEnergyUpdated(this)) {
                // It is possible, even if a flow is there, that there is a 
                // no-propertiy flag due to flow not beeing exactly zero.
                if (phasedNode.noHeatEnergy(this)) {
                    heatNode.setNoTemperature(this);
                } else {
                    heatHandler.setTemperatureFromConverter(
                            phasedNode.getHeatEnergy(this)
                            / phasedProperties.getSpecificHeatCapacity());
                }
            }
        } else if (heatNode.getFlow(this) < 0.0
                && phasedNode.getFlow(this) < 0.0
                || heatNode.getFlow(this) > 0.0
                && phasedNode.getFlow(this) > 0.0) {
            throw new CalculationException("Inconsistent flow direction");
        }

        return didSomething;
    }

    @Override
    public void prepareCalculation() {
        super.prepareCalculation(); // this will reset the nodes
        heatHandler.prepareHeatCalculation();
        phasedHandler.preparePhasedCalculation();
    }

    @Override
    public boolean isCalculationFinished() {
        return super.isCalculationFinished()
                && heatHandler.isHeatCalulationFinished()
                && phasedHandler.isPhasedCalulationFinished();
    }

    @Override
    public void setStepTime(double dt) {
        // This is physically not needed here but required by interface
    }

    @Override
    public void setEffort(double effort, HeatNode source) {
        phasedNode.setEffort(effort);
    }

    @Override
    public void setFlow(double flow, HeatNode source) {
        phasedNode.setFlow(-flow, this, true);
    }

    @Override
    public HeatHandler getHeatHandler() {
        return heatHandler;
    }

    @Override
    public void setEffort(double effort, PhasedNode source) {
        heatNode.setEffort(effort);
    }

    @Override
    public void setFlow(double flow, PhasedNode source) {
        heatNode.setFlow(-flow, this, true);
    }

    @Override
    public PhasedHandler getPhasedHandler() {
        return phasedHandler;
    }

    @Override
    public PhasedFluidProperties getPhasedFluidProperties() {
        return phasedProperties;
    }

}
