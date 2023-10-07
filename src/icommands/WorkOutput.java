package icommands;

import work.WorkContext;
import work.WorkOutputType;

/**
 * Outcome of Work's {@link IWork#doWork(WorkContext)}. It may still be
 * {@link #inWait()} for the final output. Once done waiting, the result
 */

//@JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class, property = "@id")
//@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
//@JsonAutoDetect(getterVisibility = Visibility.NONE, setterVisibility = Visibility.NONE, fieldVisibility = Visibility.ANY)
public interface WorkOutput {

    /**
     * Get a descriptive message, based on whether in wait or outcome.
     * @return the message
     */
    String getMessage();

    /**
     * Get the result type of this output. The return value is undefined if still
     * {@link #inWait()}.
     * @return the output type
     */
    WorkOutputType getType();

    /**
     * Whether the output is still pending on some asynchronous operations. If
     * true, {@link #update(WorkContext)} will be invoked.
     * @return true if waiting
     */
    boolean inWait();

    /**
     * Notify for an updated {@link WorkOutput}.
     * @param ctx the context
     * @return the new or updated output, which may be completed or require yet  more wait
     */
    WorkOutput update(WorkContext ctx);

    /**
     * Abort the output. Only invoked if the output is still in wait.
     * Implementations should try to halt any ongoing work immediately, but
     * reserves the right to remain in wait.
     *
     * @param ctx the context
     * @return true continue respecting this WorkOutput and use it for any further
     *         operation else return false, to indicate that the caller should
     *         treat it as aborted.
     */
    boolean onAbort(WorkContext ctx);

    /**
     * Set internal state - like UI message and flags - when finishing.
     * @param msg the message for the UI to be updated
     * @param success whether this {@code WorkOutput} has been successful
     */
    default WorkOutput finish(String msg, boolean success) {
        return this;
    }

}
