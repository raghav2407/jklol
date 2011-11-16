package com.jayantkrish.jklol.models;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.FactorGraph;

public class FactorGraphTest extends TestCase {

	private FactorGraph f;
	private TableFactorBuilder builder;
	private DiscreteVariable tfVar;

	public void setUp() {
		f = new FactorGraph();

		tfVar = new DiscreteVariable("Three values",
				Arrays.asList(new String[] {"T", "F", "U"}));

		DiscreteVariable otherVar = new DiscreteVariable("Two values",
				Arrays.asList(new String[] {"foo", "bar"}));

		f = f.addVariable("Var0", tfVar);
		f = f.addVariable("Var1", otherVar);
		f = f.addVariable("Var2", tfVar);
		f = f.addVariable("Var3", tfVar);

		builder = new TableFactorBuilder(f.lookupVariables(Arrays.asList(new String[] {"Var0", "Var2", "Var3"})));
		builder.incrementWeight(builder.getVars().intArrayToAssignment(new int[] {0, 0, 0}), 1.0);
		f = f.addFactor(builder.build());

		builder = new TableFactorBuilder(f.lookupVariables(Arrays.asList(new String[] {"Var2", "Var1"})));
		builder.incrementWeight(builder.getVars().intArrayToAssignment(new int[] {0, 0}), 1.0);
		builder.incrementWeight(builder.getVars().intArrayToAssignment(new int[] {1, 1}), 1.0);
		f = f.addFactor(builder.build());
	}

	public void testGetFactorsWithVariable() {
		assertEquals(2,
				f.getFactorsWithVariable(f.getVariableIndex("Var2")).size());
		assertEquals(1,
				f.getFactorsWithVariable(f.getVariableIndex("Var3")).size());
	}

	public void testGetSharedVariables() {
		List<Integer> shared = new ArrayList<Integer>(f.getSharedVariables(0, 1));
		assertEquals(1, shared.size());
		assertEquals(2, (int) shared.get(0));
	}
	
	public void testAddVariable() {
	  f = f.addVariable("Var4", tfVar);
	  assertEquals(5, f.getVariables().size());
	  
	  try {
	    f.addVariable("Var4", tfVar);
	  } catch (IllegalArgumentException e) {
	    return;
	  }
	  fail("Expected IllegalArgumentException");
	}
	
	public void testMarginalize() {
	  FactorGraph m = f.marginalize(Ints.asList(0, 3, 2));
	  assertEquals(1, m.getVariables().size());
	  assertTrue(m.getVariableNames().contains("Var1"));
	  
	  assertEquals(1, m.getFactors().size());
	  Factor factor = m.getFactors().get(0);
	  assertEquals(1, factor.getVars().size());
	  assertEquals(1.0, factor.getUnnormalizedProbability("foo"));
	  assertEquals(0.0, factor.getUnnormalizedProbability("bar"));
	}
}
