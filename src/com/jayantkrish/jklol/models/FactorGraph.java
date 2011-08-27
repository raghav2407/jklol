package com.jayantkrish.jklol.models;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Sets;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.HashMultimap;
import com.jayantkrish.jklol.util.IndexedList;

/**
 * A FactorGraph represents a graphical model in the form of a set of variables
 * with factors defined over them. FactorGraphs use generics in order to support
 * different types of graphical models and training procedures (e.g., Bayes Nets
 * and counting).
 * 
 * Theoretically, FactorGraphs are immutable. However, there are still some
 * mutators in this class which will be phased out. Also, keep in mind that some
 * factors are mutable (for efficiency), so while the graph structure of a
 * FactorGraph is immutable, the probability distribution that it represents may
 * change.
 */
public class FactorGraph {

  private VariableNumMap variables;
  private Map<String, Integer> variableNumMap;
  private HashMultimap<Integer, Integer> variableFactorMap;
  private HashMultimap<Integer, Integer> factorVariableMap;

  private IndexedList<Factor> factors;

  private InferenceHint inferenceHint;

  /**
   * Create an empty factor graph.
   */
  public FactorGraph() {
    variables = VariableNumMap.emptyMap();
    variableNumMap = new HashMap<String, Integer>();
    variableFactorMap = new HashMultimap<Integer, Integer>();
    factorVariableMap = new HashMultimap<Integer, Integer>();
    factors = new IndexedList<Factor>();
    inferenceHint = null;
  }

  /**
   * Copy constructor.
   * 
   * @param factorGraph
   */
  public FactorGraph(FactorGraph factorGraph) {
    this.variables = new VariableNumMap(factorGraph.variables);
    this.variableNumMap = new HashMap<String, Integer>(factorGraph.variableNumMap);
    this.variableFactorMap = new HashMultimap<Integer, Integer>(factorGraph.variableFactorMap);
    this.factorVariableMap = new HashMultimap<Integer, Integer>(factorGraph.factorVariableMap);
    this.factors = new IndexedList<Factor>(factorGraph.factors);
    this.inferenceHint = null;
  }

  /**
   * Get the number of factors in the graph.
   */
  public int numFactors() {
    return factors.size();
  }

  /**
   * Get a factor using its index number.
   */
  public Factor getFactor(int factorNum) {
    return factors.get(factorNum);
  }

  /**
   * Gets the indices of all factors which are adjacent (that is, share at least
   * one variable) with {@code factorNum}.
   * 
   * @param factorNum
   * @return
   */
  public Set<Integer> getAdjacentFactors(int factorNum) {
    Set<Integer> adjacentFactors = Sets.newHashSet();
    for (Integer variableNum : factorVariableMap.get(factorNum)) {
      adjacentFactors.addAll(variableFactorMap.get(variableNum));
    }
    return adjacentFactors;
  }

  /**
   * Get all factors.
   */
  public List<Factor> getFactors() {
    return factors.items();
  }

  /**
   * Get a variable using its index number.
   */
  public Variable getVariableFromIndex(int varNum) {
    return variables.getVariable(varNum);
  }

  /**
   * Get the index of a variable from its name.
   */
  public int getVariableIndex(String variableName) {
    return variableNumMap.get(variableName);
  }

  /**
   * Get the variables in the factor graph.
   */
  public List<Variable> getVariables() {
    return variables.getVariables();
  }

  /**
   * Get the variables that this factor graph is defined over.
   * 
   * @return
   */
  public VariableNumMap getVariableNumMap() {
    return variables;
  }

  /**
   * Get the variable numbers and variables corresponding to the given set of
   * variable names. Note that the order of the names in factorVariables is
   * irrelevant.
   */
  public VariableNumMap lookupVariables(Collection<String> factorVariables) {
    List<Integer> varNums = new ArrayList<Integer>();
    List<Variable> vars = new ArrayList<Variable>();
    for (String variableName : factorVariables) {
      if (!variableNumMap.containsKey(variableName)) {
        throw new IllegalArgumentException("Must use an already specified variable name.");
      }
      varNums.add(variableNumMap.get(variableName));
      vars.add(variables.getVariable(variableNumMap.get(variableName)));
    }
    return new VariableNumMap(varNums, vars);
  }
  
