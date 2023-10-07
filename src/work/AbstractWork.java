// Copyright (c) 2015 Cloudera, Inc. All rights reserved.
package work;


import icommands.IWork;

public abstract class AbstractWork implements IWork {

  @Override
  public IWork retry(WorkContext ctx, boolean dryRun) {
    throw new UnsupportedOperationException(String.format(
        "IWork %s does not support retry", this.getClass().getName()));
  }

}
