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
package com.hartrusion.alarm;

/**
 * 
 * @author Viktor Alexander Hartung
 */
public abstract class ComparePriority {

    /**
     * Compares two alarm states. Returns true if state2 has higher or same
     * priority as state1.
     * <p>
     * Example: Requested state1 is HIGH1 and actual state2 is MAX2, the return
     * value will be TRUE as the state2 variable is of higher or same priority
     * than state1
     *
     * @param actual actual state (can be the current alarm state)
     * @param toCompare state to compare (can be a state to check if it is
     * active)
     * @return true, if state2 has a higher priority than state1
     */
    public static boolean includes(AlarmState actual, AlarmState toCompare) {
        if (actual == toCompare) {
            return true;
        }
        switch (actual) {
            case MAX2 -> {
                switch (toCompare) {
                    case MAX1:
                    case HIGH2:
                    case HIGH1:
                        return true;
                    default:
                        return false;
                }
            }
            case MAX1 -> {
                switch (toCompare) {
                    case HIGH2:
                    case HIGH1:
                        return true;
                    default:
                        return false;
                }
            }
            case HIGH2 -> {
                switch (toCompare) {
                    case HIGH1:
                        return true;
                    default:
                        return false;
                }
            }
            case MIN2 -> {
                switch (toCompare) {
                    case MIN1:
                    case LOW2:
                    case LOW1:
                        return true;
                    default:
                        return false;
                }
            }
            case MIN1 -> {
                switch (toCompare) {
                    case LOW2:
                    case LOW1:
                        return true;
                    default:
                        return false;
                }
            }
            case LOW2 -> {
                switch (toCompare) {
                    case LOW1:
                        return true;
                    default:
                        return false;
                }
            }

        }
        return false;
    }
}
