/*
 * This file is part of choco-solver, http://choco-solver.org/
 *
 * Copyright (c) 2025, IMT Atlantique. All rights reserved.
 *
 * Licensed under the BSD 4-clause license.
 *
 * See LICENSE file in the project root for full license information.
 */
package org.chocosolver.solver.variables.view;

import org.chocosolver.memory.IStateBool;
import org.chocosolver.sat.Reason;
import org.chocosolver.solver.ICause;
import org.chocosolver.solver.exception.ContradictionException;
import org.chocosolver.solver.variables.BoolVar;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.solver.variables.Variable;
import org.chocosolver.solver.variables.delta.*;
import org.chocosolver.solver.variables.delta.monitor.OneValueDeltaMonitor;
import org.chocosolver.solver.variables.events.IEventType;
import org.chocosolver.solver.variables.events.IntEventType;
import org.chocosolver.solver.variables.impl.AbstractVariable;
import org.chocosolver.solver.variables.impl.scheduler.BoolEvtScheduler;
import org.chocosolver.solver.variables.view.bool.BoolEqView;
import org.chocosolver.util.iterators.*;
import org.chocosolver.util.objects.setDataStructures.iterable.IntIterableSet;

/**
 * An abstract class for boolean views over {@link org.chocosolver.solver.variables.IntVar}.
 *
 * @author Charles Prud'homme
 * @see BoolEqView
 * @see org.chocosolver.solver.variables.view.bool.BoolGeqView
 * <p>
 * Project: choco-solver.
 * @since 04/12/2018.
 */
public abstract class BoolIntView<I extends IntVar> extends IntView<I> implements BoolVar {

    /**
     * indicate if the view is fixed
     */
    protected IStateBool fixed;
    /**
     * A constant value
     */
    public final int cste;
    /**
     * Associate boolean variable expressing not(this)
     */
    private BoolVar not;
    /**
     * For boolean expression purpose
     */
    private boolean isNot = false;
    /**
     * To iterate over removed values
     */
    protected IEnumDelta delta = NoDelta.singleton;
    /**
     * Set to <tt>true</tt> if this variable reacts is associated with at least one propagator which
     * reacts on value removal
     */
    protected boolean reactOnRemoval = false;

    /**
     * A view based on <i>var<i/> and a constant
     *
     * @param var an integer variable
     */
    protected BoolIntView(String name, final I var, final int cste) {
        super(name, var);
        this.cste = cste;
        this.fixed = var.getModel().getEnvironment().makeBool(false);
    }

    @Override
    public final void notify(IEventType event, int variableIdx) throws ContradictionException {
        if (!fixed.get()) {
            if (isInstantiated()) {
                this.fixed.set(Boolean.TRUE);
                if (reactOnRemoval) {
                    delta.add(1 - getValue(), this);
                }
                super.notify(event, variableIdx);
            }
        }
    }

    @Override
    public final boolean removeValue(int value, ICause cause, Reason reason) throws ContradictionException {
        assert cause != null;
        if (value == kFALSE)
            return instantiateTo(kTRUE, cause, reason);
        else if (value == kTRUE)
            return instantiateTo(kFALSE, cause, reason);
        return false;
    }

    @Override
    public final boolean removeValues(IntIterableSet values, ICause cause) throws ContradictionException {
        boolean hasChanged = false;
        if (values.contains(kFALSE)) {
            hasChanged = instantiateTo(kTRUE, cause);
        }
        if (values.contains(kTRUE)) {
            hasChanged = instantiateTo(kFALSE, cause);
        }
        return hasChanged;
    }

    @Override
    public final boolean removeAllValuesBut(IntIterableSet values, ICause cause) throws ContradictionException {
        boolean hasChanged = false;
        if (!values.contains(kFALSE)) {
            hasChanged = instantiateTo(kTRUE, cause);
        }
        if (!values.contains(kTRUE)) {
            hasChanged = instantiateTo(kFALSE, cause);
        }
        return hasChanged;
    }

