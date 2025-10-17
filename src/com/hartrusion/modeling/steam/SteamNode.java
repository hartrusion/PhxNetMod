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

import java.util.ArrayList;
import java.util.List;
import com.hartrusion.modeling.PhysicalDomain;
import com.hartrusion.modeling.exceptions.CalculationException;
import com.hartrusion.modeling.general.AbstractElement;
import com.hartrusion.modeling.general.GeneralNode;

/**
 * Extends node for capability to handle steam. It is assumed that all steam
 * that goes into the port mixes perfectly and leaves to all elements which have
 * flows towards them in the same condition.
 *
 * <p>
 * It is assumed that the effort variable represents the absolute pressure in Pa
 * (SI units). Flow variable represents the mass flow in kg/s. This has to be
 * set as the steam domain is nonlinear and things do not just scale up anymore.
 *
 * @author Viktor Alexander Hartung
 */
public class SteamNode extends GeneralNode {

    /**
     * Steam properties are stored as doubles in an array. Those properties will
     * be used from ports and elements and are therefore the length of those
     * properties is defined here, all elements will know the node class so this
     * is available.
     * <p>
     * As for now, steam properties in that array are:
     * <ul>
     * <li>Temperature (K)</li>
     * <li>Specific Enthalpy (J/kg)</li>
     * <li>Specific Entropy (J/kg/K)</li>
     * <li>Vapour Fraction (0..1)</li>
     * </ul>
     * As these properties are the most used.
     *
     */
    public static final int STEAM_PROPERTIES_LENGTH = 4;

    private final List<SteamPropertiesContainer> steamProps = new ArrayList<>();

    private SteamTable propertyTable;

    public SteamNode(SteamTable propertyTable) {
        super(PhysicalDomain.STEAM);
        this.propertyTable = propertyTable;
    }

    @Override
    public void registerElement(AbstractElement el) {
        super.registerElement(el);
        steamProps.add(new SteamPropertiesContainer());
    }

    @Override
    public void prepareCalculation() {
        super.prepareCalculation();
        for (SteamPropertiesContainer sp : steamProps) {
            sp.resetSteamStateUpdated();
        }
    }

    /**
     * Sets the steam properties to or from this port regarding a connected
     * element and marks the properties for this element as updated.
     *
     * @param temperature
     * @param enthalpy
     * @param entropy
     * @param vapourFraction
     * @param source
     */
    public void setSteamProperties(double temperature, double enthalpy,
            double entropy, double vapourFraction, AbstractElement source) {
        SteamPropertiesContainer steamProp
                = steamProps.get(connectedElements.indexOf(source));
        steamProp.setSteamProperty(0, temperature);
        steamProp.setSteamProperty(1, enthalpy);
        steamProp.setSteamProperty(2, entropy);
        steamProp.setSteamProperty(3, vapourFraction);
        steamProp.setSteamPropertiesUpdated();
    }

    /**
     * Sets the steam properties to or from this port regarding a connected
     * element and marks the properties for this element as updated. The
     * properties are: Temperature, spec. Enthalpy, spec. Entropy, VapFract.
     *
     * @param properties array of properties
     * @param source corresponding element
     */
    public void setSteamProperties(double[] properties,
            AbstractElement source) {
        SteamPropertiesContainer steamProp
                = steamProps.get(connectedElements.indexOf(source));
        steamProp.setSteamProperty(0, properties[0]);
        steamProp.setSteamProperty(1, properties[1]);
        steamProp.setSteamProperty(2, properties[2]);
        steamProp.setSteamProperty(3, properties[3]);
        steamProp.setSteamPropertiesUpdated();
    }