  /**
   * Gets an assignment for the named set of variables.
   */
  public Assignment outcomeToAssignment(List<String> factorVariables, List<? extends Object> outcome) {
    assert factorVariables.size() == outcome.size();

    List<Integer> varNums = new ArrayList<Integer>(factorVariables.size());
    List<Object> outcomeValueInds = new ArrayList<Object>(outcome.size());
    for (int i = 0; i < factorVariables.size(); i++) {
      int varInd = getVariableIndex(factorVariables.get(i));
      varNums.add(varInd);
      outcomeValueInds.add(outcome.get(i));
    }
    return new Assignment(varNums, outcomeValueInds);
  }
  
  /**
   * Identical to {@link #outcomeToAssignment(List, List)}, but with arrays.
   * 
   * @param factorVariables
   * @param outcome
   * @return
   */
  public Assignment outcomeToAssignment(String[] factorVariables, Object[] outcome) {
    return outcomeToAssignment(Arrays.asList(factorVariables), Arrays.asList(outcome));
  }


  public Map<String, Object> assignmentToObject(Assignment a) {
    Map<String, Object> objectVals = new HashMap<String, Object>();
    for (String varName : variableNumMap.keySet()) {
      int varNum = variableNumMap.get(varName);
      if (a.containsVar(varNum)) {
        objectVals.put(varName, a.getVarValue(varNum));
      }
    }
    return objectVals;
  }

  /**
   * Get all of the factors which contain the passed-in varNum
   */
  public Set<Integer> getFactorsWithVariable(int varNum) {
    return variableFactorMap.get(varNum);
  }

  /**
   * Get all of the variables that the two factors have in common.
   */
  public Set<Integer> getSharedVariables(int factor1, int factor2) {
    Set<Integer> varNums = new HashSet<Integer>(factorVariableMap.get(factor1));
    varNums.retainAll(factorVariableMap.get(factor2));
    return varNums;
  }

  /**
   * Gets a hint about how to efficiently perform inference with this
   * model.  May return {@code null}, in which case the hint should be
   * ignored.
   */
  public InferenceHint getInferenceHint() {
      return inferenceHint;
  }

  /**
   * Sets a hint about how to efficiently perform inference with this
   * model. The hint can be set to {@code null}, in which case the hint
   * is ignored.
   */
  public void setInferenceHint(InferenceHint inferenceHint) {
      this.inferenceHint = inferenceHint;
  }

  // Factor Graph mutators
  // TODO(jayant): These mutators should be refactored out of this class so that
  // FactorGraphs are immutable.

  /**
   * Add a new variable (vertex) to the Markov network. The variable starts out
   * unconnected to any factors. The method returns the numerical ID of the
   * variable, which are assigned in sorted order.
   */
  public int addVariable(String variableName, Variable variable) {
    int varNum = variables.size();
    variableNumMap.put(variableName, varNum);
    variables = variables.addMapping(varNum, variable);
    return varNum;
  }

  /**
   * Add a new factor to the model, returning the unique number assigned to it.
   */
  public int addFactor(Factor factor) {
    int factorNum = factors.size();
    factors.add(factor);

    for (Integer i : factor.getVars().getVariableNums()) {
      variableFactorMap.put(i, factorNum);
      factorVariableMap.put(factorNum, i);
    }
    return factorNum;
  }

  /**
   * Add a new table factor to the model.
   */
  public TableFactor addTableFactor(List<String> variables) {
    VariableNumMap vars = lookupVariables(variables);
    VariableNumMap discreteVars = VariableNumMap.emptyMap();
    for (Integer varNum : vars.getVariableNums()) {
      Variable v = vars.getVariable(varNum);
      if (v instanceof DiscreteVariable) {
        discreteVars = discreteVars.addMapping(varNum, (DiscreteVariable) v);
      } else {
        throw new IllegalArgumentException();
      }
    }
    TableFactor factor = new TableFactor(discreteVars);
    addFactor(factor);
    return factor;
  }
}
