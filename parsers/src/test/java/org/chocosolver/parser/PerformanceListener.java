/*
 * This file is part of choco-parsers, http://choco-solver.org/
 *
 * Copyright (c) 2025, IMT Atlantique. All rights reserved.
 *
 * Licensed under the BSD 4-clause license.
 *
 * See LICENSE file in the project root for full license information.
 */
package org.chocosolver.parser;

import org.testng.ITestResult;
import org.testng.TestListenerAdapter;

import java.io.OutputStream;
import java.io.PrintStream;

/**
 * <br/>
 *
 * @author Charles Prud'homme
 * @version choco
 * @since 20/08/2014
 */
public class PerformanceListener extends TestListenerAdapter {

    PrintStream original = System.out;
    PrintStream fake = new PrintStream(new OutputStream() {
        public void write(int b) {
            //DO NOTHING
        }
    });


    @Override
    public void onTestStart(ITestResult tr) {
        System.out.printf("\t%s.%s ..", tr.getTestClass().getName(), tr.getName());
        original = System.out;
        System.setOut(fake);
    }

    @Override
    public void onTestFailure(ITestResult tr) {
        log(tr, "FAILURE");
    }

    @Override
    public void onTestSkipped(ITestResult tr) {
        log(tr, "SKIP");
    }

    @Override
    public void onTestSuccess(ITestResult tr) {
        log(tr, "SUCCESS");
    }

    private void log(ITestResult tr, String RESULT) {
        System.setOut(original);
        System.out.printf(".. %s (%dms)\n", RESULT, tr.getEndMillis() - tr.getStartMillis());
    }

}