    @Override
    public final boolean removeInterval(int from, int to, ICause cause) throws ContradictionException {
        boolean hasChanged = false;
        if (from <= to && from <= 1 && to >= 0) {
            if (from == kTRUE) {
                hasChanged = instantiateTo(kFALSE, cause);
            } else if (to == kFALSE) {
                hasChanged = instantiateTo(kTRUE, cause);
            } else {
                this.contradiction(cause, AbstractVariable.MSG_EMPTY);
            }
        }
        return hasChanged;
    }

    @Override
    public final boolean updateLowerBound(int value, ICause cause, Reason reason) throws ContradictionException {
        assert cause != null;
        return value > kFALSE && instantiateTo(value, cause, reason);
    }

    @Override
    public final boolean updateUpperBound(int value, ICause cause, Reason reason) throws ContradictionException {
        assert cause != null;
        return value < kTRUE && instantiateTo(value, cause, reason);
    }

    @Override
    public final boolean updateBounds(int lb, int ub, ICause cause) throws ContradictionException {
        boolean hasChanged = false;
        if (lb > kTRUE || ub < kFALSE) {
            this.contradiction(cause, MSG_EMPTY);
        } else {
            if (lb == kTRUE) {
                hasChanged = instantiateTo(kTRUE, cause);
            }
            if (ub == kFALSE) {
                hasChanged = instantiateTo(kFALSE, cause);
            }
        }
        return hasChanged;
    }

    @Override
    public final int getTypeAndKind() {
        return Variable.VIEW | Variable.BOOL;
    }

    @Override
    public final int getValue() throws IllegalStateException {
        if (!isInstantiated()) {
            throw new IllegalStateException("getValue() can be only called on instantiated variable. " +
                    name + " is not instantiated");
        }
        return getLB();
    }

    @Override
    protected final EvtScheduler<IntEventType> createScheduler() {
        return new BoolEvtScheduler();
    }

    @Override
    public final String toString() {
        if (isInstantiated()) {
            return this.name + " = " + this.getValue();
        } else {
            return this.name + " = " + "[0,1]";
        }
    }

    @Override
    public final DisposableValueIterator getValueIterator(boolean bottomUp) {
        if (_viterator == null || _viterator.isNotReusable()) {
            _viterator = new DisposableValueBoundIterator(this);
        }
        if (bottomUp) {
            _viterator.bottomUpInit();
        } else {
            _viterator.topDownInit();
        }
        return _viterator;
    }

    @Override
    public final DisposableRangeIterator getRangeIterator(boolean bottomUp) {
        if (_riterator == null || _riterator.isNotReusable()) {
            _riterator = new DisposableRangeBoundIterator(this);
        }
        if (bottomUp) {
            _riterator.bottomUpInit();
        } else {
            _riterator.topDownInit();
        }
        return _riterator;
    }

    @Override
    public final void createDelta() {
        if (!reactOnRemoval) {
            delta = new OneValueDelta(model.getEnvironment());
            reactOnRemoval = true;
        }
    }

    @Override
    public IDelta getDelta() {
        return delta;
    }

    @Override
    public final IIntDeltaMonitor monitorDelta(ICause propagator) {
        createDelta();
        return new OneValueDeltaMonitor(delta, propagator);
    }

    @Override
    public final void _setNot(BoolVar neg) {
        this.not = neg;
    }

    @Override
    public final BoolVar not() {
        if (!hasNot()) {
            not = model.boolNotView(this);
            not._setNot(this);
        }
        return not;
    }

    @Override
    public final boolean hasNot() {
        return not != null;
    }

    @Override
    public final boolean isLit() {
        return true;
    }

    @Override
    public final boolean isNot() {
        return isNot;
    }

    @Override
    public final void setNot(boolean isNot) {
        this.isNot = isNot;
    }
}
