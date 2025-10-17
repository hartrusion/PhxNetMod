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

/**
 * This interface has to be implemented by a steam table for use with the steam
 * domain of the modeling package. It contains all functions that the steam
 * modeling package needs to make calulations with a two phased fluid. Usually
 * this interface will be immplemented by a wrapper class that then calls a
 * steam table library, when creating elements for the steam domain, a reference
 * to such a wrapper class will be provided.
 * 
 * <ul>
 * <li>TSat_p</li>
 * <li>pSat_T</li>
 * <li>hLiq_p</li>
 * <li>hSteam_p</li>
 * <li>vLiq_p</li>
 * <li>vSteam_p</li>
 * <li>sLiq_p - Entropy of saturated liqid</li>
 * <li>sSteam_p - Entropy of saturated steam</li>
 * </ul>
 * 
 * 
 * <ul>
 * <li>T_ph</li>
 * <li>p_hs</li>
 * <li>h_px</li>
 * <li>h_pT</li>
 * <li>h_sv - expensive</li>
 * <li>s_ph</li>
 * <li>s_px</li>
 * <li>s_hv - expensive</li>
 * <li>x_ph</li>
 * <li>x_hs</li>
 * <li>v_ph</li>
 * <li>v_hs</li>
 * <li>c_ph</li>
 * </ul>
 *
 * @author Viktor Alexander Hartung
 */
public interface SteamTable {
    
    public double get(String function, double arg);
    
    public double get(String function, double arg1, double arg2);
}
