package com.jayantkrish.jklol.cvsm.eval;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.cvsm.eval.Value.ConstantValue;

public class Eval {

  public EvalResult eval(SExpression expression, Environment environment) {
    if (expression.isConstant()) {
      return new EvalResult(environment.getValue(expression.getConstant()), environment);
    } else {
      List<SExpression> subexpressions = expression.getSubexpressions();
      SExpression first = subexpressions.get(0); 
      if (first.isConstant()) {
        String constantName = first.getConstant();
        // Check for syntactic primitives (define, lambda, etc.)
        if (constantName.equals("define")) {
          // Binds a name to a value in the environment.
          String nameToBind = subexpressions.get(1).getConstant();
          Value valueToBind = eval(subexpressions.get(2), environment).getValue();
          Environment newEnvironment = environment.bindName(nameToBind, valueToBind);
          return new EvalResult(ConstantValue.UNDEFINED, newEnvironment);
        } else if (constantName.equals("begin")) {
          // Sequentially evaluates its subexpressions, chaining any 
          // environment changes.
          EvalResult result = new EvalResult(ConstantValue.UNDEFINED, environment);
          for (int i = 1; i < subexpressions.size(); i++) {
            result = eval(subexpressions.get(i), result.getEnvironment());
          }
          return result;
        } else if (constantName.equals("lambda")) {
          // Create and return a function value representing this function.
          Preconditions.checkArgument(subexpressions.size() == 3);
          
          List<String> argumentNames = Lists.newArrayList();
          List<SExpression> argumentExpressions = subexpressions.get(1).getSubexpressions();
          for (SExpression argumentExpression : argumentExpressions) {
            Preconditions.checkArgument(argumentExpression.isConstant());
            argumentNames.add(argumentExpression.getConstant());
          }

          SExpression functionBody = subexpressions.get(2); 
          return new EvalResult(new FunctionValue(argumentNames, functionBody, environment),
              environment);
        }
      }

      // Default case: perform function application.
      List<Value> values = Lists.newArrayList();
      for (SExpression subexpression : subexpressions) {
        values.add(eval(subexpression, environment).getValue());
      }

      FunctionValue functionToApply = (FunctionValue) values.get(0);
      List<Value> arguments = values.subList(1, values.size());
      Value result = functionToApply.apply(arguments, this);
      return new EvalResult(result, environment);
    }
  }

  public static class EvalResult {
    private final Value value;
    private final Environment environment;

    public EvalResult(Value value, Environment environment) {
      this.value = Preconditions.checkNotNull(value);
      this.environment = Preconditions.checkNotNull(environment);
    }

    public Value getValue() {
      return value;
    }

    public Environment getEnvironment() {
      return environment;
    }
  }
}
