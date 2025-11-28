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
import com.hartrusion.modeling.exceptions.ModelErrorException;
import com.hartrusion.modeling.general.AbstractElement;
import com.hartrusion.modeling.general.EffortSource;
import com.hartrusion.modeling.general.GeneralNode;
import com.hartrusion.modeling.general.OpenOrigin;
import com.hartrusion.modeling.initial.AbstractInitialCondition;
import com.hartrusion.modeling.initial.InitialConditions;
import com.hartrusion.modeling.initial.TemperatureInitialCondition;

/**
 * Represents a fixed volume with mass that can be heated up and expand to some
 * limited extend. Designed to be used as an evaporator element with a given
 * flow direction. The solving classes will handle this element in a specific
 * way that only works if there is only one path towards a capacitance or
 * effort forcing element.
 * <p>
 * The primary purpose of this class is to transfer heat from a thermal source
 * to the phased fluid system. It has a very rude density model that allows for
 * a rough estimation of density distribution to have some mass ejected or even
 * to calculate some kind of voiding factor for a positive void coefficient
 * feedback loop (this is what this was designed for).
 * <p>
 * There are two designed operating modes: Forced fluid flow though this
 * element into a reservoir or no flow with expansion by thermal energy being
 * added. There is a so-called reverse flow mode that also allows a flow from
 * the reservoir through the element but this will produce highly inaccurate
 * results, however, it is still providing a solution. The reverse flow was
 * intended to have means to drain the reservoir or keep the model stable
 * during accident simulations.
 *
 * @author Viktor Alexander Hartung
 */
public class PhasedExpandingThermalExchanger extends AbstractElement
        implements PhasedElement, InitialConditions {

    private OpenOrigin thermalOrigin;
    private EffortSource thermalEffortSource;
    private GeneralNode thermalNode;

    private final PhasedExpandingThermalVolumeHandler phasedHandler;

    public PhasedExpandingThermalExchanger(PhasedFluidProperties fluidProperties) {
        super(PhysicalDomain.PHASEDFLUID);
        elementType = ElementType.BRIDGED;
        phasedHandler = new PhasedExpandingThermalVolumeHandler(
                fluidProperties, this);
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
     * thermal networks usually are very small. You can also do a full network
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

    @Override
    public void prepareCalculation() {
        thermalEffortSource.resetEffortUpdated();
        super.prepareCalculation(); // also calls heathandler prepare calc
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
        boolean pressureKnown = false;
        double pressure = 0.0;
        // This is an isobaric process and no pressure loss occurs with flow.
        // Pass any existing pressure.
        for (GeneralNode p : nodes) {
            if (p.effortUpdated()) {
                pressure = p.getEffort();
                pressureKnown = true;
                break;
            }
        }
        if (pressureKnown) { // set pressure to all nodes 
            for (GeneralNode p : nodes) {
                if (!p.effortUpdated()) {
                    p.setEffort(pressure);
                }
            }
        }

        // Call phasedHandler calculation
        didSomething = phasedHandler.doPhasedCalculation() || didSomething;

        for (GeneralNode p : nodes) {
            pn = (PhasedNode) p;
            didSomething = pn.doCalculateHeatEnergy() || didSomething;
        }

        if (thermalOrigin != null) {
            didSomething = thermalOrigin.doCalculation() || didSomething;
            didSomething = thermalEffortSource.doCalculation() || didSomething;
        } else {
            didSomething = thermalEffortSource.doCalculation() || didSomething;
        }

        return didSomething;
    }

    @Override
    public boolean isCalculationFinished() {
        // Add call for thermalHandler calculationFinished
        return super.isCalculationFinished()
                && phasedHandler.isPhasedCalulationFinished();
    }

    /**
     * Sets the initial state of the element.
     * 
     * @param totalVolume Volume of the element
     * @param pressure current pressure (for getting rho)
     * @param temperatureIn Temperature at the inlet (for distribution)
     * @param temperatureState Temperature inside the element
     */
    public void setInitialState(double totalVolume, double pressure,
            double temperatureIn, double temperatureState) {
        phasedHandler.setVolume(totalVolume);

        PhasedFluidProperties fp = phasedHandler.getPhasedFluidProperties();

        double rho = fp.getAvgDensity(
                fp.getSpecificHeatCapacity() * temperatureIn,
                fp.getSpecificHeatCapacity() * temperatureState,
                pressure);

        phasedHandler.setInnerHeatedMass(totalVolume * rho);
        phasedHandler.setInitialHeatEnergy(
                temperatureState * fp.getSpecificHeatCapacity());
        phasedHandler.setInitialInHeatEnergy(
                temperatureIn * fp.getSpecificHeatCapacity());
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

    public GeneralNode getInnerThermalNode() {
        return thermalNode;
    }

    public OpenOrigin getInnerThermalOrigin() {
        return thermalOrigin;
    }

    public void setInnerThermalEffortSource(EffortSource s) {
        thermalEffortSource = s;
        setCoupledElement(s);
    }

    @Override
    public void setStepTime(double dt) {
        phasedHandler.setStepTime(dt);
    }

    @Override
    public void registerNode(GeneralNode p) {
        if (nodes.size() > 2) {
            throw new ModelErrorException("This element only supports exactly"
                    + " 2 connected nodeds!");
        }
        super.registerNode(p);
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
    public void setFlow(double flow, GeneralNode source) {
        // Nothing will happen.
    }

    @Override
    public void setFlow(double flow, PhasedNode source) {
        // Nothing will happen.
    }

    @Override
    public AbstractInitialCondition getState() {
        TemperatureInitialCondition ic = new TemperatureInitialCondition();
        ic.setElementName(elementName);
        // todo - also no temperature IC, will be own class.
        // ic.setTemperature(phasedHandler.getTemperature());
        return ic;
    }

    @Override
    public void setInitialCondition(AbstractInitialCondition ic) {
        checkInitialConditionName(ic);
        // todo
        //phasedHandler.setInitialTemperature(
        //        ((TemperatureInitialCondition) ic).getTemperature());
    }

    @Override
    public PhasedHandler getPhasedHandler() {
        return phasedHandler;
    }

    @Override
    public PhasedFluidProperties getPhasedFluidProperties() {
        return phasedHandler.getPhasedFluidProperties();
    }

    /**
     * Calculates a voiding based on a reference effort value, the voiding will
     * be the volume in percent that is present compared with the volume that
     * would be there on the referenceEffort at boiling temperature.
     *
     * @param referenceEffort Pressure in Pa defining the saturation point. This
     * can be 1e5 for ambient pressure.
     *
     * @return Value between 0 and 100 % with 0 % being solid filled, 100 %
     * being completely empty.
     */
    public double getVoiding(double referenceEffort) {
        return phasedHandler.getVoiding(referenceEffort);
    }

    /**
     * Returns the temperature inside the element.
     *
     * @return Temperature in Kelvin
     */
    public double getTemperature() {
        return phasedHandler.getTemperature();
    }
}