    /**
     * Get a specified property of steam towards of from a specified element.
     *
     * @param index Property to get (0: Temp, 1: Enthalpy, 2: Entropy, 3: Vapour
     * Fraction)
     * @param source Element which is connected to this port
     * @return Value of property or NaN if the property is not there.
     */
    public double getSteamProperty(int index, AbstractElement source) {
        if (steamProps.get(connectedElements.indexOf(source))
                .hasNoSteamProperties()) {
            return Double.NaN;
        }
        return steamProps.get(connectedElements.indexOf(source))
                .getSteamProperty(index);
    }

    public void setNoSteamProperties(AbstractElement source) {
        steamProps.get(connectedElements.indexOf(source)).setNoSteamValues();
    }

    public boolean steamPropertiesUpdated(AbstractElement e) {
        return steamProps.get(connectedElements.indexOf(e)).isSteamUpdated();
    }

    public boolean noSteamPropoerties(AbstractElement e) {
        return steamProps.get(connectedElements.indexOf(e))
                .hasNoSteamProperties();
    }

    public boolean doCalculateSteamProperties() {
        double inFlow = 0.0; // sum of flows into the port
        boolean allInSteamUpdated; // all steam values into port are updated
        boolean allFlowsZero; // no flow, valves closed or smthn.
        double enthalpyIn = 0.0;
        double entropyIn = 0.0;
        double avgEnthalpy;
        boolean didSomething = false;

        if (!allFlowsUpdated()) {
            return false;
        }

        // to avoid calling the expensive steam table caluclation too often,
        // the case of having just two connections is handled here separately.
        if (flowThrough) {
            if (getFlow(0) == 0.0 && getFlow(1) == 0.0) {
                // special case: there is no flow, so there can not be any
                // steam property.
                if (!steamProps.get(0).isSteamUpdated()) {
                    steamProps.get(0).setNoSteamValues();
                    didSomething = true;
                }
                if (!steamProps.get(1).isSteamUpdated()) {
                    steamProps.get(1).setNoSteamValues();
                    didSomething = true;
                }
                return didSomething;
            }
            // the steam property is the same on both connections on this port.
            // if only one of the two is known, set the same property to the
            // other element.
            if (!steamProps.get(0).isSteamUpdated()
                    && steamProps.get(1).isSteamUpdated()) {
                for (int idx = 0; idx < STEAM_PROPERTIES_LENGTH; idx++) {
                    steamProps.get(0).setSteamProperty(idx,
                            steamProps.get(1).getSteamProperty(idx));
                }
                steamProps.get(0).setSteamPropertiesUpdated();
                didSomething = true;
            } else if (steamProps.get(0).isSteamUpdated()
                    && !steamProps.get(1).isSteamUpdated()) {
                for (int idx = 0; idx < STEAM_PROPERTIES_LENGTH; idx++) {
                    steamProps.get(1).setSteamProperty(idx,
                            steamProps.get(0).getSteamProperty(idx));
                }
                steamProps.get(1).setSteamPropertiesUpdated();
                didSomething = true;
            }
            return didSomething; // this ends the method for flowThrough.
        }

        // First, a rare case with all flows exactly 0.0 - this can happen
        // if model was just started or if flow is forced to zero by a
        // closed valve. Closed valves will force a double value of 0.0
        // so it is valid to compare a double with an exact value here.
        allFlowsZero = true; // init
        for (int idx = 0; idx < connectedElements.size(); idx++) {
            allFlowsZero = allFlowsZero && (getFlow(idx) == 0.0);
        }
        if (allFlowsZero) { // all zero means no steam properties everywhere.
            for (int idx = 0; idx < connectedElements.size(); idx++) {
                if (!steamProps.get(idx).isSteamUpdated()) {
                    steamProps.get(idx).setNoSteamValues();
                    didSomething = true;
                }
            }
            return didSomething; // return! Method is finished!
        }

        if (!effortUpdated || flowThrough) {
            return didSomething; // finished or pressure unknown
        }

        // For calculating the out steam properties, all incoming props have
        // to be in a calculated state 
        allInSteamUpdated = true; // init
        for (int idx = 0; idx < connectedElements.size(); idx++) {
            if (getFlow(idx) < 0.0) {
                allInSteamUpdated = allInSteamUpdated
                        && steamProps.get(idx).isSteamUpdated()
                        && !steamProps.get(idx).hasNoSteamProperties();
                if (!allInSteamUpdated) {
                    // no need to continue here as these values will not be
                    // of any use when allInTempsUpdated is false.
                    break;
                }
                inFlow = inFlow - getFlow(idx);
                // sum up specific enthalpy * mass flow, kJ/kg * kg/s = kJ/s
                // enthalpy. note that getFlow(idx) will return a negative
                // value so to sum up, we will use minus. This is due to the
                // fact that incoming flows to a port have a negative value.
                enthalpyIn = enthalpyIn - getFlow(idx)
                        * steamProps.get(idx).getSteamProperty(1);
                // Same goes for entropy
                entropyIn = entropyIn - getFlow(idx)
                        * steamProps.get(idx).getSteamProperty(2);
            }
        }
        if (allInSteamUpdated) {
            // all leaving flows have that same, mixed enthalpy, what goes in 
            // gets mixed and leaves with same enthalpy and differen mass flows.
            avgEnthalpy = enthalpyIn / inFlow;
            // assign those values to all leaving flows
            for (int idx = 0; idx < connectedElements.size(); idx++) {
                if (getFlow(idx) > 0.0
                        & !steamProps.get(idx).isSteamUpdated()) {
                    steamProps.get(idx).setSteamProperty(0,
                            propertyTable.get("T_ph", effort, avgEnthalpy));
                    steamProps.get(idx).setSteamProperty(1, avgEnthalpy);
                    steamProps.get(idx).setSteamProperty(2,
                            propertyTable.get("s_ph", effort, avgEnthalpy));
                    steamProps.get(idx).setSteamProperty(3,
                            propertyTable.get("x_ph", effort, avgEnthalpy));
                    steamProps.get(idx).setSteamPropertiesUpdated();
                    didSomething = true;
                }
            }
        }
        return didSomething;
    }
}

