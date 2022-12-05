/*
 * TimeVaryingBranchRateModel.java
 *
 * Copyright (c) 2002-2022 Alexei Drummond, Andrew Rambaut and Marc Suchard
 *
 * This file is part of BEAST.
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership and licensing.
 *
 * BEAST is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 *  BEAST is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with BEAST; if not, write to the
 * Free Software Foundation, Inc., 51 Franklin St, Fifth Floor,
 * Boston, MA  02110-1301  USA
 */

package dr.evomodel.branchratemodel;

import dr.evolution.tree.NodeRef;
import dr.evolution.tree.Tree;
import dr.evolution.tree.TreeTraitProvider;
import dr.evolution.tree.TreeUtils;
import dr.evomodel.tree.TreeModel;
import dr.evomodelxml.branchratemodel.TimeVaryingBranchRateModelParser;
import dr.inference.model.Model;
import dr.inference.model.Parameter;
import dr.inference.model.Variable;
import dr.util.Author;
import dr.util.Citable;
import dr.util.Citation;

import java.util.Collections;
import java.util.List;

/**
 * @author Pratyusa Datta
 * @author Marc A. Suchard
 */

public class TimeVaryingBranchRateModel extends AbstractBranchRateModel implements DifferentiableBranchRates, Citable {

    private final Tree tree;
    private final Parameter rates;
    private final Parameter gridPoints;

    private boolean nodeRatesKnown;
    private boolean storedNodeRatesKnown;

    private double[] nodeRates;
    private double[] storedNodeRates;

    private final double[] times;

    public TimeVaryingBranchRateModel(Tree tree,
                                      Parameter rates,
                                      Parameter gridPoints) {

        super(TimeVaryingBranchRateModelParser.PARSER_NAME);

        this.tree = tree;
        this.rates = rates;
        this.gridPoints = gridPoints;

        if (tree instanceof TreeModel) {
            addModel((TreeModel) tree);
        }

        addVariable(rates);
        addVariable(gridPoints);

        nodeRates = new double[tree.getNodeCount()];
        storedNodeRates = new double[tree.getNodeCount()];

        times = computeTimes();

        nodeRatesKnown = false;
    }

    @Override
    public double getBranchRate(final Tree tree, final NodeRef node) {

        assert tree == this.tree;

        if (!nodeRatesKnown) { // lazy evaluation
            calculateNodeRates();
            nodeRatesKnown = true;
        }

        return nodeRates[getParameterIndexFromNode(node)];
    }

    @Override
    public double[] updateGradientLogDensity(double[] gradientWrtBranches, double[] value, int from, int to) {

        assert from == 0;
        assert to == gradientWrtBranches.length - 1;

        double[] gradientWrtRates = new double[rates.getDimension()];
        calculateNodeGradient(gradientWrtRates, gradientWrtBranches); // TODO do we need to pass `value` as well?
        return gradientWrtRates;
    }

    @Override
    public double getBranchRateDifferential(final Tree tree, final NodeRef node) {
        return 1.0; // TODO
    }

    @Override
    public double getBranchRateSecondDifferential(Tree tree, NodeRef node) {
        throw new RuntimeException("Not yet implemented");
    }

    private void calculateNodeRates() {

        NodeRef root = tree.getRoot();
        double rootHeight = tree.getNodeHeight(root);

        int epochIndex = times.length - 1;
        while (times[epochIndex] >= rootHeight) {
            --epochIndex;
        }

        traverseTreeByBranchForRates(rootHeight, tree.getChild(root, 0), epochIndex);
        traverseTreeByBranchForRates(rootHeight, tree.getChild(root, 1), epochIndex);

        if (TEST) {
            double[] copy = new double[nodeRates.length];
            System.arraycopy(nodeRates, 0, copy, 0, nodeRates.length);

            GenericFunction func = new GenericFunction.Rates(nodeRates, new FunctionalForm.PiecewiseConstant(rates));
            func.reset();

            traverseTreeByBranchGeneric(rootHeight, tree.getChild(root, 0), epochIndex, func);
            traverseTreeByBranchGeneric(rootHeight, tree.getChild(root, 1), epochIndex, func);

            for (int i = 0; i < copy.length; ++i) {
                if (copy[i] != nodeRates[i]) {
                    System.err.println("Error!");
                    System.exit(-1);
                }
            }
        }
    }

    private static final boolean TEST = false;

