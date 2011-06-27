package com.jayantkrish.jklol.models;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jayantkrish.jklol.util.HashMultimap;

/**
 * A TableFactor is a representation of a factor where each weight is set beforehand. The internal
 * representation is sparse, making it appropriate for factors where many weight settings are 0.
 */
public class TableFactor extends DiscreteFactor {

	// I'm assuming factors are going to be sparse, otherwise a double[] would be
	// more efficient.
	private SparseOutcomeTable<Double> weights;

	// An index storing assignments containing particular variable values.
	private List<HashMultimap<Object, Assignment>> varValueAssignmentIndex;

	/**
	 * Construct a TableFactor involving the specified variable numbers (whose possible values are
	 * in variables).
	 */
	public TableFactor(VariableNumMap vars) {
		super(vars);

		weights = new SparseOutcomeTable<Double>(vars.getVariableNums());
		varValueAssignmentIndex = new ArrayList<HashMultimap<Object, Assignment>>(vars.getVariableNums().size());
		for (int i = 0; i < vars.getVariableNums().size(); i++) {
			varValueAssignmentIndex.add(new HashMultimap<Object, Assignment>());
		}
	}

	////////////////////////////////////////////////////////////////////////////////
	// Factor overrides.
	////////////////////////////////////////////////////////////////////////////////

	public Iterator<Assignment> outcomeIterator() {
		return weights.assignmentIterator();
	}

	public double getUnnormalizedProbability(Assignment a) {
		assert a.getVarNumsSorted().equals(weights.getVarNums());

		if (weights.containsKey(a)) {
			return weights.get(a);
		}
		return 0.0;
	}

	public Set<Assignment> getAssignmentsWithEntry(int varNum, Set<Object> varValues) {
		int varIndex = -1;
		List<Integer> varNums = getVars().getVariableNums();
		for (int i = 0; i < varNums.size(); i++) {
			if (varNums.get(i) == varNum) {
				varIndex = i;
				break;
			}
		}
		assert varIndex != -1;

		Set<Assignment> possibleAssignments = new HashSet<Assignment>();
		for (Object varValue : varValues) {
			possibleAssignments.addAll(varValueAssignmentIndex.get(varIndex).get(varValue));
		}
		return possibleAssignments;
	}

	/////////////////////////////////////////////////////////////////////////////
	// TableFactor-specific methods
	////////////////////////////////////////////////////////////////////////////

	/**
	 * General purpose method for setting a factor weight.
	 */ 
	public void setWeightList(List<? extends Object> varValues, double weight) {
		assert getVars().size() == varValues.size();
		setWeight(getVars().outcomeToAssignment(varValues), weight);
	}

	public void setWeight(Assignment a, double weight) {
		assert weight >= 0.0;
		weights.put(a, weight);

		Assignment copy = new Assignment(a);
		List<Integer> varNums = getVars().getVariableNums();
		for (int i = 0; i < varNums.size(); i++) {
			varValueAssignmentIndex.get(i).put(copy.getVarValue(varNums.get(i)), copy);
		}
	}


	public String toString() {
		return weights.toString();
	}

	///////////////////////////////////////////////////////////////////////////////////
	// Static methods, mostly for computing sums / products of factors
	///////////////////////////////////////////////////////////////////////////////////

	public static DiscreteFactor sumProductTableFactor(List<DiscreteFactor> toSum, Collection<Integer> variablesToRetain) {
		TableFactor p = TableFactor.productFactor(toSum);
		List<Integer> vars = new ArrayList<Integer>(p.getVars().getVariableNums());
		vars.removeAll(variablesToRetain);
		return p.marginalize(vars);
	}

	public static DiscreteFactor maxProductTableFactor(List<DiscreteFactor> toSum, Collection<Integer> variablesToRetain) {
		TableFactor p = TableFactor.productFactor(toSum);
		List<Integer> vars = new ArrayList<Integer>(p.getVars().getVariableNums());
		vars.removeAll(variablesToRetain);
		return p.maxMarginalize(vars);
	}

	private static TableFactor subsetProductFactor(DiscreteFactor whole, List<DiscreteFactor> subsets) {
		Map<Integer, Set<Object>> varValueMap = getPossibleVariableValues(subsets);
		Set<Assignment> possibleAssignments = null;
		for (Integer varNum : varValueMap.keySet()) {
			if (possibleAssignments == null) {
				possibleAssignments = whole.getAssignmentsWithEntry(varNum, varValueMap.get(varNum));
			} else {
				possibleAssignments.retainAll(whole.getAssignmentsWithEntry(varNum, varValueMap.get(varNum)));
			}
		}

		Iterator<Assignment> iter = null;
		if (possibleAssignments != null) {
			iter = possibleAssignments.iterator(); 
		} else {
			iter = whole.outcomeIterator();
		}
		TableFactor returnFactor = new TableFactor(whole.getVars());
		while (iter.hasNext()) {
			Assignment a = iter.next();
			double prob = whole.getUnnormalizedProbability(a);
			for (DiscreteFactor subset : subsets) {
				Assignment sub = a.subAssignment(subset.getVars().getVariableNums());
				prob  *=  subset.getUnnormalizedProbability(sub);
			}
			if (prob > 0) {
				returnFactor.setWeight(a, prob);
			}
		}
		return returnFactor;
	}

