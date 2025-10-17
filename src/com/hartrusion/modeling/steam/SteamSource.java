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
import com.hartrusion.modeling.general.Enforcer;
import com.hartrusion.modeling.general.GeneralNode;

/**
 * Represents a source of steam with fixed mass flow and pressure. As steam will
 * have bound properties from the steam table, generating it with conventional,
 * electronic circuit like general elements would require to determine
 * unrealistic or illegal steam states not supported by the SteamTable, any
 * steam source has to be absolute like this.
 *
 * <p>
 * The steam properties have to be set on the steam handler by using the
 * getSteamHandler() method.
 *
 * @author Viktor Alexander Hartung
 */
public class SteamSource extends Enforcer implements SteamElement {

    private final SteamConnectionHandler steamHandler;

    private SteamNode steamPort;

    /**
     * State of steam that will be enforced on the port connection.
     */
    private double[] steamProperties
            = new double[SteamNode.STEAM_PROPERTIES_LENGTH];

    SteamTable propertyTable;

    public SteamSource(SteamTable propertyTable) {
        super(PhysicalDomain.STEAM);
        this.propertyTable = propertyTable;
        this.steamHandler = new SteamConnectionHandler(this);
    }

    @Override
    public void registerNode(GeneralNode p) {
        // only one heatport and one steamport are allowed here, check this
        // and save array indizies.
        if (p instanceof SteamNode && steamPort == null) {
            steamPort = (SteamNode) p;
            steamHandler.registerSteamPort(steamPort);
        } else {
            throw new ModelErrorException(
                    "Port seems to be of wrong type or type already added.");
        }
        super.registerNode(p);
    }

    @Override
    public void prepareCalculation() {
        super.prepareCalculation(); // this will reset the ports
        steamHandler.prepareSteamCalculation();
    }

    @Override
    public boolean doCalculation() {
        boolean didSomething;
        didSomething = super.doCalculation();
        didSomething = didSomething || steamPort.doCalculateSteamProperties();

        if (!steamPort.steamPropertiesUpdated(this)
                && steamPort.flowUpdated(this)) {
            if (flow == 0.0) {
                steamPort.setNoSteamProperties(this);
            } else {
                steamHandler.setSteamProperties(steamProperties);
            }
            didSomething = true;
        }
        return didSomething;
    }

    @Override
    public boolean isCalculationFinished() {
        return super.isCalculationFinished()
                && steamHandler.isSteamCalulationFinished();
    }

    public void setWaterParameters(double pressure, double temperature) {
        if (propertyTable.get("TSat_p", pressure) < temperature) {
            throw new ModelErrorException("Expected water but steam was "
                    + "provided.");
        }
        effort = pressure;
        steamProperties[0] = temperature;
        steamProperties[1] = propertyTable.get("h_pT", 
                pressure, steamProperties[0]);
        steamProperties[2] = propertyTable.get("s_ph", 
                pressure, steamProperties[1]);
        steamProperties[3] = 0.0;
    }

    public void setSteamParameters(double pressure, double enthalpy) {
        effort = pressure;
        steamProperties[0] = propertyTable.get("T_ph", pressure, enthalpy);
        steamProperties[1] = enthalpy;
        steamProperties[2] = propertyTable.get("s_ph", pressure, enthalpy);
        steamProperties[3] = propertyTable.get("x_ph", effort, enthalpy);
    }

    @Override
    public void setEffort(double effort, SteamNode source) {
        throw new ModelErrorException(
                "Force effort value on enforcer is illegal.");
    }

    @Override
    public void setEffort(double e) {
        throw new ModelErrorException(
                "Use only setSteamParameters on SteamSource to ensure "
                + "consistent steam parameters.");
    }

    @Override
    public void setFlow(double flow, SteamNode source) {
        throw new ModelErrorException(
                "Force effort value on enforcer is illegal.");
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
        if (idx != 0) {
            throw new ModelErrorException("This converter never has more "
                    + "than one port.");
        }
        return steamPort;
    }
}
