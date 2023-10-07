package work;

import icommands.CompositeWork;
import icommands.IWork;
import icommands.WorkOutput;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import com.google.common.base.Preconditions;


public class Step implements WorkOutput {

    public static final boolean DEFAULT_IGNORE_FAILURE = false;
    public static final long DEFAULT_TIMEOUT = 0L;
    private final Logger LOG = Logger.getLogger(Step.class.getName());

    /**
     * Tracks the status of a step.
     */
    //@JsonAutoDetect(getterVisibility = Visibility.NONE, fieldVisibility = Visibility.ANY)
    public enum Status {
        /**
         * The step has not been run.
         */
        INIT,
        /**
         * The step is currently running. It may or may not be in a wait condition.
         */
        WORKING,
        /**
         * The step has finished and it will not be run again.
         */
        DONE,
        /**
         * The step was aborted.
         */
        ABORTED
    }

    private IWork work;
    private final boolean ignoreFailure;
    private final String description;
    private final Duration timeout;

    private Status status = Status.INIT;
    private long startTimestamp = -1;
    private long endTimestamp = -1;
    private WorkOutput output;
    private List<Long> childCmds = Lists.newArrayList();
    private List<TypedDbBaseId> contextEntities = Lists.newArrayList();
    private boolean timedOut = false;

    //@JsonCreator
    private Step(IWork work,
                     boolean ignoreFailure,
                     String description,
                     long timeout) {
        this.work = work;
        this.ignoreFailure = ignoreFailure;
        this.description = description;
        this.timeout = new Duration(timeout);
    }

    public Status getStatus() {
        return status;
    }

    public IWork getWork() {
        return work;
    }

    /**
     * Used by test code to update the IWork in a step.
     */

    public void updateWork(IWork work) {
        this.work = work;
    }

    public boolean ignoreFailure() {
        return ignoreFailure;
    }

    public String getDescription() {
        return description;
    }

    public long getTimeout() {
        return timeout.getMillis();
    }

    public long getStartTimestamp() {
        return startTimestamp;
    }

    public long getEndTimestamp() {
        return endTimestamp;
    }

    /**
     * Get the output of the step's work.
     *
     * @return the output, may be null when status is {@link Status#INIT}.
     */
    public WorkOutput getOutput() {
        if (this.status == Status.ABORTED) {
            return new AbortedWorkOutput(output, timedOut ? getTimeout() : null);
        } else {
            return output;
        }
    }

    
    public List<Long> getChildCmds() {
        return Collections.unmodifiableList(childCmds);
    }

    public void removeChild(long childId) {
        childCmds.remove(childId);
    }

    
    List<TypedDbBaseId> getContext() {
        return contextEntities;
    }

    /**
     * Do work either on the actual work or on the WorkOutput if in wait, and
     * store the output. Unexpected exceptions are handled within.
     *
     * @param ctx the context
     */
    public void doWork(WorkContext ctx) {
        Preconditions.checkState(status != Status.DONE);
        status = Status.WORKING;
        if (startTimestamp == -1) {
            startTimestamp = DateTimeUtils.currentTimeMillis();
        }
        ctx.setListener(createListener(childCmds, contextEntities));
        try {
            WorkOutput output;
            if (getOutput() == null) {
                String desc = getWork().getDescription(ctx);
                LOG.info("Executing command {} work: {}",
                        desc != null ? I18n.t(desc) : getWork().getClass());
                output = getWork().doWork(ctx);
            } else {
                Preconditions.checkArgument(inWait());
                output = getOutput().update(ctx);
            }
            Preconditions.checkState(output != null,
                    "WorkOutput cannot be null");
            this.output = output;
            timeoutIfNecessary(ctx);
        } catch (RuntimeException e) {
            LOG.warn("Command " + String.valueOf(ctx.getCommandId()) +
                    " Unexpected exception during doWork", e);
            this.output = new ExceptionWorkOutput(e);
            throw e;
        } finally {
            if (output != null && !output.inWait()) {
                done(ctx);
            }
            ctx.setListener(null);
        }
    }

    private void timeoutIfNecessary(WorkContext ctx) {
        if (timeout.getMillis() == 0) {
            return;
        }
        if (!output.inWait()) {
            // If the work's done anyway, we let it go.
            return;
        }
        Duration taken = new Duration(startTimestamp,
                DateTimeUtils.currentTimeMillis());
        if (taken.isLongerThan(timeout)) {
            timedOut = true;
            LOG.error("Command {} Timeout for {}",
                    getWork().getDescription(ctx));
            abort(ctx);
        }
    }

