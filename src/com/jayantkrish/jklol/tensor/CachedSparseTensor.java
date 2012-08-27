package com.jayantkrish.jklol.tensor;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.util.PermutationIterator;

/**
 * A {@code SparseTensor} which internally caches the results of
 * {@code relabelDimensions()}. This class is useful because
 * {@code SparseTensor}s must have their dimensions aligned before performing
 * mathematical operations (products, sums, etc). Hence, this class can
 * dramatically improve performance when there are large tensors that are
 * operated on repeatedly in the course of a computation.
 */
public class CachedSparseTensor extends SparseTensor {

  private static final long serialVersionUID = 8039031224458210634L;

  private final Map<List<Integer>, SparseTensor> tensorCache;

  /**
   * Gets a tensor with the specified dimensions, caching all permutations of
   * values in {@code tensorCache}.
   * 
   * @param dimensionNums
   * @param dimensionSizes
   * @param keyNums
   * @param values
   * @param tensorCache
   */
  public CachedSparseTensor(int[] dimensionNums, int[] dimensionSizes,
      long[] keyNums, double[] values, Map<List<Integer>, SparseTensor> tensorCache) {
    super(dimensionNums, dimensionSizes, keyNums, values);
    this.tensorCache = Maps.newHashMap(tensorCache);
  }

  /**
   * Construct a {@code CachedSparseTensor} which caches out all possible
   * permutations of the dimensions of {@code tensor}. The returned tensor is
   * functionally identical to {@code tensor}, but may be faster for certain
   * operations.
   * 
   * @param tensor
   * @return
   */
  public static CachedSparseTensor cacheAllPermutations(SparseTensor tensor) {
    Map<List<Integer>, SparseTensor> tensorCache = Maps.newHashMap();
    Iterator<int[]> permutationIterator = new PermutationIterator(tensor.getDimensionNumbers().length);
    while (permutationIterator.hasNext()) {
      List<Integer> currentPermutation = Ints.asList(permutationIterator.next());
      tensorCache.put(currentPermutation, tensor.relabelDimensions(
          Ints.toArray(currentPermutation)));
    }
    return new CachedSparseTensor(tensor.getDimensionNumbers(), tensor.getDimensionSizes(),
        tensor.keyNums, tensor.values, tensorCache);
  }

  public static TensorFactory getFactory() {
    return new TensorFactory() {
      @Override
      public TensorBuilder getBuilder(int[] dimNums, int[] dimSizes) {
        return new CachedSparseTensorBuilder(dimNums, dimSizes);
      }
    };
  }

  @Override
  public SparseTensor relabelDimensions(int[] newDimensions) {
    Preconditions.checkArgument(newDimensions.length == numDimensions());

    // Compute how {@code newDimensions} permutes the indexes of this.
    int[] sortedDims = Arrays.copyOf(newDimensions, newDimensions.length);
    Arrays.sort(sortedDims);

    Map<Integer, Integer> newDimensionIndexes = Maps.newHashMap();
    for (int i = 0; i < sortedDims.length; i++) {
      newDimensionIndexes.put(sortedDims[i], i);
    }

    int[] permutation = new int[newDimensions.length];
    for (int i = 0; i < newDimensions.length; i++) {
      permutation[i] = newDimensionIndexes.get(newDimensions[i]);
    }

    if (tensorCache.containsKey(Ints.asList(permutation))) {
      return tensorCache.get(Ints.asList(permutation)).relabelDimensions(sortedDims);
    } else {
      return super.relabelDimensions(newDimensions);
    }
  }

  /**
   * A builder for {@code CachedSparseTensor}s, which is essentially identical
   * to {@code SparseTensorBuilder}, except that on calling build() all permuted
   * versions of the tensor are cached.
   * 
   * @author jayantk
   */
  private static class CachedSparseTensorBuilder extends SparseTensorBuilder {
    private static final long serialVersionUID = 2735943253861954626L;

    public CachedSparseTensorBuilder(int[] dimensionNums, int[] dimensionSizes) {
      super(dimensionNums, dimensionSizes);
    }

    @Override
    public CachedSparseTensor build() {
      SparseTensor tensor = super.build();
      return CachedSparseTensor.cacheAllPermutations(tensor);
    }
  }
}