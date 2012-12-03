package com.jayantkrish.jklol.cli;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.CcgExample;
import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.DependencyStructure;
import com.jayantkrish.jklol.util.IoUtils;

/**
 * Parses input sentences using a trained CCG parser.
 * 
 * @author jayant
 */
public class ParseCcg {

  public static void printCcgParses(List<CcgParse> parses, int numParses) {
    for (int i = 0; i < Math.min(parses.size(), numParses); i++) {
      if (i > 0) {
        System.out.println("---");
      }
      System.out.println("HEAD: " + parses.get(i).getSemanticHeads());
      System.out.println("SYN: " + parses.get(i).getSyntacticCategory());
      System.out.println("DEPS: " + parses.get(i).getAllDependencies());
      System.out.println("LEX: " + parses.get(i).getSpannedLexiconEntries());
      System.out.println("PROB: " + parses.get(i).getSubtreeProbability());
    }
  }

  public static void main(String[] args) {
    OptionParser parser = new OptionParser();
    // Required arguments.
    OptionSpec<String> model = parser.accepts("model").withRequiredArg().ofType(String.class).required();
    // Optional arguments
    OptionSpec<Integer> beamSize = parser.accepts("beamSize").withRequiredArg().ofType(Integer.class).defaultsTo(100);
    OptionSpec<Integer> numParses = parser.accepts("numParses").withRequiredArg().ofType(Integer.class).defaultsTo(1);
    // If provided, running this program computes test error using the
    // given file. Otherwise, this program parses a string provided on
    // the command line. The format of testFile is the same as
    // expected by TrainCcg to train a CCG parser.
    OptionSpec<String> testFile = parser.accepts("test").withRequiredArg().ofType(String.class);
    OptionSet options = parser.parse(args);

    // Read the parser.
    CcgParser ccgParser = IoUtils.readSerializedObject(options.valueOf(model), CcgParser.class);

    if (options.has(testFile)) {
      // Parse all test examples.
      List<CcgExample> testExamples = Lists.newArrayList();
      for (String line : IoUtils.readLines(options.valueOf(testFile))) {
        testExamples.add(CcgExample.parseFromString(line));
      }
      CcgLoss loss = runTestSetEvaluation(ccgParser, testExamples, options.valueOf(beamSize));
      System.out.println(loss);
    } else {
      // Parse a string from the command line.
      List<String> sentenceToParse = options.nonOptionArguments();
      List<CcgParse> parses = ccgParser.beamSearch(sentenceToParse, options.valueOf(beamSize));
      printCcgParses(parses, options.valueOf(numParses));
    }

    System.exit(0);
  }

  public static CcgLoss runTestSetEvaluation(CcgParser ccgParser, Iterable<CcgExample> testExamples,
      int beamSize) {
    int labeledTp = 0, labeledFp = 0, labeledFn = 0, unlabeledTp = 0, unlabeledFp = 0, unlabeledFn = 0;
    int numParsed = 0, numExamples = 0;
    for (CcgExample example : testExamples) {
      List<CcgParse> parses = ccgParser.beamSearch(example.getWords(), beamSize);
      System.out.println("SENT: " + example.getWords());
      printCcgParses(parses, 1);

      if (parses.size() > 0) {
        List<DependencyStructure> predictedDeps = parses.get(0).getAllDependencies();
        Set<DependencyStructure> trueDeps = example.getDependencies();
        System.out.println("Predicted: ");
        for (DependencyStructure dep : predictedDeps) {
          if (trueDeps.contains(dep)) {
            System.out.println(dep);
          } else {
            System.out.println(dep + "\tINCORRECT");
          }
        }

        System.out.println("Missing true dependencies:");
        for (DependencyStructure dep : trueDeps) {
          if (!predictedDeps.contains(dep)) {
            System.out.println(dep);
          }
        }

        // Compute the correct / incorrect labeled dependencies for
        // the current example.
        Set<DependencyStructure> incorrectDeps = Sets.newHashSet(predictedDeps);
        incorrectDeps.removeAll(trueDeps);
        Set<DependencyStructure> correctDeps = Sets.newHashSet(predictedDeps);
        correctDeps.retainAll(trueDeps);
        int correct = correctDeps.size();
        int falsePositive = predictedDeps.size() - correctDeps.size();
        int falseNegative = trueDeps.size() - correctDeps.size();
        System.out.println();
        double precision = ((double) correct) / (correct + falsePositive);
        double recall = ((double) correct) / (correct + falseNegative);
        System.out.println("Labeled Precision: " + precision);
        System.out.println("Labeled Recall: " + recall);

        // Update the labeled dependency score accumulators for the
        // whole data set.
        labeledTp += correct;
        labeledFp += falsePositive;
        labeledFn += falseNegative;

        // Compute the correct / incorrect unlabeled dependencies.
        Set<DependencyStructure> unlabeledPredicted = stripDependencyLabels(predictedDeps);
        trueDeps = stripDependencyLabels(trueDeps);
        incorrectDeps = Sets.newHashSet(unlabeledPredicted);
        incorrectDeps.removeAll(trueDeps);
        correctDeps = Sets.newHashSet(unlabeledPredicted);
        correctDeps.retainAll(trueDeps);
        correct = correctDeps.size();
        falsePositive = unlabeledPredicted.size() - correctDeps.size();
        falseNegative = trueDeps.size() - correctDeps.size();
        precision = ((double) correct) / (correct + falsePositive);
        recall = ((double) correct) / (correct + falseNegative);
        System.out.println("Unlabeled Precision: " + precision);
        System.out.println("Unlabeled Recall: " + recall);

        unlabeledTp += correct;
        unlabeledFp += falsePositive;
        unlabeledFn += falseNegative;

        numParsed += 1;
      }
      numExamples++;
    }

    return new CcgLoss(labeledTp, labeledFp, labeledFn, unlabeledTp, unlabeledFp, unlabeledFn,
        numParsed, numExamples);
  }

