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

import com.hartrusion.modeling.heatfluid.HeatConnectionHandler;
import com.hartrusion.modeling.exceptions.ModelErrorException;
import com.hartrusion.modeling.ElementType;
import com.hartrusion.modeling.PhysicalDomain;
import com.hartrusion.modeling.exceptions.CalculationException;
import com.hartrusion.modeling.general.FlowThrough;
import com.hartrusion.modeling.general.GeneralNode;
import com.hartrusion.modeling.heatfluid.HeatElement;
import com.hartrusion.modeling.heatfluid.HeatHandler;
import com.hartrusion.modeling.heatfluid.HeatNode;
import com.hartrusion.modeling.steam.SteamConnectionHandler;
import com.hartrusion.modeling.steam.SteamElement;
import com.hartrusion.modeling.steam.SteamHandler;
import com.hartrusion.modeling.steam.SteamNode;
import com.hartrusion.modeling.steam.SteamTable;

/**
 * Servers as an interface between HeatFluid elements and steam elements. Use to
 * connect both domains.
 *
 * <p>
 * As both heat fluid and steam domain use mass flows, the flow variable does
 * not need to be converted in any way. On the steam domain, we will calculate
 * the remaining properties and add them to the steam properties, the heat
 * domain basically looses the informationen.
 *
 * <p>
 * Heat fluid is liquid or gas only. If there is parts of the steam already
 * vaporized, a virtual cheating temperature is calculated using the specific
 * heat capacity of the steam. The additional enthalpy that is stored in the
 * steam above the liquid state is then added as a temperature value of the heat
 * fluid. For example, if you would have steam at 2.8 bars and 300 deg Celsius,
 * saturation temperature was 131.2 deg Celsius here and the maximum enthalpy of
 * the liquid part was 551.46 kJ/kg. With the total spec enthalpy of 3070.1
 * kJ/kg, there is more energy in the steam than it could transport in liquid
 * phase, its 2518.6 kJ/kg too much. With a specific heat capacity of the fluid
 * of 4.2672 kJ/kg/K this would be a temperature increase of h/c = 590 Kelvin.
 * The heat fluid will therefore have a temperture of 131.2 + 590 = 721.1 deg
 * Celsius. This sounds ridicolous first, but this acutally allows mixing the
 * heat fluid into a larger reservoir where we assume constant heat capacity
 * anyway so adding the stream of this will lead to an increase of the
 * temperature as it would do when the fluid condenses.
 *
 * <p>
 * Note that this element implements both interfaces from heat and from the
 * Steam domain, the setEffort and setFlow methods are overloaded by specifying
 * the proper type of port.
 *
 * @author Viktor Alexander Hartung
 */
