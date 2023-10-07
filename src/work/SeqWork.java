package work;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import icommands.CompositeWork;
import icommands.IWork;
import icommands.WorkOutput;

import java.util.Arrays;
import java.util.List;

/**
 * Sequence of {@link IWork}. Each piece of work is defined as a
 * {@link Step}, and a step must finish before the next step will be
 * executed.
 *
 * {@link #doWork(WorkContext)} advances the sequence through all steps unless a
 * step's output is {@link WorkOutput#inWait()}. Otherwise the sequence will
 * complete when:
 * <ul>
 * <li>no more steps to run
 * <li>the most recently executed step returned {@link WorkOutputType#FAILURE}
 * and {@link Step#ignoreFailure()} is false.
 */
//@JsonAutoDetect(getterVisibility = Visibility.NONE, fieldVisibility = Visibility.ANY)
public class SeqWork extends AbstractWork implements CompositeWork,
    WorkOutput {

  private static final int NOT_RUN = -1;

  // Fields initialized at construction.
  protected List<Step> steps;
  private Step finallyStep;
  private Callback callback;

  // Index of the current step in wait or next step to be run. The value can be
  // 1. NOT_RUN
  // 2. 0 to steps.size() - 1 while on main steps
  // 3. steps.size() while on finally step, or finished if no finally step
  // 4. steps.size() + 1 when finished
  private int currStep = NOT_RUN;

  private SeqWork() {
  }

  // Use of creator method (JsonCreator/JsonProperty) conflicts with
  // JsonIdentityInfo http://jira.codehaus.org/browse/JACKSON-847
  protected SeqWork(List<Step> steps, Step finallyStep, Callback callback) {
    this.steps = steps;
    this.finallyStep = finallyStep;
    this.callback = callback;
  }

  /**
   * Retrieve the steps.
   */
  public List<Step> getSteps() {
    return steps;
  }

  @Override
  public void updateSteps(List<Step> steps) {
    // No test currently requires step re-writing for this class.
    throw new UnsupportedOperationException();
  }
  
  public Step getFinallyStep() {
    return finallyStep;
  }

  /**
   * Advances the flow until end state unless an output
   * {@link WorkOutput#inWait()} is encountered.
   *
   * @param ctx the context that the flow is executing within
   * @throws IllegalArgumentException if no ctx is provided
   */
  @Override
  public WorkOutput doWork(WorkContext ctx) {
    Preconditions.checkArgument(ctx != null);

    if (steps.isEmpty()) {
      return WorkOutputs.success("message.command.flow.work.seq.noSteps");
    } else if (currStep == NOT_RUN) {
      currStep = 0;
    }
    while (inWait()) {
      Step step;
      if (currStep < steps.size()) {
        step = steps.get(currStep);
      } else { // currStep == steps.size() with finally.
        step = finallyStep;
      }
      step.doWork(ctx);
      WorkOutput output = step.getOutput();
      if (output.inWait()) {
        break; // Halt so we return to same step.
      }
      switch (output.getType()) {
      case ABORTED:
      case FAILURE:
        if (!step.ignoreFailure() && step != finallyStep) {
          currStep = steps.size(); // Jump to finally.
          break;
        }
        // Fall through to success case.
      case SUCCESS:
        currStep++;
        break;
      default:
        throw new RuntimeException("Unexpected WorkOutput " + output.getType());
      }
    }

    return this;
  }

  @Override
  public void onFinish(WorkOutput output, WorkContext ctx) {
    // Already delegated as each step is done.
    if (callback != null) {
      callback.onFinish(output, ctx);
    }
  }

  @Override
  public boolean onAbort(WorkContext ctx) {
    getCurrentStep().onAbort(ctx);
    currStep = steps.size();
    return true;
  }

  /**
   * Get the final step for reporting purposes, i.e. getType() and getMessage().
   * If the current/last step succeeded and a finally step exists and it does
   * not ignore failure, then the finally step calls the shot. Otherwise it's
   * the current/last step as usual.
   */
  private Step getFinalStep() {
    Step last = getCurrentStep();
    WorkOutput lastOut = last.getOutput();
    if (!lastOut.inWait() && lastOut.getType() == WorkOutputType.SUCCESS
        && finallyStep != null && !finallyStep.ignoreFailure()) {
      return finallyStep;
    } else {
      return last;
    }
  }

  @Override
  public WorkOutputType getType() {
    return getFinalStep().getOutput().getType();
  }

  @Override
  public String getMessage() {
    return getFinalStep().getOutput().getMessage();
  }

  @Override
  public boolean inWait() {
    return currStep != (finallyStep == null ? steps.size() : steps.size() + 1);
  }

  @Override
  public WorkOutput update(WorkContext ctx) {
    return doWork(ctx);
  }

  // Get the last step that has been run with an output.
  
  Step getCurrentStep() {
    Preconditions.checkState(currStep != NOT_RUN);
    Step last = null;
    for (Step step : steps) {
      if (step.getOutput() == null) {
        break;
      } else {
        last = step;
      }
    }
    return last;
  }

  @Override
  public List<ProgressSummary> getProgressSummaries(WorkContext ctx) {
    List<ProgressSummary> summaries = Lists.newArrayList();
    for (Step step : steps) {
      summaries.add(step.getProgressSummary(ctx));
    }
    if (finallyStep != null) {
      summaries.add(finallyStep.getProgressSummary(ctx));
    }
    return summaries;
  }

  @Override
  public boolean isParallel() {
    return false;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final SeqWork that = (SeqWork) obj;
    return Objects.equal(this.currStep, that.currStep)
        && Objects.equal(this.steps, that.steps);
  }

  /**
   * Construct a flow out of the specified steps.
   */
  public static SeqWork of(Step... steps) {
    Preconditions.checkNotNull(steps);
    return of(Arrays.asList(steps));
  }

  public static SeqWork ofIWorkList(List<IWork> works) {
    return of(works.toArray(new IWork[0]));
  }

  /**
   * Construct a flow out of the specified IWorks. The IWorks are built into
   * a step through {@link Step#of(IWork)}.
   */
  public static SeqWork of(IWork... works) {
    Preconditions.checkNotNull(works);
    List<Step> steps = Lists.newArrayListWithCapacity(works.length);
    for (IWork work : works) {
      steps.add(Step.of(work));
    }
    return of(steps);
  }

  /**
   * Construct a flow out of the list of steps.
   */
  public static SeqWork of(List<Step> steps) {
    return of(steps, null);
  }

  /**
   * {@link #of(List, Step, Callback)} without callback.
   */
  public static SeqWork of(List<Step> steps, Step finallyStep) {
    return of(steps, finallyStep, null);
  }

  /**
   * Construct a flow out of the list of steps with a finally step that will be
   * run in the end regardless of the outcome of the flow, even in the case of
   * {@link #onAbort(WorkContext)}. SeqWork is only finished, i.e.
   * no longer in wait, when steps and the finally have both finished.
   *
   * @param steps the main flow of steps to run serially
   * @param finallyStep the step to always run in the end. Its output will be
   *          incorporated unless {@link Step#ignoreFailure()}. By design
   *          final step should be idempotent as it will rerun on retry.
   * @param callback the callback to be invoked for relevant events
   */
  public static SeqWork of(List<Step> steps, Step finallyStep, Callback callback) {
    Preconditions.checkNotNull(steps);
    for (Step step : steps) {
      Preconditions.checkNotNull(step);
    }
    return new SeqWork(steps, finallyStep, callback);
  }

  @Override
  public SeqWork retry(WorkContext ctx, boolean dryRun) {
    Integer failedStep = null;

    List<Step> newSteps = Lists.newArrayList();
    int i = 0;
    for (Step step : steps) {

      if (canRetryStep(step)) {
        Preconditions.checkState(failedStep == null,
            "Assertion failure, there should be only one failed/aborted step.");
        failedStep = i;
        newSteps.add(step.retry(ctx, dryRun));
      } else {
        newSteps.add(step);
      }
      ++i;
    }

    Step newFinally = null;
    // We should always rerun final step. By design final step has to be
    // idempotent.
    Step finallyStep = getFinallyStep();
    if (finallyStep != null) {
      newFinally = finallyStep.retry(ctx, dryRun);
    }
    SeqWork result = SeqWork.of(newSteps, newFinally, callback);
    result.currStep = failedStep == null ? NOT_RUN : failedStep;
    return result;
  }

  private boolean canRetryStep(Step step) {
    if (step.getStatus() == Step.Status.DONE
        && step.getOutput().getType() == WorkOutputType.FAILURE
        && step.ignoreFailure() == false) {
      return true;
    }
    if (step.getStatus() == Step.Status.ABORTED) {
      return true;
    }
    return false;
  }

  public Callback getCallback() {
    return callback;
  }

  public void setCallback(Callback callback) {
    this.callback = callback;
  }

  //@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
  //@JsonAutoDetect(getterVisibility = Visibility.NONE, setterVisibility = Visibility.NONE, fieldVisibility = Visibility.ANY)
  public interface Callback {
    void onFinish(WorkOutput output, WorkContext ctx);
  }
}
