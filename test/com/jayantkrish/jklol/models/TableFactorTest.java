import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.FeatureFunction;
import com.jayantkrish.jklol.models.IndicatorFeatureFunction;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.Assignment;
import com.jayantkrish.jklol.models.Variable;
import junit.framework.*;

import java.util.*;

/**
 * This also tests many of the methods in Factor and the
 * IndicatorFeatureFunction.
 */ 
public class TableFactorTest extends TestCase {

    private TableFactor f;
    private TableFactor g;
    private TableFactor h;

    private Variable<String> v;
    private Variable<String> v2;

    private FeatureFunction feature;

    public void setUp() {
	v = new Variable<String>("Three values",
		Arrays.asList(new String[] {"T", "F", "U"}));

	v2 = new Variable<String>("Two values",
		Arrays.asList(new String[] {"foo", "bar"}));

	h = new TableFactor(Arrays.asList(new Integer[] {1, 0}),
		Arrays.asList(new Variable[] {v2, v}));
	
	f = new TableFactor(Arrays.asList(new Integer[] {0, 3, 2, 5}),
		Arrays.asList(new Variable[] {v, v, v, v}));

	// NOTE: These insertions are to the variables in SORTED ORDER,
	// even though the above variables are defined out-of-order.
	f.setWeightList(Arrays.asList(new String[] {"T", "T", "T", "T"}), 1.0);
	f.setWeightList(Arrays.asList(new String[] {"T", "T", "F", "T"}), 3.0);
	f.setWeightList(Arrays.asList(new String[] {"T", "T", "F", "U"}), 2.0);

	g = new TableFactor(Arrays.asList(new Integer[] {0, 1, 3}),
		Arrays.asList(new Variable[] {v, v, v}));

	g.setWeightList(Arrays.asList(new String[] {"T", "U", "F"}), 7.0);
	g.setWeightList(Arrays.asList(new String[] {"T", "F", "F"}), 11.0);
	g.setWeightList(Arrays.asList(new String[] {"F", "T", "T"}), 9.0);
	g.setWeightList(Arrays.asList(new String[] {"T", "U", "T"}), 13.0);

	Set<Assignment> testAssignments = new HashSet<Assignment>();
	testAssignments.add(f.outcomeToAssignment(Arrays.asList(new String[] {"T", "T", "T", "T"})));
	testAssignments.add(f.outcomeToAssignment(Arrays.asList(new String[] {"T", "F", "F", "F"})));
	testAssignments.add(f.outcomeToAssignment(Arrays.asList(new String[] {"T", "T", "F", "U"})));
	feature = new IndicatorFeatureFunction(testAssignments);
    }

    public void testVariableOrder() {
	assertEquals(Arrays.asList(new Integer[] {0, 1}),
		h.getVarNums());
	assertEquals(Arrays.asList(new Variable[] {v, v2}),
		h.getVars());
    }

