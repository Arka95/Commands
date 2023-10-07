package work;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import icommands.CompositeWork;
import icommands.IWork;
import icommands.WorkOutput;
import java.util.Arrays;
import java.util.List;

/**
 * Run all of its {@link IWork} and wait on each of the resulting
 * {@link WorkOutput}.
 */
public class ScatteredWork extends AbstractWork implements CompositeWork {

  public static final boolean DEFAULT_BATCH_COMMIT = false;
  public static final boolean DEFAULT_RUN_WITH_CONFIG_HELPER_CACHE = false;

  private List<Step> steps;

  private final boolean batchCommit;
  private final boolean runWithConfigHelperCache;

  protected ScatteredWork( List<Step> steps,
                           boolean batchCommit,
                          boolean runWithConfigHelperCache) {
    this.steps = steps;
    this.batchCommit = batchCommit;
    this.runWithConfigHelperCache = runWithConfigHelperCache;
  }

  
  public List<IWork> getWorks() {
    List<IWork> works = Lists.newArrayList();
    for (Step step : getSteps()) {
      works.add(step.getWork());
    }
    return works;
  }

  
  public List<Step> getSteps() {
    return ImmutableList.copyOf(steps);
  }

  @Override
  public void updateSteps(List<Step> steps) {
    // Allow step re-writing to force consistent ordering of serialized steps.
    this.steps = steps;
  }

  @Override
  public WorkOutput doWork(WorkContext ctx) {
    CmfEntityManager em = ctx.getCmfEM();
    try {
      if (batchCommit) {
        em.flush();
        em.setFlushMode(FlushModeType.COMMIT);
      }
      if (runWithConfigHelperCache) {
        ctx.getServiceDataProvider().getConfigHelper().enableCache(em);
      }
      for (Step step : steps) {
        // Only call doWork() on step that is in INIT state as on retry some
        // steps will be reset to INIT.
        if (step.getStatus() == Step.Status.INIT) {
          step.doWork(ctx);
        }
      }
    } finally {
      if (batchCommit) {
        // reset flush mode
        em.setFlushMode(FlushModeType.AUTO);
      }
      if (runWithConfigHelperCache) {
        ctx.getServiceDataProvider().getConfigHelper().disableCache(em);
      }
    }
    return gather();
  }

  /**
   * Gather work outputs into a single workoutput.
   * Extensions can override this method to change the way that gathering of
   * work outputs occurs.
   *
   * @return a single "gathered" work output
   */
  protected WorkOutput gather() {
    return WorkOutputs.gather(steps);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(steps);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final ScatteredWork that = (ScatteredWork) obj;
    return Objects.equal(this.steps, that.steps);
  }


  /**
   * Construct a composite IWork that will scatter out to all the specified
   * {@link Step} objects, with the options of:
   *
   *  - batch commiting all of the nested doWork calls
   * using JPA's {javax.persistence.FlushModeType#COMMIT}. The flush mode will be reset
   * to {FlushModeType#AUTO} after each doWork iteration.
   *
   * @param steps steps
   * @param batchCommit if true, all of the IWorks will be batch committed, per above description.
   * @param runWithConfigHelperCache if true, will cache config objects generated across all of the IWorks
   *
   */
  public static ScatteredWork of(List<Step> steps,
                                 boolean batchCommit,
                                 boolean runWithConfigHelperCache) {
    Preconditions.checkNotNull(steps);
    Preconditions.checkArgument(!steps.isEmpty());
    return new ScatteredWork(steps, batchCommit, runWithConfigHelperCache);
  }

  /**
   * Construct a composite IWork that will scatter out to all the specified
   * IWork objects.
   *
   * @param works the works to run in parallel
   * @return the composite work
   */
  public static ScatteredWork of(List<IWork> works) {
    Preconditions.checkNotNull(works);
    List<Step> steps = Lists.newArrayList();
    for (IWork work : works) {
      steps.add(Step.of(work));
    }
    return ofSteps(steps);
  }

  /**
   * Same as {@link #of(List)}.
   */
  public static ScatteredWork of(IWork... works) {
    return of(Arrays.asList(works));
  }

  /**
   * Construct a composite IWork that will scatter out to all the specified
   * steps.
   *
   * @param steps the steps to run in parallel
   * @return the composite work
   */
  public static ScatteredWork ofSteps(List<Step> steps) {
    Preconditions.checkNotNull(steps);
    return new ScatteredWork(ImmutableList.copyOf(steps),
        DEFAULT_BATCH_COMMIT, DEFAULT_RUN_WITH_CONFIG_HELPER_CACHE);
  }

  @Override
  public void onFinish(WorkOutput output, WorkContext ctx) {
    // Already delegated as each step is done.
  }

  @Override
  public List<ProgressSummary> getProgressSummaries(WorkContext ctx) {
    List<ProgressSummary> summaries = Lists.newArrayList();
    for (Step step : steps) {
      summaries.add(step.getProgressSummary(ctx));
    }
    return summaries;
  }

  @Override
  public boolean isParallel() {
    return true;
  }

  @Override
  public ScatteredWork retry(WorkContext ctx, boolean dryRun) {
    return ScatteredWork.ofSteps(prepareStepsForRetry(ctx, dryRun));
  }

  protected List<Step> prepareStepsForRetry(WorkContext ctx, boolean dryRun) {
    List<Step> newSteps = Lists.newArrayList();
    for (Step step : steps) {
      if (canRetryStep(step)) {
        newSteps.add(step.retry(ctx, dryRun));
      } else {
        newSteps.add(step);
      }
    }
    return newSteps;
  }

  private boolean canRetryStep(Step step) {
    if (step.getStatus() == Step.Status.DONE
        && step.getOutput().getType() == WorkOutputType.FAILURE
        && step.ignoreFailure() == false) {
      // Retrying a step whose ignore flag is set may or may not make a sense.
      // For now deciding not to retry it. This is based on the rational that
      // if ignoreFailure step is producing an output that is causing other
      // steps to fail then perhaps we should not ignore that step to begin
      // with.
      return true;
    }
    if (step.getStatus() == Step.Status.ABORTED) {
      return true;
    }
    return false;
  }
}