    private void calculateNodeGradient(double[] gradientWrtRates, double[] gradientWrtBranches) {

        // TODO remove code duplication with `calculateNodeRates`
        NodeRef root = tree.getRoot();
        double rootHeight = tree.getNodeHeight(root);

        int epochIndex = times.length - 1;
        while (times[epochIndex] >= rootHeight) {
            --epochIndex;
        }

        traverseTreeByBranchForGradient(gradientWrtRates, gradientWrtBranches, rootHeight, tree.getChild(root, 0), epochIndex);
        traverseTreeByBranchForGradient(gradientWrtRates, gradientWrtBranches, rootHeight, tree.getChild(root, 1), epochIndex);
    }

    private void traverseTreeByBranchForRates(double parentHeight, NodeRef child, int epochIndex) {

        // TODO needs testing / debugging

        final double childHeight = tree.getNodeHeight(child);

        double currentHeight = parentHeight;
        double branchRateNumerator = 0.0;
        double branchRateDenominator = 0.0;

        final double weightedRate;
        if (currentHeight > childHeight) {

            while (times[epochIndex] > childHeight) {
                double timeLength = currentHeight - times[epochIndex];
                double rate = rates.getParameterValue(epochIndex);

                branchRateNumerator += rate * timeLength;
                branchRateDenominator += timeLength;
                currentHeight = times[epochIndex];

                --epochIndex;
            }

            double timeLength = currentHeight - childHeight;
            double rate = rates.getParameterValue(epochIndex);

            branchRateNumerator += rate * timeLength;
            branchRateDenominator += timeLength;

            weightedRate = branchRateNumerator / branchRateDenominator;
        } else {
            weightedRate = rates.getParameterValue(epochIndex);
        }

        nodeRates[getParameterIndexFromNode(child)] = weightedRate;

        if (!tree.isExternal(child)) {
            traverseTreeByBranchForRates(childHeight, tree.getChild(child, 0), epochIndex);
            traverseTreeByBranchForRates(childHeight, tree.getChild(child, 1), epochIndex);
        }
    }

    private void traverseTreeByBranchForGradient(double[] gradientWrtRates, double[] gradientWrtNodes,
                                                 double parentHeight, NodeRef child, int epochIndex) {
        // TODO -- will look like `traverseTreeByBranchForRates`.  We will remove code duplication later.

        final double childHeight = tree.getNodeHeight(child);

        double currentHeight = parentHeight;

        if (currentHeight > childHeight) {

            while (times[epochIndex] > childHeight) {
                double timeLength = currentHeight - times[epochIndex];

                gradientWrtRates[epochIndex] += gradientWrtNodes[getParameterIndexFromNode(child)] *
                        (timeLength / (parentHeight - childHeight));
                currentHeight = times[epochIndex];

                --epochIndex;
            }

            double timeLength = currentHeight - childHeight;
            gradientWrtRates[epochIndex] += gradientWrtNodes[getParameterIndexFromNode(child)] *
                    (timeLength / (parentHeight - childHeight));
        }

        if (!tree.isExternal(child)) {
            traverseTreeByBranchForGradient(gradientWrtRates, gradientWrtNodes, childHeight, tree.getChild(child, 0), epochIndex);
            traverseTreeByBranchForGradient(gradientWrtRates, gradientWrtNodes, childHeight, tree.getChild(child, 1), epochIndex);
        }

    }

    private double[] computeTimes() {
        double[] times = new double[rates.getDimension()];
        System.arraycopy(gridPoints.getParameterValues(), 0, times, 1, gridPoints.getDimension());
        return times;
    }

    @Override
    public void handleModelChangedEvent(Model model, Object object, int index) {
        nodeRatesKnown = false;
        fireModelChanged();
    }

    @Override
    protected final void handleVariableChangedEvent(Variable variable, int index, Parameter.ChangeType type) {

        if (variable == gridPoints) {
            throw new RuntimeException("Not yet implemented");
        }

        nodeRatesKnown = false;
        fireModelChanged();
    }

    @Override
    protected void storeState() {

        if (storedNodeRates == null) {
            storedNodeRates = new double[nodeRates.length];
        }

        System.arraycopy(nodeRates, 0, storedNodeRates, 0, nodeRates.length);
        storedNodeRatesKnown = nodeRatesKnown;
    }

    @Override
    protected void restoreState() {
        double[] tmp = nodeRates;
        nodeRates = storedNodeRates;
        storedNodeRates = tmp;

        nodeRatesKnown = storedNodeRatesKnown;
    }

    @Override
    protected void acceptState() { }

    @Override
    public Parameter getRateParameter() {
        return rates;
    }

    @Override
    public int getParameterIndexFromNode(NodeRef node) {
        return node.getNumber(); // TODO Unsure if this is correct
    }

    @Override
    public ArbitraryBranchRates.BranchRateTransform getTransform() {
        throw new RuntimeException("Not yet implemented");
    }

