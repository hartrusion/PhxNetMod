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
package com.hartrusion.values;

import static com.hartrusion.util.ArraysExt.*;

/**
 * Holds a primitive value with arrays holding the previous values. Intended to 
 * be used by the ParameterHandler. Setting a value will insert the value in 
 * the history arrays.
 * 
 * @author Viktor Alexander Hartung
 */
public class PrimitiveDoubleValue extends AbstractValue {

    private double value;

    private byte divCount2 = 1;
    private byte divCount5 = 1;
    private byte divCount10 = 1;
    private byte divCount20 = 1;
    private byte divCount50 = 1;

    private float[] values1 = new float[601];
    private float[] values2 = new float[601];
    private float[] values5 = new float[601];
    private float[] values10 = new float[601];
    private float[] values20 = new float[601];
    private float[] values50 = new float[601];
    
    PrimitiveDoubleValue() {
        // initialize all with NaN, used to mark that there is no value 
        // available yet.
        for (int idx = 0; idx < 601; idx++) {
            values1[idx] = Float.NaN;
            values2[idx] = Float.NaN;
            values5[idx] = Float.NaN;
            values10[idx] = Float.NaN;
            values20[idx] = Float.NaN;
            values50[idx] = Float.NaN;
            
        }
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
        float fValue = (float) value;

        leftShiftInsert(values1, fValue);

        // Decrement them all, note that they start initialized with "1"
        divCount2--;
        divCount5--;
        divCount10--;
        divCount20--;
        divCount50--;

        // insert into arrays on each nTh set operation.
        if (divCount2 <= 0) {
            leftShiftInsert(values2, fValue);
            divCount2 = 2;
        }
        if (divCount5 <= 0) {
            leftShiftInsert(values5, fValue);
            divCount5 = 5;
        }
        if (divCount10 <= 0) {
            leftShiftInsert(values10, fValue);
            divCount10 = 10;
        }
        if (divCount20 <= 0) {
            leftShiftInsert(values20, fValue);
            divCount20 = 20;
        }
        if (divCount50 <= 0) {
            leftShiftInsert(values50, fValue);
            divCount50 = 50;
        }
    }
    
    public float[] getValues1() {
        return values1;
    }

    public float[] getValues2() {
        return values2;
    }

    public float[] getValues5() {
        return values5;
    }

    public float[] getValues10() {
        return values10;
    }

    public float[] getValues20() {
        return values20;
    }

    public float[] getValues50() {
        return values50;
    }
}
