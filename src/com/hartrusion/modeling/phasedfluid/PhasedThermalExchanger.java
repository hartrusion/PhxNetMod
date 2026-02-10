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

import com.hartrusion.modeling.ElementType;
import com.hartrusion.modeling.PhysicalDomain;
import com.hartrusion.modeling.assemblies.PhasedCondenser;
import com.hartrusion.modeling.exceptions.ModelErrorException;
import com.hartrusion.modeling.general.EffortSource;
import com.hartrusion.modeling.general.FlowThrough;
import com.hartrusion.modeling.general.GeneralNode;
import com.hartrusion.modeling.general.OpenOrigin;

/**
 * Represents a fixed mass that can store heat energy and transfer this heat
 * energy to a thermal system. Commonly used as one side of a heat exchanger
 * system.
 * <p>
 * As the thermal network that is connected to this element is transferring
 * temperature, not heat energy, a pressure value has to be managed and passed
 * to the handler so the temperature without the evaporation energy part can be
 * calculated and used.
 *
 * @author Viktor Alexander Hartung
 */
public class PhasedThermalExchanger extends FlowThrough
        implements PhasedElement {

    PhasedThermalVolumeHandler phasedHandler;

    private OpenOrigin thermalOrigin;
    private EffortSource thermalEffortSource;
    private GeneralNode thermalNode;

    private boolean previousPressureSet;

    /**
     * A reference to a condenser assembly that uses this element. This is used
     * to have the prepareCalculation call on the assembly as this will set
     * different resistances there.
     */
    private PhasedCondenser externalPhasedCondenserAssembly;

    public void setExternalPhasedCondenserAssembly(
            PhasedCondenser externalPhasedCondenserAssembly) {
        this.externalPhasedCondenserAssembly = externalPhasedCondenserAssembly;
    }

    public PhasedThermalExchanger(PhasedFluidProperties fluidProperties) {
        super(PhysicalDomain.PHASEDFLUID);
        elementType = ElementType.BRIDGED;
        phasedHandler = new PhasedThermalVolumeHandler(fluidProperties, this);
    }

    @Override
    public void prepareCalculation() {
        previousPressureSet = false;
        if (externalPhasedCondenserAssembly != null) {
            // Call assembly prepare
            externalPhasedCondenserAssembly.prepareCalculation();
        }
        // thermalEffortSource.resetEffortUpdated();
        super.prepareCalculation();
        phasedHandler.preparePhasedCalculation();
        if (thermalOrigin != null) {
            thermalOrigin.prepareCalculation();
        }
        thermalEffortSource.prepareCalculation();
    }

    @Override
    public boolean doCalculation() {
        boolean didSomething = false;
        PhasedNode pn;

        if (thermalOrigin != null) {
            didSomething = thermalOrigin.doCalculation();
        }

        if (!previousPressureSet) {
            if (getNode(0).effortUpdated()) {
                phasedHandler.setPreviousPressure(getNode(0).getEffort());
                didSomething = true;
                previousPressureSet = true;
            } else if (getNode(1).effortUpdated()) {
                // as this is a flowthrough type element, we should never
                // need this and node 0 is sufficient.
                phasedHandler.setPreviousPressure(getNode(1).getEffort());
                didSomething = true;
                previousPressureSet = true;
            }
        }

        didSomething = thermalEffortSource.doCalculation() || didSomething;

        // Call Phased Handler calculation
        didSomething = phasedHandler.doPhasedCalculation() || didSomething;

        // call calculation on phased nodes - contrary to flow, it is not
        // possible to do this with the set-operation as it is unknown when 
        // that calculation will be possible.
        for (GeneralNode p : nodes) {
            pn = (PhasedNode) p;
            didSomething = pn.doCalculateHeatEnergy() || didSomething;
        }

        return didSomething;
    }

    @Override
    public boolean isCalculationFinished() {
        // Add call for phasedHandler calculationFinished
        return super.isCalculationFinished()
                && phasedHandler.isPhasedCalulationFinished();
    }

    /**
     * Creates a minimum set of elements with a thermal effort source. It will
     * create and connect:
     * <ul>
     * <li>Thermal open Origin</li>
     * <li>General Port</li>
     * <li>Thermal Effort Source</li>
     * </ul>
     * <p>
     * The thermal effort source will be used as a connection to a thermal
     * network from the heat domain.
     * <p>
     * Use of this method is optional, it just makes network setup easier as
     * thermal networks usually are super small. You can also do a full network
     * build yourself and use the setInnerThermalEffortSource instead.
     */
    public void initComponent() {
        // Create thermal elements
        thermalOrigin = new OpenOrigin(PhysicalDomain.THERMAL);
        thermalEffortSource = new EffortSource(PhysicalDomain.THERMAL);
        thermalNode = new GeneralNode(PhysicalDomain.THERMAL);

        setCoupledElement(thermalEffortSource);
        if (thermalEffortSource.getCoupledElement() == null) {
            thermalEffortSource.setCoupledElement(this);
        }

        // connect thermal elements
        thermalNode.registerElement(thermalOrigin);
        thermalOrigin.registerNode(thermalNode);
        thermalNode.registerElement(thermalEffortSource);
        thermalEffortSource.registerNode(thermalNode);

        // make source known to the thermal handler
        phasedHandler.setThermalEffortSource(thermalEffortSource);
    }

    /**
     * Returns a reference to the instance of the used thermal effort source
     * element. This element serves as a connection to a thermal network. For
     * setting up the heat exchanger, connect this to a port in the thermal
     * domain.
     *
     * @return
     */
    public EffortSource getInnerThermalEffortSource() {
        return thermalEffortSource;
    }

    /**
     * Returns a reference to the thermal node that is connected between the
     * Effort source an the thermal origin.
     *
     * @return
     */
    public GeneralNode getInnerThermalNode() {
        return thermalNode;
    }

    public OpenOrigin getInnerThermalOrigin() {
        return thermalOrigin;
    }

    public void setInnerThermalEffortSource(EffortSource s) {
        thermalEffortSource = s;
        setCoupledElement(s);
        phasedHandler.setThermalEffortSource(thermalEffortSource);
    }

    @Override
    public void registerNode(GeneralNode p) {
        super.registerNode(p);
        // Node is of class PhasedNode so it can be casted and registered.
        // otherwise model will not work.
        phasedHandler.registerPhasedNode((PhasedNode) p);
    }

    @Override
    public void setEffort(double effort, GeneralNode source) {
        // there is no change in effort, therefore just pass it through directly
        int idx = nodes.indexOf(source);
        idx = -idx + 1; // switch 0 and 1
        nodes.get(idx).setEffort(effort, this, true);
    }

    @Override
    public void setEffort(double effort, PhasedNode source) {
        // there is no change in effort, therefore just pass it through directly
        int idx = nodes.indexOf(source);
        idx = -idx + 1; // switch 0 and 1
        nodes.get(idx).setEffort(effort, this, true);
    }

    @Override
    public void setFlow(double flow, PhasedNode source) {
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
    public PhasedHandler getPhasedHandler() {
        return phasedHandler;
    }

    @Override
    public void setStepTime(double dt) {
        phasedHandler.setStepTime(dt);
    }

    @Override
    public PhasedFluidProperties getPhasedFluidProperties() {
        return phasedHandler.getPhasedFluidProperties();
    }

    /**
     * Returns the current temperature of the fluid inside the volume.
     *
     * @return
     */
    public double getTemperature() {
        if (getNode(0).effortUpdated()) {
            return phasedHandler.getPhasedFluidProperties().getTemperature(
                    phasedHandler.getHeatEnergy(), getNode(0).getEffort());
        } else if (getNode(1).effortUpdated()) {
            return phasedHandler.getPhasedFluidProperties().getTemperature(
                    phasedHandler.getHeatEnergy(), getNode(0).getEffort());
        }
        throw new ModelErrorException(
                    "None of both nodes is in required updated state for "
                            + "this operation.");
    }

}
