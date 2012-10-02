package com.jayantkrish.jklol.ccg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.ccg.CcgCategory.Argument;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.TableFactorBuilder;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.loglinear.IndicatorLogLinearFactor;
import com.jayantkrish.jklol.models.parametric.ListSufficientStatistics;
import com.jayantkrish.jklol.models.parametric.ParametricFactor;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.IndexedList;
import com.jayantkrish.jklol.util.Pair;

/**
 * Parameterized CCG grammar. This class instantiates CCG parsers given 
 * parameter vectors. This class parameterizes CCGs with probabilities for:
 * <ul>
 *  <li>Lexicon entries.
 *  <li>Dependency structures (i.e., semantic dependencies).
 * </ul>
 *
 * @author jayant
 */
public class ParametricCcgParser {
  
  private final VariableNumMap terminalVar;
  private final VariableNumMap ccgCategoryVar;   
  private final ParametricFactor terminalFamily;
  
  private final VariableNumMap dependencyHeadVar;
  private final VariableNumMap dependencyArgNumVar;	
  private final VariableNumMap dependencyArgVar;
  private final ParametricFactor dependencyFamily;
  
  private final String TERMINAL_PARAMETERS = "terminals";
  private final String DEPENDENCY_PARAMETERS = "dependencies";
  
  public ParametricCcgParser(VariableNumMap terminalVar, VariableNumMap ccgCategoryVar,
      ParametricFactor terminalFamily, VariableNumMap dependencyHeadVar, 
      VariableNumMap dependencyArgNumVar, VariableNumMap dependencyArgVar, 
      ParametricFactor dependencyFamily) {
    this.terminalVar = Preconditions.checkNotNull(terminalVar);
    this.ccgCategoryVar = Preconditions.checkNotNull(ccgCategoryVar);
    this.terminalFamily = Preconditions.checkNotNull(terminalFamily);
    this.dependencyHeadVar = Preconditions.checkNotNull(dependencyHeadVar);
    this.dependencyArgNumVar = Preconditions.checkNotNull(dependencyArgNumVar);
    this.dependencyArgVar = Preconditions.checkNotNull(dependencyArgVar);
    this.dependencyFamily = Preconditions.checkNotNull(dependencyFamily);
  }

  /**
   * Produces a parametric CCG parser from a lexicon, represented a series 
   * of phrase/CCG category mappings. Each mapping is given as a comma separated string,
   * whose first value is the phrase and whose second value is the CCG category string.
   * 
   * @param lexiconLines
   * @return
   */
  public static ParametricCcgParser parseFromLexicon(Iterable<String> lexiconLines) {
    // Parse out all of the categories, words, and semanticPredicates from the lexicon.
    IndexedList<CcgCategory> categories = IndexedList.create();
    IndexedList<List<String>> words = IndexedList.create();
    IndexedList<String> semanticPredicates = IndexedList.create();
    for (String lexiconLine : lexiconLines) {
      // Create the CCG category.
      Pair<ArrayList<String>, CcgCategory> line = parseLexiconLine(lexiconLine);
      words.add(line.getLeft());
      categories.add(line.getRight());
      
      // Store the heads of the dependencies as semantic predicates.
      addArgumentsToPredicateList(line.getRight().getHeads(), semanticPredicates);
      // Store any predicates from the subjects of the dependency structures.
      addArgumentsToPredicateList(line.getRight().getSubjects(), semanticPredicates);
      // Store any predicates from the objects of the dependency structures.
      addArgumentsToPredicateList(line.getRight().getObjects(), semanticPredicates);
    }
    
    // Build the terminal distribution.
    DiscreteVariable ccgCategoryType = new DiscreteVariable("ccgCategory", categories.items());
    DiscreteVariable wordType = new DiscreteVariable("words", words.items());
    
    VariableNumMap terminalVar = VariableNumMap.singleton(0, "words", wordType);
    VariableNumMap ccgCategoryVar = VariableNumMap.singleton(1, "ccgCategory", ccgCategoryType);
    VariableNumMap vars = terminalVar.union(ccgCategoryVar);
    TableFactorBuilder terminalBuilder = new TableFactorBuilder(vars, SparseTensorBuilder.getFactory());
    for (String lexiconLine : lexiconLines) {
      Pair<ArrayList<String>, CcgCategory> line = parseLexiconLine(lexiconLine);
      terminalBuilder.setWeight(vars.outcomeArrayToAssignment(line.getLeft(), 
          line.getRight()), 1.0);
    }
    ParametricFactor terminalParametricFactor = new IndicatorLogLinearFactor(vars, terminalBuilder.build());
        
    // Build the dependency distribution.
    DiscreteVariable semanticPredicateType = new DiscreteVariable("semanticPredicates", semanticPredicates.items());
    DiscreteVariable argumentNums = new DiscreteVariable("argNums", Ints.asList(1, 2, 3));

    VariableNumMap semanticHeadVar = VariableNumMap.singleton(0, "semanticHead", semanticPredicateType);
    VariableNumMap semanticArgNumVar = VariableNumMap.singleton(1, "semanticArgNum", argumentNums);
    VariableNumMap semanticArgVar = VariableNumMap.singleton(2, "semanticArg", semanticPredicateType);
    vars = VariableNumMap.unionAll(semanticHeadVar, semanticArgNumVar, semanticArgVar);

    // TODO: induce sparsity here.
    ParametricFactor dependencyParametricFactor = new IndicatorLogLinearFactor(vars, TableFactor.unity(vars));
    return new ParametricCcgParser(terminalVar, ccgCategoryVar, terminalParametricFactor, 
        semanticHeadVar, semanticArgNumVar, semanticArgVar, dependencyParametricFactor);
  }

