package icommands;

import work.SeqWork;
import work.WorkContext;

/**
 * A independent piece of work. It may require some inputs as field variables,
 * make use of {CmdWorkCtx} during the execution of the work, and finally
 * produce a {WorkOutput} and possibly other outputs as field variables.
 * The CmdWork may be persisted throughout its life cycle, both before
 * { #doWork(CmdWorkCtx)} and after. It is important for all its fields to
 * either support or be ignored during JSON serialization.
 *
 * CmdWork may be included into composite CmdWorks such as {@link SeqWork}
 * and {ScatterCmdWork}, but it should make no assumption on how it may be
 * incorporated in different types of work flows.
 */

//@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
//@JsonAutoDetect(getterVisibility = Visibility.NONE, setterVisibility = Visibility.NONE, fieldVisibility = Visibility.ANY)
public interface IWork {
    /**
     * Perform the work.
     *
     * @param ctx the context that supports the execution of the work
     * @throws RuntimeException will result in the {@link WorkContext}
     * being rolled back.
     * @return the result
     */
    WorkOutput doWork(WorkContext ctx);


    /**
     * Callback after this work has either completed or been aborted. Cannot
     * affect the outcome of this work, or cause the transaction to be rolled
     * back, even if RuntimeException is thrown.
     *
     * @param output
     *          the generated output
     * @param ctx
     *          the context
     */
    void onFinish(WorkOutput output, WorkContext ctx);

    /**
     * Return an instance that can be retried. It next time it will run it should
     * start from last failing step.
     *
     * @param ctx
     * @param dryRun to indicate that this is a dry run. When <code>true</code>
     *          any changes to existing state of world must be avoided. The intent
     *          of this boolean variable is make sure if the workflow is prepared
     *          for retry it should pass. When true do not make or persist any
     *          changes. Just check that the workflow can be retried.
     * @return An instance of CmdWork that can be retried.
     * @throws UnsupportedOperationException Implementation does not support
     *           retry.
     * @throws IllegalStateException the CmdWork instance is not in a state where
     *           it can be retried.
     */
    IWork retry(WorkContext ctx, boolean dryRun);
}