    @Override
    public double[] updateDiagonalHessianLogDensity(double[] diagonalHessian, double[] gradient, double[] value, int from, int to) {
        throw new RuntimeException("Not yet implemented");
    }

    @Override
    public Citation.Category getCategory() {
        return Citation.Category.MOLECULAR_CLOCK;
    }

    @Override
    public String getDescription() {
        return "Time-varying branch rate model";
    }

    @Override
    public List<Citation> getCitations() {
        return Collections.singletonList(
                new Citation(
                        new Author[]{
                                new Author("P", "Datta"),
                                new Author("MA", "Suchard"),
                        },
                        Citation.Status.IN_PREPARATION
                )
        );
    }

    public String toString() {
        TreeTraitProvider[] treeTraitProviders = {this};
        return TreeUtils.newick(tree, treeTraitProviders);
    }

    interface FunctionalForm {

        void reset();

        void increment(int epochIndex, double startTime, double endTime);

        double getExpectedRate();

        double getDefaultRate(int epochIndex);

        class PiecewiseConstant implements FunctionalForm {

            final private Parameter rates;
            private double branchRateNumerator;
            private double branchRateDenominator;

            PiecewiseConstant(Parameter rates) {
                this.rates = rates;
            }

            @Override
            public void reset() {
                branchRateNumerator = 0.0;
                branchRateDenominator = 0.0;
            }

            @Override
            public void increment(int epochIndex, double startTime, double endTime) {
                double timeLength = startTime - endTime;
                double rate = rates.getParameterValue(epochIndex);

                branchRateNumerator += rate * timeLength;
                branchRateDenominator += timeLength;
            }

            @Override
            public double getExpectedRate() {
                return branchRateNumerator / branchRateDenominator;
            }

            @Override
            public double getDefaultRate(int epochIndex) {
                return rates.getParameterValue(epochIndex);
            }
        }

        abstract class PiecewiseLinear implements FunctionalForm { }
    }

    interface GenericFunction {

        void reset();

        void increment(int epochIndex, double startTime, double endTime);

        double getExpectedRate();

        double getDefaultRate(int epochIndex);

        void action(int epochIndex, int nodeIndex, double rate);

        abstract class AbstractGenericFunction implements GenericFunction {

            final FunctionalForm functionalForm;

            AbstractGenericFunction(FunctionalForm functionalForm) {
                this.functionalForm = functionalForm;
            }

            @Override
            public void reset() {
                functionalForm.reset();
            }

            @Override
            public void increment(int epochIndex, double startTime, double endTime) {
                functionalForm.increment(epochIndex, startTime, endTime);
            }

            @Override
            public double getExpectedRate() {
                return functionalForm.getExpectedRate();
            }

            @Override
            public double getDefaultRate(int epochIndex) {
                return functionalForm.getDefaultRate(epochIndex);
            }
        }

        class Gradient extends AbstractGenericFunction {

            final private double[] gradientEpochs;
            final private double[] gradientNodes;

            Gradient(double[] gradientEpochs, double[] gradientNodes, FunctionalForm functionalForm) {
                super(functionalForm);
                this.gradientEpochs = gradientEpochs;
                this.gradientNodes = gradientNodes;
            }

            @Override
            public void action(int epochIndex, int nodeIndex, double rate) {

            }
        }

        class Rates extends AbstractGenericFunction {

            final private double[] nodeRates;

            Rates(double[] nodeRates, FunctionalForm functionalForm) {
                super(functionalForm);
                this.nodeRates = nodeRates;
            }

            @Override
            public void action(int epochIndex, int nodeIndex, double rate) {
                nodeRates[nodeIndex] = rate;
            }
        }
    }

    private void traverseTreeByBranchGeneric(double currentHeight, NodeRef child, int epochIndex,
                                             GenericFunction generic) {

        final double childHeight = tree.getNodeHeight(child);
        generic.reset();

        final double rate;
        if (currentHeight > childHeight) {

            while (times[epochIndex] > childHeight) {
                generic.increment(epochIndex, currentHeight, times[epochIndex]);
                currentHeight = times[epochIndex];

                --epochIndex;
            }

            generic.increment(epochIndex, currentHeight, childHeight);
            rate = generic.getExpectedRate();
        } else {
            rate = generic.getDefaultRate(epochIndex);
        }

        generic.action(epochIndex, getParameterIndexFromNode(child), rate);

        if (!tree.isExternal(child)) {
            traverseTreeByBranchGeneric(childHeight, tree.getChild(child, 0), epochIndex, generic);
            traverseTreeByBranchGeneric(childHeight, tree.getChild(child, 1), epochIndex, generic);
        }
    }
}
