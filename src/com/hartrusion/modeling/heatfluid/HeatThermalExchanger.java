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

import com.hartrusion.modeling.initial.TemperatureInitialCondition;
import com.hartrusion.modeling.ElementType;
import com.hartrusion.modeling.PhysicalDomain;
import com.hartrusion.modeling.general.EffortSource;
import com.hartrusion.modeling.general.GeneralNode;
import com.hartrusion.modeling.initial.AbstractInitialCondition;
import com.hartrusion.modeling.general.OpenOrigin;
import com.hartrusion.modeling.initial.InitialConditions;

/**
 * Heat-Thermal-Exchanger element which is basically one side of a heat fluid
 * heat exchanger. The element itself is a heat element, meaning it has two
 * ports for heat fluid to flow through but no hydraulic properties itself
 * (therefore it extends HeatPassive). It represents a volume that can be heated
 * up or cooled down by adding elements and ports to a thermal domain system.
 *
 * <p>
 * It has an instance of an open origin and an effort source that will be
 * connected to a thermal system. This effort source serves as an interface
 * between the two domains, as both domains do not exchange any physical flow
 * with each other or exchange any pressure. The effort source has to be
 * connected to a thermal resistor.
 *
 * <p>
 * To model a heat exchanger for two heat fluid flows, this needs to be
 * instanciated twice for each side and connected with the origin elements with
 * a resistance in the thermal region. There is also a non linear resistance
 * available for this purpose, representing a more realistic behaviour.
 *
 * <p>
 * It is possible to create the thermal elements externally and just set them to
 * this class, the class just needs tho be connected to a thermal effort source
 * which serves as a connection between the domains towards the thermal system.
 *
 * <p>
 * The easy way is just to call initComponent(), this will create the thermal
 * elements and the thermal source will also be called from this element.
 *
 * @author Viktor Alexander Hartung
 */
public class HeatThermalExchanger extends HeatPassive
        implements InitialConditions {

    private OpenOrigin thermalOrigin;
    private EffortSource thermalEffortSource;
    private GeneralNode thermalNode;

    private final HeatThermalVolumeHandler heatHandler;

    public HeatThermalExchanger() {
        elementType = ElementType.BRIDGED;
        heatHandler = new HeatThermalVolumeHandler(this);
        // HeatPassive requires to have this interface used with the used
        // heat handler instance.
        heatHandlerInterface = heatHandler;
    }

    /**
     * Creates a minimum set of elements with a thermal effort source. It will
     * create and connect:
     *
     * <ul>
     * <li>Thermal open Origin</li>
     * <li>General Port</li>
     * <li>Thermal Effort Source</li>
     * </ul>
     *
     * <p>
     * The thermal effort source will be used as a connection to a thermal
     * network from the heat domain.
     *
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
        heatHandler.setThermalEffortSource(thermalEffortSource);
    }

    @Override
    public void prepareCalculation() {
        // thermalEffortSource.resetEffortUpdated();
        super.prepareCalculation(); // also calls heathandler prepare calc
        if (thermalOrigin != null) {
            thermalOrigin.prepareCalculation();
        }
        thermalEffortSource.prepareCalculation();
    }

    @Override
    public boolean doCalculation() {
        // add the call to the integrated thermal origin here. Call it first
        // to have the enforced thermal properties on the nodes early.
        if (thermalOrigin != null) {
            return thermalOrigin.doCalculation()
                    || thermalEffortSource.doCalculation()
                    || super.doCalculation(); // also calls heathandler calcul.
        } else {
            return thermalEffortSource.doCalculation()
                    || super.doCalculation(); // also calls heathandler calcul.
        }
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
        heatHandler.setThermalEffortSource(thermalEffortSource);
        setCoupledElement(s);
    }

    @Override
    public void setStepTime(double dt) {
        heatHandler.setStepTime(dt);
    }

    /**
     * Sets a specific heat capacity. The default value is water, only needed if
     * the default value (4200 J/kg/K) is not sufficient.
     *
     * @param c J/kg/K - note the SI units, usually its given in kJ
     */
    public void setSpecificHeatCapacity(double c) {
        heatHandler.setSpecificHeatCapacity(c);
    }

    @Override
    public AbstractInitialCondition getState() {
        TemperatureInitialCondition ic = new TemperatureInitialCondition();
        ic.setElementName(elementName);
        ic.setTemperature(heatHandler.getTemperature());
        return ic;
    }

    @Override
    public void setInitialCondition(AbstractInitialCondition ic) {
        checkInitialConditionName(ic);
        heatHandler.setInitialTemperature(
                ((TemperatureInitialCondition) ic).getTemperature());
    }
}