    /**
     * Perform housekeeping work when this step has been completed, success or
     * not.
     */
    private void done(WorkContext ctx) {
        Preconditions.checkState(status == Status.WORKING
                || status == Status.ABORTED);
        if (status == Status.WORKING) {
            status = Status.DONE;
        }
        try {
            getWork().onFinish(getOutput(), ctx);
        } catch (RuntimeException e) {
            LOG.warn("Command " + String.valueOf(ctx.getCommandId()) +
                            " Unexpected exception during onFinish()",
                    e);
        } finally {
            endTimestamp = System.currentTimeMillis();
        }
    }

    private boolean abort(WorkContext ctx) {
        Preconditions.checkState(status != Status.INIT);
        if (status == Status.DONE) {
            return false;
        }
        try {
            getOutput().onAbort(ctx);
        } catch (RuntimeException e) {
            LOG.warn("Command " + String.valueOf(ctx.getCommandId()) +
                    " Unexpected exception during onAbort()", e);
        } finally {
            status = Status.ABORTED;
            done(ctx);
        }
        return false;
    }

    public ProgressSummary getProgressSummary(WorkContext ctx) {
        ProgressSummary prog = new ProgressSummary();

        if (startTimestamp >= 0) {
            prog.setStartFromInstant(new Instant(startTimestamp));
        }
        if (endTimestamp >= 0) {
            prog.setEndFromInstant(new Instant(endTimestamp));
        }

        switch (status) {
            case INIT:
                prog.setState(State.NOT_RUN);
                break;
            case WORKING:
                prog.setState(State.RUNNING);
                break;
            case DONE:
                if (getOutput().getType() == WorkOutputType.SUCCESS) {
                    prog.setState(State.SUCCEEDED);
                    break;
                }
                // Let failure fall through to be same as aborted.
            case ABORTED:
                prog.setState(State.FAILED);
                break;
        }
        String desc = description != null ? description : getWork().getDescription(ctx);
        if (desc != null) {
            prog.setDescription(I18n.t(desc));
        }
        WorkOutput output = getOutput();
        if (output != null) {
            String msg = output.getMessage();
            if (msg != null) {
                prog.setOutcome(I18n.t(msg));
            }
        }
        prog.setIgnoreError(ignoreFailure);
        ServiceHandlerRegistry shr = ctx.getServiceDataProvider().getServiceHandlerRegistry();
        OperationsManager opsMgr = ctx.getServiceDataProvider().getOperationsManager();
        for (Long childCmdId : childCmds) {
            DbCommand childCmd = ctx.getCmfEM().findCommand(childCmdId);
            if (childCmd != null) { // Possibly deleted in the mean time.
                prog.addCommand(childCmd, shr, opsMgr,
                        ctx.getServiceDataProvider().getCurrentUserManager(),
                        ctx.getCmfEM());
            }
        }
        for (TypedDbBaseId ctxEntityId : contextEntities) {
            TypedDbBase contextEntity = ctx.getCmfEM().findEntityById(
                    ctxEntityId.getType().getEntityClass(), ctxEntityId.getId());
            if (contextEntity != null) { // Possibly deleted in the mean time.
                prog.addContext(contextEntity);
            }
        }
        if (work instanceof CompositeWork) {
            CompositeWork comp = (CompositeWork) work;
            prog.setParallel(comp.isParallel());
            prog.setChildren(comp.getProgressSummaries(ctx));
        }
        if (work instanceof RemoteIWork) {
            RemoteIWork remote = (RemoteIWork) work;
            if (remote != null && remote.getRemoteCmdId() != null && remote.getRemoteCmdId() != -1L) {
                prog.setRemoteCommand(new RemoteCommand(remote.getRemoteCmdId(), remote.getPeerName()));
            }
        }
        return prog;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(status, work, output, ignoreFailure);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Step that = (Step) obj;
        return Objects.equal(this.status, that.status)
                && Objects.equal(this.work, that.work)
                && Objects.equal(this.output, that.output)
                && Objects.equal(this.ignoreFailure, that.ignoreFailure);
    }

