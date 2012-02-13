package com.jayantkrish.jklol.preprocessing;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.tensor.TensorBase.KeyValue;
import com.jayantkrish.jklol.util.IndexedList;

/**
 * Generates a feature vector by mapping each possible feature to a unique index
 * in a vector. {@code DictionaryFeatureVectorGenerator} represents the standard
 * method for mapping generated features into a vector.
 * 
 * @author jayantk
 */
public class DictionaryFeatureVectorGenerator<T, U> implements FeatureVectorGenerator<T> {

  private final IndexedList<U> featureIndexes;
  private final FeatureGenerator<T, U> generator;
  private final boolean ignoreOovFeatures;

  /**
   * 
   * @param featureIndexes
   * @param generator
   * @param ignoreOovFeatures if {@code true}, any features generated by
   * {@code generator} which are not in {@code featureIndexes} are ignored. If
   * {@code false}, this class throws an exception when such a feature is
   * generated.
   */
  public DictionaryFeatureVectorGenerator(IndexedList<U> featureIndexes,
      FeatureGenerator<T, U> generator, boolean ignoreOovFeatures) {
    this.featureIndexes = Preconditions.checkNotNull(featureIndexes);
    this.generator = Preconditions.checkNotNull(generator);
    this.ignoreOovFeatures = ignoreOovFeatures;
  }

  /**
   * Creates a {@code DictionaryFeatureVectorGenerator} which contains every
   * feature generated by running {@code generator} on {@code data}. A common
   * use of this method is to initialize a fixed vocabulary of features from a
   * training data set.
   * 
   * @param data
   * @param generator
   * @param ignoreOovFeatures
   * @return
   */
  public static <T, U> DictionaryFeatureVectorGenerator<T, U> createFromData(Collection<T> data,
      FeatureGenerator<T, U> generator, boolean ignoreOovFeatures) {
    IndexedList<U> features = new IndexedList<U>();
    for (T datum : data) {
      features.addAll(generator.generateFeatures(datum).keySet());
    }
    return new DictionaryFeatureVectorGenerator<T, U>(features, generator, ignoreOovFeatures);
  }

  @Override
  public Tensor apply(T item) {
    Map<U, Double> featureCounts = generator.generateFeatures(item);
    SparseTensorBuilder featureBuilder = new SparseTensorBuilder(new int[] { 0 }, new int[] { getNumberOfFeatures() });
    for (Map.Entry<U, Double> entry : featureCounts.entrySet()) {
      if (!featureIndexes.contains(entry.getKey())) {
        // The generator instantiated a feature which is not mapped to an index.
        Preconditions.checkState(ignoreOovFeatures, "Generated an out-of-vocabulary feature: " + entry.getKey());
        continue;
      }
      featureBuilder.incrementEntry(entry.getValue(), featureIndexes.getIndex(entry.getKey()));
    }
    return featureBuilder.build();
  }

  @Override
  public int getNumberOfFeatures() {
    return featureIndexes.size();
  }

  /**
   * Given a feature vector, get the features which were active for it. This
   * method inverts the mapping from feature sets to feature vectors performed
   * by this class.
   * 
   * @param featureVector
   * @return
   */
  public Map<U, Double> getActiveFeatures(Tensor featureVector) {
    Preconditions.checkArgument(featureVector.getDimensionSizes().length == 1
        && featureVector.getDimensionSizes()[0] == getNumberOfFeatures());

    Map<U, Double> features = Maps.newHashMap();
    Iterator<KeyValue> keyValueIter = featureVector.keyValueIterator();
    while (keyValueIter.hasNext()) {
      KeyValue featureKeyValue = keyValueIter.next();
      features.put(featureIndexes.get(featureKeyValue.getKey()[0]), featureKeyValue.getValue());
    }
    return features;
  }
}
