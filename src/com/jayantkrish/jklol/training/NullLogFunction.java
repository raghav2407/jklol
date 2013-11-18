package com.jayantkrish.jklol.training;

import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.util.Assignment;

/**
 * A {@link LogFunction} which doesn't log anything.
 * 
 * @author jayantk
 */
public class NullLogFunction implements LogFunction {
  
  public NullLogFunction() { super(); }

  @Override
  public void log(Assignment example, FactorGraph graph) {}

  @Override
  public void log(int iteration, int exampleNum, Assignment example, FactorGraph graph) {}
  
  @Override
  public void logMessage(Object message) {};
  
  @Override
  public void logParameters(int iteration, SufficientStatistics parameters) {}

  @Override
  public void logStatistic(int iteration, String statisticName, double value) {}

  @Override
  public void notifyIterationStart(int iteration) {}

  @Override
  public void notifyIterationEnd(int iteration) {}

  @Override
  public void startTimer(String timerName) {}

  @Override
  public double stopTimer(String timerName) { return 0; }
}