  private static Pair<ArrayList<String>, CcgCategory> parseLexiconLine(String lexiconLine) {
    int commaIndex = lexiconLine.indexOf(",");
    // Add the lexicon word sequence to the lexicon.
    String wordPart = lexiconLine.substring(0, commaIndex);
    return Pair.of(Lists.newArrayList(wordPart.split(" ")),
        CcgCategory.parseFrom(lexiconLine.substring(commaIndex + 1)));
  }
  
  private static void addArgumentsToPredicateList(Collection<Argument> arguments, 
      IndexedList<String> semanticPredicates) {
    for (Argument arg : arguments) {
      if (arg.hasPredicate()) {
        semanticPredicates.add(arg.getPredicate());
      }
    }
  }

  /**
   * Gets a new all-zero parameter vector.
   *  
   * @return
   */
  public SufficientStatistics getNewSufficientStatistics() {
    SufficientStatistics terminalParameters = terminalFamily.getNewSufficientStatistics();
    SufficientStatistics dependencyParameters = dependencyFamily.getNewSufficientStatistics();
    
    return new ListSufficientStatistics(Arrays.asList(TERMINAL_PARAMETERS, DEPENDENCY_PARAMETERS),
        Arrays.asList(terminalParameters, dependencyParameters));
  }

  /**
   * Instantiates a {@code CcgParser} whose probability distributions are 
   * derived from {@code parameters}.
   *  
   * @param parameters
   * @return
   */
  public CcgParser getParserFromParameters(SufficientStatistics parameters) {
    ListSufficientStatistics parameterList = parameters.coerceToList();
    DiscreteFactor terminalDistribution = terminalFamily.getFactorFromParameters(
        parameterList.getStatisticByName(TERMINAL_PARAMETERS)).coerceToDiscrete();
    DiscreteFactor dependencyDistribution = dependencyFamily.getFactorFromParameters(
        parameterList.getStatisticByName(DEPENDENCY_PARAMETERS)).coerceToDiscrete();
    
    return new CcgParser(terminalVar, ccgCategoryVar, terminalDistribution, 
        dependencyHeadVar, dependencyArgNumVar, dependencyArgVar, dependencyDistribution);
  }
  
  /**
   * Increments {@code gradient} by {@code count * features(parse)}, where 
   * {@code features} is a function from CCG parses to a feature vectors. 
   *   
   * @param gradient
   * @param parse
   * @param count
   */
  public void incrementSufficientStatistics(SufficientStatistics gradient, CcgParse parse, 
      double count) {
    // Update the dependency structure parameters.
    SufficientStatistics dependencyGradient = gradient.coerceToList().getStatisticByName(DEPENDENCY_PARAMETERS);
    for (DependencyStructure dependencies : parse.getAllDependencies()) {
      Assignment assignment = Assignment.unionAll(
          dependencyHeadVar.outcomeArrayToAssignment(dependencies.getHead()),
          dependencyArgNumVar.outcomeArrayToAssignment(dependencies.getArgIndex()),
          dependencyArgVar.outcomeArrayToAssignment(dependencies.getObject()));

      dependencyFamily.incrementSufficientStatisticsFromAssignment(dependencyGradient, assignment, count);
    }
    
    // Update terminal distribution parameters.
    SufficientStatistics terminalGradient = gradient.coerceToList().getStatisticByName(TERMINAL_PARAMETERS);
    updateTerminalGradient(terminalGradient, parse, count);
  }
  
  private void updateTerminalGradient(SufficientStatistics terminalGradient, CcgParse parse, double count) {
    if (parse.isTerminal()) {
      Assignment assignment = Assignment.unionAll(
          terminalVar.outcomeArrayToAssignment(parse.getSpannedWords()),
          ccgCategoryVar.outcomeArrayToAssignment(parse.getLexiconEntry()));
      terminalFamily.incrementSufficientStatisticsFromAssignment(terminalGradient, assignment, count);
    } else {
      updateTerminalGradient(terminalGradient, parse.getLeft(), count);
      updateTerminalGradient(terminalGradient, parse.getRight(), count);
    }
  }
  

  public String getParameterDescription(SufficientStatistics parameters) {
    return getParameterDescription(parameters, -1);
  }
  
  public String getParameterDescription(SufficientStatistics parameters, int numFeatures) {
    ListSufficientStatistics parameterList = parameters.coerceToList();
    
    StringBuilder sb = new StringBuilder();
    sb.append(terminalFamily.getParameterDescription(
        parameterList.getStatisticByName(TERMINAL_PARAMETERS), numFeatures));
    sb.append(dependencyFamily.getParameterDescription(
        parameterList.getStatisticByName(DEPENDENCY_PARAMETERS), numFeatures));
    return sb.toString();
  }
}
