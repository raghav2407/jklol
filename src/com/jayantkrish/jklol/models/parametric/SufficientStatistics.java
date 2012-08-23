package com.jayantkrish.jklol.models.parametric;

import java.io.Serializable;

/**
 * The sufficient statistics of a {@code FactorGraph}. This class represents
 * (expected) occurrence counts for a set of events which can be used to
 * estimate a {@code FactorGraph}. {@code SufficientStatistics} may also
 * represent the parameter vector for a graphical model, since a model family's
 * parameters have the same dimensionality as its sufficient statistics.
 * 
 * @author jayantk
 */
public interface SufficientStatistics extends Serializable {

  /**
   * Adds the event counts of {@code other} to {@code this}. Equivalent to
   * {@code this = this + (multiplier * other)} for each event in {@code this}.
   * 
   * @param other
   * @param multiplier
   */
  public void increment(SufficientStatistics other, double multiplier);

  /**
   * Increments the statistic/parameter values by their corresponding amounts in
   * {@code other}. This increment uses feature names to determine the
   * correspondence, meaning this method can be used to transfer parameters from
   * one model to another, provided that the parameters have the same names in
   * both models.
   * <p>
   * Parameters which are present in only one model are not incremented.
   * 
   * @param other
   */
  public void transferParameters(SufficientStatistics other);

  /**
   * Adds a constant to each event count. This method is useful for performing
   * add-one smoothing when estimating {@link ParametricFactorGraph}s.
   * 
   * @param amount
   */
  public void increment(double amount);

  /**
   * Multiplies each event count in {@code this} by {@code amount}.
   * 
   * @param amount
   */
  public void multiply(double amount);

  /**
   * Increments each element of {@code this} with a random perturbation. Each
   * element is drawn independently from a mean-0 Gaussian with standard
   * deviation {@code stddev}.
   * 
   * @param variance
   */
  public void perturb(double stddev);

  /**
   * Returns a deep copy of this vector of sufficient statistics. Mutating this
   * object will not affect the values in the returned object, and vice versa.
   * 
   * @return
   */
  public SufficientStatistics duplicate();

  /**
   * Computes the standard inner (dot) product between this vector and
   * {@code other}.
   * 
   * @param other
   * @return
   */
  public double innerProduct(SufficientStatistics other);

  /**
   * Gets the L2 norm of {@code this}, treating {@code this} as a vector. In
   * other words, this method returns the square root of the sum of the squares
   * of each entry.
   * 
   * @param exponent
   * @return
   */
  public double getL2Norm();

  /**
   * Attempts to convert {@code this} into a {@link ListSufficientStatistics}.
   * Throws {@code CoercionError} if conversion is not possible.
   * 
   * @return
   */
  public ListSufficientStatistics coerceToList();
}
