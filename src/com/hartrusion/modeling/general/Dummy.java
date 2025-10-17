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
package com.hartrusion.modeling.general;

import com.hartrusion.modeling.ElementType;
import com.hartrusion.modeling.PhysicalDomain;
import com.hartrusion.modeling.exceptions.ModelErrorException;

/**
 * This is a dummy element used for nothing but its own meaningless existence.
 * It will, on retrieving the element type, tell, that it is nothing. It is used
 * for some lists with synced index to not have null-elements and provide an
 * empty list entry, for example.
 *
 * @author Viktor Alexander Hartung
 */
public class Dummy extends AbstractElement {
    
    public Dummy(PhysicalDomain domain) {
        super(domain);
        elementType = ElementType.NONE;
    }

    @Override
    public boolean doCalculation() {
        return false;
    }

    @Override
    public void setStepTime(double dt) {
    }

    @Override
    public void setEffort(double effort, GeneralNode source) {
        throw new ModelErrorException("Cant set anything on a dummy");
    }

    @Override
    public void setFlow(double flow, GeneralNode source) {
        throw new ModelErrorException("Cant set anything on a dummy");
    }
}
