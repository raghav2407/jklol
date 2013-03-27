package com.jayantkrish.jklol.preprocessing;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jayantkrish.jklol.util.CountAccumulator;

/**
 * Utilities for manipulating {@link FeatureGenerator}s.
 * 
 * @author jayantk
 */
public class FeatureGenerators {

  /**
   * Combines many {@code FeatureGenerator}s into a single
   * {@code FeatureGenerator}. The returned generator returns the union of all
   * features generated by {@code generators}. If multiple generators output the
   * same feature, the returned generator sums their counts.
   */
  public static <A, B> FeatureGenerator<A, B> combinedFeatureGenerator(
      Iterable<FeatureGenerator<A, ? extends B>> generators) {
    return new CombinedFeatureGenerator<A, B>(generators);
  }

  /**
   * See {@link #combinedFeatureGenerator(Iterable)}.
   * 
   * @param generators
   * @return
   */
  public static <A, B> FeatureGenerator<A, B> combinedFeatureGenerator(
      FeatureGenerator<A, ? extends B>... generators) {
    return FeatureGenerators.combinedFeatureGenerator(Arrays.asList(generators));
  }

  /**
   * Constructs features from several {@code FeatureGenerators} by taking
   * products of their generated features (e.g., if the generated features are
   * indicators, this constructs features which are the logical AND of the
   * generated features). This operation will dramatically increase the size
   * number of generated features.
   * 
   * @return
   */
  public static <A, B> FeatureGenerator<A, List<B>> productFeatureGenerator(
      Iterable<FeatureGenerator<A, ? extends B>> generators) {
    return new ProductFeatureGenerator<A, B>(generators);
  }

  /**
   * See {@link #productFeatureGenerator(Iterable)}.
   * 
   * @param generators
   * @return
   */
  public static <A, B> FeatureGenerator<A, List<B>> productFeatureGenerator(
      FeatureGenerator<A, ? extends B>... generators) {
    return FeatureGenerators.productFeatureGenerator(Arrays.asList(generators));
  }
  
  public static <A, B> CountAccumulator<B> getFeatureCounts(FeatureGenerator<A, B> featureGenerator,
      Iterable<? extends A> data) {
    CountAccumulator<B> featureCounts = CountAccumulator.create();
    for (A datum : data) {
      Map<B, Double> datumFeatures = featureGenerator.generateFeatures(datum);
      featureCounts.increment(datumFeatures);
    }
    return featureCounts; 
  }

  /**
   * Combines many {@code FeatureGenerator}s into a single
   * {@code FeatureGenerator} which returns the sum of each base generator's
   * feature counts.
   * 
   * @author jayantk
   * @param <A>
   * @param <B>
   */
  private static class CombinedFeatureGenerator<A, B> implements FeatureGenerator<A, B> {

    private final Iterable<FeatureGenerator<A, ? extends B>> generators;

    public CombinedFeatureGenerator(Iterable<FeatureGenerator<A, ? extends B>> generators) {
      this.generators = generators;
    }

    @Override
    public Map<B, Double> generateFeatures(A item) {
      CountAccumulator<B> featureCounts = CountAccumulator.create();
      for (FeatureGenerator<A, ? extends B> generator : generators) {
        featureCounts.increment(generator.generateFeatures(item));
      }
      return featureCounts.getCountMap();
    }
  }
  
  private static class ProductFeatureGenerator<A, B> implements FeatureGenerator<A, List<B>> {
    private final List<FeatureGenerator<A, ? extends B>> generators;

    public ProductFeatureGenerator(Iterable<FeatureGenerator<A, ? extends B>> generators) {
      this.generators = Lists.newArrayList(generators);
    }

    @Override
    public Map<List<B>, Double> generateFeatures(A item) {
      // Generate features for each wrapped generator.
      List<Map<? extends B, Double>> generatedFeatures = Lists.newArrayList();
      List<B> currentKey = Lists.newArrayList();
      for (FeatureGenerator<A, ? extends B> generator : generators) {
        generatedFeatures.add(generator.generateFeatures(item));
        currentKey.add(null);
      }
      
      // Return the products of all generated features.
      Map<List<B>, Double> counts = Maps.newHashMap();
      recursivelyPopulateCounts(0, generatedFeatures, currentKey, 1.0, counts);
      return counts;
    }
    
    private void recursivelyPopulateCounts(int index, List<Map<? extends B, Double>> generatedFeatures, 
        List<B> currentKey, double currentWeight, Map<List<B>, Double> counts) {
      if (index >= generators.size()) {
        counts.put(ImmutableList.copyOf(currentKey), currentWeight);
        return;
      } else {
        Map<? extends B, Double> currentFeatures = generatedFeatures.get(index);
        for (B key : currentFeatures.keySet()) {
          currentKey.set(index, key);
          recursivelyPopulateCounts(index + 1, generatedFeatures, currentKey, currentWeight * currentFeatures.get(key), counts);
        }
      }
    }
  }
}