public class SteamHeatFluidConverter extends FlowThrough
        implements HeatElement, SteamElement {

    /**
     * Node connected to the steam domain
     */
    SteamNode steamNode;

    /**
     * Node connected to the heat domain
     */
    HeatNode heatNode;

    private int steamNodeIndex = -1;
    private int heatNodeIndex = -1;

    SteamTable propertyTable;

    /**
     * Usually the heat fluid is liquid, if a transport of gas is however
     * desired, it will be described with this variable. There is no way to set
     * this to true yet as it seems to be illegal to do this as the behaviour of
     * steam does not allow the assumptions we did in the heat fluid domain.
     */
    private boolean gasPhase;

    HeatConnectionHandler heatHandler = new HeatConnectionHandler(this);
    SteamConnectionHandler steamHandler = new SteamConnectionHandler(this);

    public SteamHeatFluidConverter(SteamTable propertyTable) {
        super(PhysicalDomain.MULTIDOMAIN);
        setElementType(ElementType.BRIDGED);
        this.propertyTable = propertyTable;
    }

    @Override
    public void registerNode(GeneralNode p) {
        // only one heatport and one steamport are allowed here, check this
        // and save array indizies.
        if (p instanceof SteamNode && steamNodeIndex == -1) {
            steamNodeIndex = nodes.size();
            steamNode = (SteamNode) p;
            steamHandler.registerSteamPort(steamNode);
        } else if (p instanceof HeatNode && heatNodeIndex == -1) {
            heatNodeIndex = nodes.size();
            heatNode = (HeatNode) p;
            heatHandler.registerHeatNode(heatNode);
        } else {
            throw new ModelErrorException(
                    "Port seems to be of wrong type or type already added.");
        }
        super.registerNode(p);
    }

    @Override
    public boolean doCalculation() {
        boolean didSomething = false;
        // check if only one of 2 effort values are updated. update the other 
        // one if necessary
        if (heatNode.effortUpdated() && !steamNode.effortUpdated()) {
            steamNode.setEffort(heatNode.getEffort());
            didSomething = true;
        } else if (!heatNode.effortUpdated() && steamNode.effortUpdated()) {
            heatNode.setEffort(steamNode.getEffort());
            didSomething = true;
        }

        // usually we would put the whole calculation in the heat and steam
        // handler but since we have two one-port handlers here, a few things
        // are handled here and the doHandlerCalculation methods will only get
        // called if needed.
        didSomething = didSomething || converterCalculation();

        didSomething = didSomething || steamNode.doCalculateSteamProperties();
        didSomething = didSomething || heatNode.doCalculateTemperature();

        return didSomething;
    }

    private boolean converterCalculation() {
        boolean didSomething = false;
        double thetaSat, hLiquid, deltaH, specHeatCap, dTVirt;
        // check if flow is known
        if (!heatNode.flowUpdated(this) && !steamNode.flowUpdated(this)) {
            return false; // at least one flow needs to be known
        }
        // if one flow is missing, set the other one - this should never
        // be needed as the flow should set itself through automatically
        // but we do not know if reare cases happen where the nodes get
        // some flow assigned by a slover, so we will add a few lines here
        // to handle such occasions
        if (heatNode.flowUpdated(this) && !steamNode.flowUpdated(this)) {
            steamNode.setFlow(-heatNode.getFlow(this), this, false);
            didSomething = true;
        } else if (!heatNode.flowUpdated(this) && steamNode.flowUpdated(this)) {
            heatNode.setFlow(-steamNode.getFlow(this), this, false);
            didSomething = true;
        }
        // now, if flows are konw, we can set some properties for steam or heat.
        if (heatNode.getFlow(this) == 0.0 && steamNode.getFlow(this) == 0.0) {
            // Special case: Zero flow.
            if (!heatNode.temperatureUpdated(this)) {
                heatNode.setNoTemperature(this);
                didSomething = true;
            }
            if (!steamNode.steamPropertiesUpdated(this)) {
                steamNode.setNoSteamProperties(this);
                didSomething = true;
            }
            return didSomething;
        }
        if (heatNode.getFlow(this) > 0.0 && steamNode.getFlow(this) < 0.0
                && heatNode.effortUpdated()
                && !steamHandler.isSteamCalulationFinished()) {
            // flow comes from heat and goes to steam domain. for this we
            // also need updated effort value, as this is needed for enthalpy
            // value calculation.
            if (heatNode.temperatureUpdated(this)
                    && !steamNode.steamPropertiesUpdated(this)) {
                // It is possible, even if a flow is there, that there is a 
                // no-temperature flag due to flow not beeing exactly zero.
                if (heatNode.noTemperature(this)) {
                    steamNode.setNoSteamProperties(this);
                } else {
                    steamHandler.setSteamFromConverter(heatNode.getEffort(),
                            heatNode.getTemperature(this));
                }
                // usually this should return true as calculation of handler
                // should be finished now but we cant say we wont change the
                // implementation later, so lets still request the state
                // from handler.
                return didSomething || steamHandler.isSteamCalulationFinished();
            }
        } else if (heatNode.getFlow(this) < 0.0
                && steamNode.getFlow(this) > 0.0
                && !heatHandler.isHeatCalulationFinished()) {
            // flow goes from steam domain to heat domain
            if (!heatNode.temperatureUpdated(this)
                    && steamNode.steamPropertiesUpdated(this)) {
                if (steamNode.getSteamProperty(3, this) > 0.0 && !gasPhase) {
                    // Calculate virtual temperature. The liquid part will
                    // stay as it is, get the temperature and the sp. enthalpy
                    thetaSat = propertyTable.get("TSat_p",
                            steamNode.getEffort());
                    hLiquid = propertyTable.get("hLiq_p",
                            steamNode.getEffort());
                    // Calculate the remaining amount of enthalpy that is not
                    // stored in the liquid part of the water.
                    deltaH = steamNode.getSteamProperty(1, this) - hLiquid;
                    // Use the specific heat capacity from the steam as we would
                    // use the steam table inlcluding this value elsewhere also.
                    specHeatCap = propertyTable.get("c_ph",
                            steamNode.getEffort(), hLiquid);
                    // Calculate the temperature increase that would be there
                    // if there would be no vaporization.
                    dTVirt = deltaH / specHeatCap;
                    heatHandler.setTemperatureFromConverter(thetaSat + dTVirt);
                } else {
                    if (gasPhase) {
                        if (steamNode.getSteamProperty(3, this) < 1.0) {
                            // this would be wrong as we assume we only
                            // transport gas.
                            throw new ModelErrorException("Non gas-phased steam"
                                    + "but converter is set to gas only.");
                        }
                    }
                    // Just use the temperature as it is present in steam domain
                    heatHandler.setTemperatureFromConverter(steamNode.getSteamProperty(0, this));
                }
            }
        } else if (heatNode.getFlow(this) < 0.0
                && steamNode.getFlow(this) < 0.0
                || heatNode.getFlow(this) > 0.0
                && steamNode.getFlow(this) > 0.0) {
            throw new CalculationException("Inconsistent flow direction");
        }
        return didSomething;
    }

    @Override
    public void setStepTime(double dt) {
        // This is physically not needed here but required by interface
    }

    @Override
    public void prepareCalculation() {
        super.prepareCalculation(); // this will reset the nodes
        heatHandler.prepareHeatCalculation();
        steamHandler.prepareSteamCalculation();
    }

    @Override
    public boolean isCalculationFinished() {
        return super.isCalculationFinished()
                && heatHandler.isHeatCalulationFinished()
                && steamHandler.isSteamCalulationFinished();
    }

    @Override
    public HeatHandler getHeatHandler() {
        return heatHandler;
    }

    @Override
    public SteamHandler getSteamHandler() {
        return steamHandler;
    }

    @Override
    public void setEffort(double effort, HeatNode source) {
        steamNode.setEffort(effort);
    }

    @Override
    public void setEffort(double effort, SteamNode source) {
        heatNode.setEffort(effort);
    }

    @Override
    public void setFlow(double flow, HeatNode source) {
        steamNode.setFlow(-flow, this, true);
    }

    @Override
    public void setFlow(double flow, SteamNode source) {
        heatNode.setFlow(-flow, this, true);
    }

    @Override
    public SteamTable getSteamTable() {
        return propertyTable;
    }

    @Override
    public SteamNode getSteamNode(int idx) {
        if (idx != 0) {
            throw new ModelErrorException("This converter never has more "
                    + "than one port.");
        }
        return steamNode;
    }

}