/**
 * Holds steam properties towards elements on this port. The same way flow and
 * effort values are stored in port, the steam properties towards and from the
 * element are also consequently stored in port here.
 *
 * <p>
 * Steam properties are stored in an array of values, this seems to be the most
 * easy way to handle the data.
 *
 * <p>
 * There is a condition that is called "no Value". This will happen if there is
 * no flow towards or from an element, it cant be determined what to do there.
 * As the port iself does never store steam properties, there will be no steam
 * property towards that element. It is up to the connected element if it can
 * then store steam by itself or not. The steam here is always assigned to a
 * flow so without flow, there is no steam. This is a valid condition and marks
 * the boundary of the model without making it unsolveable.
 *
 * @author Viktor Alexander Hartung
 */
class SteamPropertiesContainer {

    private boolean steamUpdated;
    private boolean noSteamValue;
    private double[] steamProperties
            = new double[SteamNode.STEAM_PROPERTIES_LENGTH];

    public boolean isSteamUpdated() {
        return steamUpdated;
    }

    public void resetSteamStateUpdated() {
        steamUpdated = false;
        noSteamValue = false;
    }

    public double getSteamProperty(int index) {
        return steamProperties[index];
    }

    public void setSteamProperty(int index, double value) {
        if (!Double.isFinite(value)) {
            throw new CalculationException("Tried to set non-finite value "
                    + "on steam properties");
        }
        steamProperties[index] = value;
    }

    public void setSteamPropertiesUpdated() {
        steamUpdated = true;
    }

    public boolean hasNoSteamProperties() {
        return noSteamValue;
    }

    public void setNoSteamValues() {
        steamUpdated = true;
        noSteamValue = true;
        steamProperties[0] = 0;
        steamProperties[1] = 0;
        steamProperties[2] = 0;
        steamProperties[3] = 1.0;
    }
}
