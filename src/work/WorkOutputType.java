// Copyright (c) 2012 Cloudera, Inc. All rights reserved.
package work;


import icommands.IWork;

/**
 * Types of output that may be returned by a {@link IWork}. WorkFlow
 * implementations can decide how to advance itself based on the type of output.
 */
//@JsonAutoDetect(getterVisibility = Visibility.NONE, fieldVisibility = Visibility.ANY)
public enum WorkOutputType {
  /**
   * Work was completely successfully.
   */
  SUCCESS,
  /**
   * Work has failed.
   */
  FAILURE,
  /**
   * Work was aborted. Not to be returned by WorkOutput implementation, but only
   * returned by framework when work output is aborted.
   */
  ABORTED
}
