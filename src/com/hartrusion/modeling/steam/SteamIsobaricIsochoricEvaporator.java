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

import com.hartrusion.modeling.ElementType;
import com.hartrusion.modeling.PhysicalDomain;
import com.hartrusion.modeling.general.AbstractElement;
import com.hartrusion.modeling.general.EffortSource;
import com.hartrusion.modeling.general.GeneralNode;
import com.hartrusion.modeling.initial.AbstractIC;
import com.hartrusion.modeling.general.OpenOrigin;

/**
 * Represents a fixed, non-compressible volume that is can be heated up and
 * expand but not contract. This component is intended to represent a heat
 * transfer to a steam system where a change in thermal heat transfer will lead
 * to an expansion of the flud.
 *
 * <p>
 * Intended use is an evaporator. It has massive problems still as its using the
 * specific volume.
 *
 * <p>
 * It is limited to an expansion and due to the way of what can be calculated
 * with the steam table, it can not contract and "suck in" from the output by
 * itself.
 *
 * <p>
 * It is recommended to use initComponent() to set up most parts of the element.
 * The thermalEffortSource has then to be connected further to a heat network.
 *
 * <p>
 * Note that any solver must not set the flow on neighbour elements to allow the
 * element itself to set one flow.
 *
 * @author Viktor Alexander Hartung
 */
public class SteamIsobaricIsochoricEvaporator extends AbstractElement
        implements SteamElement {

    private OpenOrigin thermalOrigin;
    private EffortSource thermalEffortSource;
    private GeneralNode thermalNode;

    SteamIsobaricIsochoricThermalTransferHandler steamHandler;

    SteamTable propertyTable;

    public SteamIsobaricIsochoricEvaporator(SteamTable propertyTable) {
        super(PhysicalDomain.STEAM);
        elementType = ElementType.BRIDGED;
        this.propertyTable = propertyTable;
        steamHandler = new SteamIsobaricIsochoricThermalTransferHandler(
                this, propertyTable);
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
     * It also sets up the coupling info.
     *
     * <p>
     * Use of this method is optional, it just makes network setup easier as
     * thermal networks usually are super small. You can also do a full network
     * build yourself and use the setThermalEffortSource instead.
     */
    public void initComponent() {
        // Create thermal elements
        thermalOrigin = new OpenOrigin(PhysicalDomain.THERMAL);
        thermalEffortSource = new EffortSource(PhysicalDomain.THERMAL);
        // Set the couling references here also
        setCoupledElement(thermalEffortSource);
        if (thermalEffortSource.getCoupledElement() == null) {
            thermalEffortSource.setCoupledElement(this);
        }
        thermalNode = new GeneralNode(PhysicalDomain.THERMAL);

        // connect thermal elements
        thermalNode.registerElement(thermalOrigin);
        thermalOrigin.registerNode(thermalNode);
        thermalNode.registerElement(thermalEffortSource);
        thermalEffortSource.registerNode(thermalNode);

        // make source known to the steam handler
        steamHandler.setThermalEffortSource(thermalEffortSource);
    }

    @Override
    public void prepareCalculation() {
        // thermalEffortSource.resetEffortUpdated();
        super.prepareCalculation(); // calls prep calc on all nodes
        steamHandler.prepareSteamCalculation();
        if (thermalOrigin != null) {
            thermalOrigin.prepareCalculation();
        }
        thermalEffortSource.prepareCalculation();
    }

    @Override
    public boolean doCalculation() {
        boolean didSomething = false;

        if ((nodes.get(0).flowUpdated(this)
                || nodes.get(1).flowUpdated(this))
                && nodes.get(0).effortUpdated()) {
            // calling this without known effort and flow will
            // not do anything
            didSomething = steamHandler.doSteamCalculation() || didSomething;
        }
        // Call thermal elements part from here too
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
        return super.isCalculationFinished()
                && steamHandler.isSteamCalulationFinished();
    }

    /**
     * Setup and initialize the component. Initial conditions have to be
     * provided as the element stores thermal energy in the steam volume.
     *
     * @param totalVolume
     * @param pressure
     * @param enthalpy
     */
    public void setInitialState(double totalVolume, double pressure,
            double enthalpy) {

        steamHandler.setVolume(totalVolume);

        steamHandler.setSteamProperty(0,
                propertyTable.get("T_ph", pressure, enthalpy));
        steamHandler.setSteamProperty(1, enthalpy);
        double entropy = propertyTable.get("s_ph", pressure, enthalpy);
        steamHandler.setSteamProperty(2, entropy);

        steamHandler.setSteamProperty(3,
                propertyTable.get("x_ph", pressure, enthalpy));

//        steamHandler.setTotalMass(
//                totalVolume / propertyTable.specificVolumeHS(enthalpy, entropy));
        steamHandler.setTotalMass(
                totalVolume / propertyTable.get("v_ph", pressure, enthalpy));
    }

    @Override
    public void registerNode(GeneralNode p) {
        super.registerNode(p);
        steamHandler.registerSteamPort((SteamNode) p);
    }

    @Override
    public void setStepTime(double dt) {
        steamHandler.setStepTime(dt);
    }

    @Override
    public void setEffort(double effort, GeneralNode source) {
        // one set effort sets it to all others
        for (GeneralNode p : nodes) {
            if (!p.effortUpdated()) {
                p.setEffort(effort, this, true);
            }
        }
    }

    @Override
    public void setEffort(double effort, SteamNode source) {
        setEffort(effort, (GeneralNode) source); // redirect
    }

    @Override
    public void setFlow(double flow, GeneralNode source) {
        // nothing to do, doCalculation will distribute flow.
    }

    @Override
    public void setFlow(double flow, SteamNode source) {
        // nothing to do, doCalculation will distribute flow.
    }

    @Override
    public SteamHandler getSteamHandler() {
        return steamHandler;
    }

    @Override
    public SteamTable getSteamTable() {
        return propertyTable;
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

    public void setThermalEffortSource(EffortSource s) {
        thermalEffortSource = s;
        setCoupledElement(s);
    }

    @Override
    public SteamNode getSteamNode(int idx) {
        return ((SteamNode) nodes.get(idx));
    }
}
