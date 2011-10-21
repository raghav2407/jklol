package com.jayantkrish.jklol.inference;

import com.jayantkrish.jklol.util.Assignment;

/**
 * Max marginals, or maximal probability assignments, for a graphical model.
 * This class supports retrieving the top-{@code #beamSize()} maximal
 * probability assignments, in order of probability.
 * 
 * @author jayant
 */
public interface MaxMarginalSet {

  /**
   * Gets the number of maximal probability assignments contained in {@code
   * this}.
   * 
   * @return
   */
  int beamSize();

  /**
   * Gets the {@code n}th most probable assignment. Assignments are zero
   * indexed, and therefore {@code n} must be less than {@link #beamSize()}.
   * 
   * @param n
   * @return
   */
  Assignment getNthBestAssignment(int n);
}
