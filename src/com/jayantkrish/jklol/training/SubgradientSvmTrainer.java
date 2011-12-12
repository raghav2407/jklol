package com.jayantkrish.jklol.training;

import java.util.Iterator;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.evaluation.Example;
import com.jayantkrish.jklol.inference.FactorMarginalSet;
import com.jayantkrish.jklol.inference.MarginalCalculator;
import com.jayantkrish.jklol.inference.MarginalSet;
import com.jayantkrish.jklol.inference.MaxMarginalSet;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.TableFactorBuilder;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.dynamic.DynamicAssignment;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraph;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.util.AllAssignmentIterator;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Trains a {@link ParametricFactorGraph} as a structured SVM. The training
 * procedure performs (stochastic) subgradient descent on the structured SVM
 * objective function using a user-specified cost function and L2
 * regularization.
 * 
 * @author jayantk
 */
public class SubgradientSvmTrainer extends AbstractTrainer {

  private final int numIterations;
  private final int batchSize;
  private final double regularization;
  private final MarginalCalculator marginalCalculator;
  private final CostFunction costFunction;
  private final LogFunction log;

  public SubgradientSvmTrainer(int numIterations, int batchSize,
      double regularizationConstant, MarginalCalculator marginalCalculator,
      CostFunction costFunction, LogFunction log) {
    Preconditions.checkArgument(regularizationConstant > 0);

    this.numIterations = numIterations;
    this.batchSize = batchSize;
    this.regularization = regularizationConstant;
    this.marginalCalculator = marginalCalculator;
    this.costFunction = costFunction;
    this.log = (log != null) ? log : new NullLogFunction();
  }

  /**
   * {@inheritDoc}
   * 
   * {@code modelFamily} is presumed to be a loglinear model.
   * {@code trainingData} contains the inputVar/outputVar pairs for training.
   * The inputVar and outputVar of each training example should be over disjoint
   * sets of variables, and the union of these sets should contain all of the
   * variables in {@code modelFamily}.
   * 
   * @param modelFamily
   * @param trainingData
   * @return
   */
  public SufficientStatistics train(ParametricFactorGraph modelFamily,
      SufficientStatistics initialParameters,
      Iterable<Example<DynamicAssignment, DynamicAssignment>> trainingData) {
    SufficientStatistics parameters = initialParameters;

    // cycledTrainingData loops indefinitely over the elements of trainingData.
    // This is desirable because we want batchSize examples but don't
    // particularly care where in trainingData they come from.
    Iterator<Example<DynamicAssignment, DynamicAssignment>> cycledTrainingData =
        Iterators.cycle(trainingData);

    // Each iteration processes a single batch of batchSize training examples.
    for (int i = 0; i < numIterations; i++) {
      log.notifyIterationStart(i);
      // Get the examples for this batch. Ideally, this would be a random
      // sample; however, deterministically iterating over the examples may be
      // more efficient and is fairly close if the examples are provided in
      // random order.
      List<Example<DynamicAssignment, DynamicAssignment>> batchData = getBatch(cycledTrainingData, batchSize);

      DynamicFactorGraph currentDynamicModel = modelFamily.getFactorGraphFromParameters(parameters);
      SufficientStatistics subgradient = modelFamily.getNewSufficientStatistics();
      int numIncorrect = 0;
      double approximateObjectiveValue = regularization * Math.pow(parameters.getL2Norm(), 2) / 2;
      for (Example<DynamicAssignment, DynamicAssignment> example : batchData) {

        FactorGraph currentModel = currentDynamicModel.getFactorGraph(example.getInput());
        Assignment input = currentDynamicModel.getVariables().toAssignment(example.getInput());
        Assignment output = currentDynamicModel.getVariables().toAssignment(example.getOutput());

        double objectiveValue = updateSubgradientWithInstance(currentModel, input, output, modelFamily, subgradient);

        if (objectiveValue != 0.0) {
          numIncorrect++;
          approximateObjectiveValue += objectiveValue / batchSize;
        }
      }

      // TODO: Can we use the Pegasos projection step?
      // If so, the step size should decay as 1/i, not 1/sqrt(i).
      double stepSize = 1.0 / (regularization * Math.sqrt(i + 1));
      parameters.multiply(1.0 - (stepSize * regularization));
      parameters.increment(subgradient, stepSize / batchSize);

      log.logStatistic(i, "number of examples within margin", Integer.toString(numIncorrect));
      log.logStatistic(i, "approximate objective value", Double.toString(approximateObjectiveValue));
      log.notifyIterationEnd(i);
    }
    return parameters;
  }