    public void testGetSetProbability() {
	assertEquals(1.0,
		f.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "T", "T", "T"})));
	assertEquals(0.0,
		f.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "F", "F", "F"})));
    }

    public void testGetProbabilityError() {
	try {
	    f.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "T", "T"}));
	} catch (AssertionError e) {
	    return;
	}
	fail("Expected AssertionError");
    }


    public void testGetProbabilityError2() {
	try {
	    f.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "T", "T", "T", "T", "T"}));
	} catch (AssertionError e) {
	    return;
	}
	fail("Expected AssertionError");
    }

    public void testSetProbabilityError() {
	try {
	    f.setWeightList(Arrays.asList(new String[] {"T", "T", "T"}), 3.0);
	} catch (AssertionError e) {
	    return;
	}
	fail("Expected AssertionError");
    }

    public void testSetProbabilityError2() {
	try {
	    f.setWeightList(Arrays.asList(new String[] {"T", "T", "T", "T", "T"}), 3.0);
	} catch (AssertionError e) {
	    return;
	}
	fail("Expected AssertionError");
    }

    public void testSetProbabilityError3() {
	try {
	    f.setWeightList(Arrays.asList(new String[] {"T", "T", "T", "T"}), -1.0);	
	} catch (AssertionError e) {
	    return;
	}
	fail("Expected AssertionError");
    }

    public void testGetSetProbabilityWithVarNums() {
	assertEquals(2.0,
		f.getUnnormalizedProbability(Arrays.asList(new Integer[] {5, 3, 2, 0}), 
			Arrays.asList(new String[] {"U", "F", "T", "T"})));

	assertEquals(2.0,
		f.getUnnormalizedProbability(Arrays.asList(new Integer[] {7, 5, 3, 4, 2, 0, 1}), 
			Arrays.asList(new String[] {"U", "U", "F","T", "T", "T", "F"})));
    }

    public void testMarginalize() {
	Factor m = f.marginalize(Arrays.asList(new Integer[] {5, 2}));

	assertEquals(Arrays.asList(new Integer[] {0, 3}), m.getVarNums());
	assertEquals(1.0, 
		m.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "T"})));
	assertEquals(5.0,
		m.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "F"})));
    }

    public void testMarginalizeToNothing() {
	Factor m = f.marginalize(Arrays.asList(new Integer[] {0, 3, 2, 5}));

	assertEquals(6.0,
		m.getUnnormalizedProbability(Arrays.asList(new String[] {})));
    }

    public void testMaxMarginalize() {
	Factor m = f.maxMarginalize(Arrays.asList(new Integer[] {5, 2}));

	assertEquals(Arrays.asList(new Integer[] {0, 3}), m.getVarNums());
	assertEquals(3.0, 
		m.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "F"})));
	assertEquals(1.0,
		m.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "T"})));
    }

    public void testMaxMarginalizeToNothing() {
	Factor m = f.maxMarginalize(Arrays.asList(new Integer[] {0, 3, 2, 5}));

	assertEquals(3.0,
		m.getUnnormalizedProbability(Arrays.asList(new String[] {})));
    }

    public void testConditionalNone() {
	Factor c = f.conditional(new Assignment(Arrays.asList(new Integer[] {6, 8}),
			Arrays.asList(new Integer[] {1, 1})));
	// Nothing should change.
	assertEquals(1.0,
		c.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "T", "T", "T"})));
	assertEquals(0.0,
		c.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "F", "F", "F"})));
    }

    public void testConditionalAll() {
	Factor c = f.conditional(new Assignment(Arrays.asList(new Integer[] {0, 2, 3, 5}),
			Arrays.asList(new Integer[] {0, 0, 1, 0})));
	
	assertEquals(3.0,
		c.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "T", "F", "T"})));

	assertEquals(0.0,
		c.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "T", "T", "T"})));

	assertEquals(0.0,
		c.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "F", "F", "F"})));
    }

    public void testConditionalPartial() {
	Factor c = f.conditional(new Assignment(Arrays.asList(new Integer[] {0, 3}),
			Arrays.asList(new Integer[] {0, 1})));
	
	assertEquals(3.0,
		c.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "T", "F", "T"})));

	assertEquals(2.0,
		c.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "T", "F", "U"})));

	assertEquals(0.0,
		c.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "T", "T", "T"})));

	assertEquals(0.0,
		c.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "F", "F", "F"})));
    }

    public void testSumProduct() {
	List<Factor> factors = Arrays.asList(new Factor[] {f,g});
	Factor t = TableFactor.sumProductTableFactor(factors, 
		Arrays.asList(new Integer[] {0, 3}));

	assertEquals(90.0,
		t.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "F"})));
	assertEquals(0.0,
		t.getUnnormalizedProbability(Arrays.asList(new String[] {"F", "T"})));

	assertEquals(13.0,
		t.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "T"})));

	assertEquals(0.0,
		t.getUnnormalizedProbability(Arrays.asList(new String[] {"F", "F"})));
    }

    public void testMaxProduct() {
	List<Factor> factors = Arrays.asList(new Factor[] {f,g});
	Factor t = TableFactor.maxProductTableFactor(factors, 
		Arrays.asList(new Integer[] {0, 3}));

	assertEquals(33.0,
		t.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "F"})));
	assertEquals(0.0,
		t.getUnnormalizedProbability(Arrays.asList(new String[] {"F", "T"})));

	assertEquals(13.0,
		t.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "T"})));

	assertEquals(0.0,
		t.getUnnormalizedProbability(Arrays.asList(new String[] {"F", "F"})));
    }

    public void testProduct() {

	TableFactor t = TableFactor.productFactor(Arrays.asList(new Factor[] {f, g}));

	assertEquals(14.0,
		t.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "U", "T", "F", "U"})));

	assertEquals(0.0,
		t.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "U", "T", "F", "F"})));       
    }

    public void testProductEmptyFactor() {
	Factor m = f.marginalize(Arrays.asList(new Integer[] {0, 3, 2, 5}));

	TableFactor t = TableFactor.productFactor(Arrays.asList(new Factor[] {m, f}));

	assertEquals(18.0,
		t.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "T", "F", "T"})));
	
    }

    public void testComputeExpectation() {
	assertEquals(0.5,
		f.computeExpectation(feature));
    }

    public void testMostLikelyAssignments() {
	List<Assignment> likely = g.mostLikelyAssignments(2);
	
	assertEquals(2, likely.size());
	assertEquals(13.0, 
		g.getUnnormalizedProbability(likely.get(0)));
	assertEquals(11.0, 
		g.getUnnormalizedProbability(likely.get(1)));
    }
}