    /**
     * Construct a step of a work flow out of a piece of work.
     *
     * @param work the work to be done
     * @param desc custom description to use, overriding that of {@link IWork}
     * @param ignoreFailure whether to treat failure as if result was success
     * @param timeout the timeout in milliseconds, starting from when
     *          {@link IWork#doWork(WorkContext)} is executed. Value of 0 is
     *          equivalent to no timeout. Negative values are not allowed.
     * @return the step
     */
    public static Step of(IWork work, String desc,
                             boolean ignoreFailure, long timeout) {
        Preconditions.checkNotNull(work);
        Preconditions.checkArgument(timeout >= 0);
        return new Step(work, ignoreFailure, desc, timeout);
    }

    /**
     * {@link #of(IWork, String, boolean, long))} without timeout.
     */
    public static Step of(IWork work, String desc,
                             boolean ignoreFailure) {
        return of(work, desc, ignoreFailure, DEFAULT_TIMEOUT);
    }

    /**
     * {@link #of(IWork, String, boolean)} without ignoring failure.
     */
    public static Step of(IWork work, String desc) {
        return of(work, desc, DEFAULT_IGNORE_FAILURE);
    }

    /**
     * {@link #of(IWork, String, boolean)} without a custom description.
     */
    public static Step of(IWork work, boolean ignoreFailure) {
        return of(work, null, ignoreFailure);
    }

    /**
     * {@link #of(IWork, String, boolean)} without ignoring failure and custom
     * description.
     */
    public static Step of(IWork work) {
        return of(work, DEFAULT_IGNORE_FAILURE);
    }

    @Override
    public WorkOutputType getType() {
        Preconditions.checkState(getOutput() != null);
        Preconditions.checkState(!inWait());
        if (this.ignoreFailure()) {
            return WorkOutputType.SUCCESS;
        } else {
            return output.getType();
        }
    }

    @Override
    public String getMessage() {
        return getOutput().getMessage();
    }

    @Override
    public boolean inWait() {
        return getOutput() != null && getOutput().inWait();
    }


    @Override
    public WorkOutput update(WorkContext ctx) {
        doWork(ctx);
        return this;
    }

    @Override
    public boolean onAbort(WorkContext ctx) {
        return abort(ctx);
    }

    /**
     * Create a new copy of this instance that can be retried. It next time it
     * will run it should start from last failing step.
     *
     * The implementation should not change the instance. Any changes required for
     * retry should be done to a copy.
     *
     *
     * @param ctx
     * @param dryRun to indicate that this is a dry run. When <code>true</code>
     *          any changes to the command should be avoided.
     * @return An instance of Step that can be retried.
     * @throws IllegalStateException If the IWork instance is not in
     *           failure/abort state or it does not support retry.
     */
    public Step retry(WorkContext ctx, boolean dryRun) {
        // Need to create a new listener
        WorkOutput workOutput = getOutput();
        Preconditions.checkState(!workOutput.inWait());
        Preconditions.checkState(ignoreFailure() || workOutput.getType() != WorkOutputType.SUCCESS);
        List<Long> childCmds = Lists.newArrayList(this.childCmds);
        List<TypedDbBaseId> contextEntities = Lists.newArrayList(this.contextEntities);
        ctx.setListener(createListener(childCmds, contextEntities));
        try {
            Step result = of(getWork().retry(ctx, dryRun), description,
                    ignoreFailure,
                    timeout.getMillis());
            result.childCmds = childCmds;
            result.contextEntities = contextEntities;
            return result;
        } finally {
            ctx.setListener(null);
        }
    }

    /**
     * @param childCmds
     * @param contextEntities
     * @return
     */
    private static Listener createListener(
            final List<Long> childCmds,
            final List<TypedDbBaseId> contextEntities) {
        return new Listener() {

            @Override
            public void contextAdded(TypedDbBase context) {
                Preconditions.checkNotNull(context);
                contextEntities.add(TypedDbBaseId.of(context));
            }

            @Override
            public boolean childCmdRemoved(DbCommand childCmd) {
                Preconditions.checkNotNull(childCmd);
                return childCmds.remove(childCmd.getId());
            }

            @Override
            public void childCmdAdded(DbCommand childCmd) {
                Preconditions.checkNotNull(childCmd);
                childCmds.add(childCmd.getId());
            }
        };
    }
}