  private static Set<DependencyStructure> stripDependencyLabels(Collection<DependencyStructure> dependencies) {
    Set<DependencyStructure> deps = Sets.newHashSet();
    for (DependencyStructure oldDep : dependencies) {
      deps.add(new DependencyStructure("", oldDep.getHeadWordIndex(),
          "", oldDep.getObjectWordIndex(), 0));
    }
    return deps;
  }

  public static class CcgLoss {
    private final int labeledTruePositives;
    private final int labeledFalsePositives;
    private final int labeledFalseNegatives;

    private final int unlabeledTruePositives;
    private final int unlabeledFalsePositives;
    private final int unlabeledFalseNegatives;

    private final int numExamplesParsed;
    private final int numExamples;

    public CcgLoss(int labeledTruePositives, int labeledFalsePositives, int labeledFalseNegatives,
        int unlabeledTruePositives, int unlabeledFalsePositives, int unlabeledFalseNegatives,
        int numExamplesParsed, int numExamples) {
      this.labeledTruePositives = labeledTruePositives;
      this.labeledFalsePositives = labeledFalsePositives;
      this.labeledFalseNegatives = labeledFalseNegatives;

      this.unlabeledTruePositives = unlabeledTruePositives;
      this.unlabeledFalsePositives = unlabeledFalsePositives;
      this.unlabeledFalseNegatives = unlabeledFalseNegatives;

      this.numExamplesParsed = numExamplesParsed;
      this.numExamples = numExamples;
    }

    /**
     * Gets labeled dependency precision, which is the percentage of
     * predicted labeled dependencies present in the gold standard
     * parse. Labeled dependencies are word-word dependencies with a
     * specified argument slot.
     * 
     * @return
     */
    public double getLabeledDependencyPrecision() {
      return ((double) labeledTruePositives) / (labeledTruePositives + labeledFalsePositives);
    }

    /**
     * Gets labeled dependency recall, which is the percentage of the
     * gold standard labeled dependencies present in the predicted
     * parse. Labeled dependencies are word-word dependencies with a
     * specified argument slot.
     * 
     * @return
     */
    public double getLabeledDependencyRecall() {
      return ((double) labeledTruePositives) / (labeledTruePositives + labeledFalseNegatives);
    }

    public double getLabeledDependencyFScore() {
      double precision = getLabeledDependencyPrecision();
      double recall = getLabeledDependencyRecall();
      return (2 * precision * recall) / (precision + recall);
    }

    /**
     * Gets unlabeled dependency precision, which is the percentage of
     * predicted unlabeled dependencies present in the gold standard
     * parse. Unlabeled dependencies are word-word dependencies,
     * ignoring the precise argument slot.
     * 
     * @return
     */
    public double getUnlabeledDependencyPrecision() {
      return ((double) unlabeledTruePositives) / (unlabeledTruePositives + unlabeledFalsePositives);
    }

    /**
     * Gets unlabeled dependency recall, which is the percentage of
     * the gold standard unlabeled dependencies present in the
     * predicted parse. Unlabeled dependencies are word-word
     * dependencies, ignoring the precise argument slot.
     * 
     * @return
     */
    public double getUnlabeledDependencyRecall() {
      return ((double) unlabeledTruePositives) / (unlabeledTruePositives + unlabeledFalseNegatives);
    }

    public double getUnlabeledDependencyFScore() {
      double precision = getUnlabeledDependencyPrecision();
      double recall = getUnlabeledDependencyRecall();
      return (2 * precision * recall) / (precision + recall);
    }

    /**
     * Gets the fraction of examples in the test set for which a CCG
     * parse was produced.
     * 
     * @return
     */
    public double getCoverage() {
      return ((double) numExamplesParsed) / numExamples;
    }

    /**
     * Gets the number of examples in the test set.
     * 
     * @return
     */
    public int getNumExamples() {
      return numExamples;
    }

    @Override
    public String toString() {
      return "Labeled Precision: " + getLabeledDependencyPrecision() + "\nLabeled Recall: "
          + getLabeledDependencyRecall() + "\nLabeled F Score: " + getLabeledDependencyFScore()
          + "\nUnlabeled Precision: " + getUnlabeledDependencyPrecision() + "\nUnlabeled Recall: "
          + getUnlabeledDependencyRecall() + "\nUnlabeled F Score: " + getUnlabeledDependencyFScore()
          + "\nCoverage: " + getCoverage();
    }
  }
}
