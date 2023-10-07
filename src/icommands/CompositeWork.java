// Copyright (c) 2015 Cloudera, Inc. All rights reserved.
package icommands;

import work.Step;

import java.util.List;

/**
 * Works that composes others into a single unit of work.
 */
public interface CompositeWork extends IWork {

  /**
   * Steps encapsulated within this work.
   *
   * @return the steps
   */
  List<Step> getSteps();

  /**
   * Get information on the nested works.
   *
   * @param ctx
   * @return
   */
  //@JsonIgnore
  //List<ProgressSummary> getProgressSummaries(WorkContext ctx);

  /**
   * Whether the nested works are done in parallel.
   */
  //@JsonIgnore
  boolean isParallel();

  /**
   * Used by test code to update command steps (optional method).
   * This can't be called "setSteps", because we don't want Jackson
   * to auto-detect it as a setter.
   *
   * @param steps command steps.
   */
  void updateSteps(List<Step> steps);
}