  /**
   * Computes the subgradient of the given training example ({@code input} and
   * {@code output}) and adds it to {@code subgradient}. Returns the structured
   * hinge loss of the prediction vs. the actual output.
   * 
   * @param currentModel
   * @param input
   * @param output
   * @param modelFamily
   * @param subgradient
   * @return
   */
  private double updateSubgradientWithInstance(FactorGraph currentModel, Assignment input, Assignment output,
      ParametricFactorGraph modelFamily, SufficientStatistics subgradient) {
    // Get the cost-augmented best prediction based on the current input.
    FactorGraph costAugmentedModel = costFunction.augmentWithCosts(currentModel,
        currentModel.getVariables().intersection(output.getVariableNums()),
        output);
    FactorGraph conditionalCostAugmentedModel = costAugmentedModel.conditional(input);
    MaxMarginalSet predicted = marginalCalculator.computeMaxMarginals(conditionalCostAugmentedModel);
    Assignment prediction = predicted.getNthBestAssignment(0);

    // Get the best value for any hidden variables, given the current input and
    // correct output.
    Assignment observed = output.union(input);
    FactorGraph conditionalOutputModel = currentModel.conditional(observed);
    MaxMarginalSet actualMarginals = marginalCalculator.computeMaxMarginals(conditionalOutputModel);
    Assignment actual = actualMarginals.getNthBestAssignment(0);

    // Update parameters if necessary.
    if (!prediction.equals(actual)) {
      // Convert the assignments into marginal (point) distributions in order to
      // update
      // the parameter vector.
      MarginalSet actualMarginal = FactorMarginalSet.fromAssignment(conditionalOutputModel.getAllVariables(), actual);
      MarginalSet predictedMarginal = FactorMarginalSet.fromAssignment(conditionalCostAugmentedModel.getAllVariables(), prediction);
      // Update the parameter vector
      modelFamily.incrementSufficientStatistics(subgradient, actualMarginal, 1.0);
      modelFamily.incrementSufficientStatistics(subgradient, predictedMarginal, -1.0);

      // Return the loss, which is the amount by which the prediction is within
      // the margin.
      return (costAugmentedModel.getUnnormalizedLogProbability(prediction)
      - costAugmentedModel.getUnnormalizedLogProbability(actual));
    }
    return 0.0;
  }

  private <P, Q> List<Example<P, Q>> getBatch(
      Iterator<Example<P, Q>> trainingData, int batchSize) {
    List<Example<P, Q>> batchData = Lists.newArrayListWithCapacity(batchSize);
    for (int i = 0; i < batchSize && trainingData.hasNext(); i++) {
      batchData.add(trainingData.next());
    }
    return batchData;
  }

  /**
   * The cost imposed on the SVM for selecting an incorrect value. This function
   * is used during training to add cost factors to the factor graph. The
   * resulting factor graph is then decoded to select the
   * "best and most dangerous" prediction of the SVM.
   * 
   * @author jayantk
   */
  public static interface CostFunction {

    /**
     * Returns {@code factorGraph} with additional cost factors added. Cost
     * factors add additional weight to incorrect labels, i.e., labels which are
     * not equal to {@code trueLabel}.
     * 
     * @param factorGraph
     * @param outputVariables
     * @param trueLabel
     * @return
     */
    public FactorGraph augmentWithCosts(FactorGraph factorGraph, VariableNumMap outputVariables, Assignment trueLabel);
  }

  /**
   * {@code HammingCost} is a per-variable cost function. The total added cost
   * for a predicted assignment is the number of variables in predicted whose
   * values do not agree with the true assignment. If {@code HammingCost} is
   * used for binary classification, it reduces to the standard hinge loss for
   * (non-structured) SVMs.
   * 
   * @author jayantk
   */
  public static class HammingCost implements CostFunction {
    public FactorGraph augmentWithCosts(FactorGraph factorGraph, VariableNumMap outputVariables, Assignment trueLabel) {
      FactorGraph augmentedGraph = factorGraph;
      for (Integer varNum : outputVariables.getVariableNums()) {
        List<Integer> varNumList = Ints.asList(varNum);
        TableFactorBuilder builder = new TableFactorBuilder(outputVariables.intersection(varNumList));

        Iterator<Assignment> varAssignments = new AllAssignmentIterator(outputVariables.intersection(varNumList));
        while (varAssignments.hasNext()) {
          builder.incrementWeight(varAssignments.next(), Math.E);
        }
        builder.multiplyWeight(trueLabel.intersection(varNumList), Math.exp(-1.0));

        augmentedGraph = augmentedGraph.addFactor(builder.build());
      }
      return augmentedGraph;
    }
  }
}