	public static TableFactor productFactor(DiscreteFactor ... factors) {
		return productFactor(Arrays.asList(factors));
	}

	public static TableFactor productFactor(List<DiscreteFactor> toMultiply) {
		VariableNumMap allVars = VariableNumMap.EMPTY;
		for (DiscreteFactor f : toMultiply) {
			allVars = allVars.union(f.getVars());
		}

		// Check if we can use a faster multiplication algorithm that doesn't
		// enumerate all plausible assignments
		DiscreteFactor whole = null;
		List<DiscreteFactor> others = new ArrayList<DiscreteFactor>();
		for (DiscreteFactor f : toMultiply) {
			if (whole == null && f.getVars().equals(allVars)) {
				whole = f;
			} else {
				others.add(f);
			}
		}
		if (whole != null) {
			return subsetProductFactor(whole, others);
		}

		// Can't use the faster algorithm. Find all possible value assignments to each variable,
		// then try each possible combination and calculate its probability.
		List<Object> varValues = new ArrayList<Object>(allVars.size());
		for (int i = 0; i < allVars.size(); i++) {
			varValues.add(null);
		}

		TableFactor returnFactor = new TableFactor(allVars);
		Iterator<Assignment> assignmentIter = new AllAssignmentIterator(allVars);
		while (assignmentIter.hasNext()) {
			Assignment a = assignmentIter.next();
			double weight = 1.0;
			for (DiscreteFactor f : toMultiply) {
				weight *= f.getUnnormalizedProbability(a.subAssignment(f.getVars()));
			}
			if (weight > 0) {
				returnFactor.setWeight(a, weight);
			}
		}

		// TODO(jayantk): this algorithm was faster when lots of variables have 0 probability entries.
		// Map<Integer, Set<Integer>> varValueMap = getPossibleVariableValues(toMultiply);
		// recursiveFactorInitialization(varNums, vars, 0, varValues, toMultiply, returnFactor, varValueMap);
		return returnFactor;
	}


	/*
    private static void recursiveFactorInitialization(List<Integer> varNums, List<Variable> vars, int curInd, 
	    List<Object> varAssignments, List<DiscreteFactor> factors, TableFactor returnFactor, Map<Integer, Set<Integer>> varValueMap) {

	if (curInd == varNums.size()) {
	    // Base case: varAssignments has a unique assignment to all variables, so 
	    // we now must initialize the factor weight.
	    double weight = 1.0;
	    for (DiscreteFactor f : factors) {
		weight *= f.getUnnormalizedProbability(varNums, varAssignments);
	    }
	    if (weight > 0) {
		returnFactor.setWeightList(varAssignments, weight);
	    }
	} else {
	    Variable curVar = vars.get(curInd);
	    Set<Integer> values = varValueMap.get(varNums.get(curInd));
	    for (Integer i : values) {
		varAssignments.set(curInd, curVar.getValue(i));
		recursiveFactorInitialization(varNums, vars, curInd + 1, varAssignments, factors, returnFactor, varValueMap);
	    }
	}
    }
	 */

	/*
	 * Helper method for deciding all possible assignments to each variable in the provided list of factors.
	 */
	private static Map<Integer, Set<Object>> getPossibleVariableValues(List<DiscreteFactor> factors) {
		Map<Integer, Set<Object>> varValueMap = new HashMap<Integer, Set<Object>>();
		for (DiscreteFactor f : factors) {
			Iterator<Assignment> assignmentIterator = f.outcomeIterator();
			HashMultimap<Integer, Object> factorValueMap = new HashMultimap<Integer, Object>();
			while (assignmentIterator.hasNext()) {
				Assignment a = assignmentIterator.next();
				List<Integer> varNumsSorted = a.getVarNumsSorted();
				List<Object> varValuesSorted = a.getVarValuesInKeyOrder();
				for (int i = 0; i < varNumsSorted.size(); i++) {
					factorValueMap.put(varNumsSorted.get(i), varValuesSorted.get(i));
				}
			}

			for (Integer varNum : f.getVars().getVariableNums()) {
				if (!varValueMap.containsKey(varNum)) {
					varValueMap.put(varNum, new HashSet<Object>(factorValueMap.get(varNum)));
				} else {
					varValueMap.get(varNum).retainAll(factorValueMap.get(varNum));
				}
			}
		}
		return varValueMap;
	}

}