package commands;

import java.util.List;

public class Command {
    
    /*@Override
    public IWork constructWork(DbNull target, ClusterUsageReportGeneratorCommandArgs args) throws CmdNoopException {

        List<Step> steps = Lists.newArrayList();

        List<IWork> hiveIWorks = MetastoreSummaryIWork.createWorks(REPORT_ICEBERG, args);
        if (hiveIWorks.size() > 0) {
            steps.add(Step.of(ScatterIWork.of(hiveIWorks),
                    String.of(I18n.t("message.command.service.hive.collectMetaSummary.name"))));
        }

        // Step to generate the Usage reports based on the ask
        steps.add(Step.of(new ClusterUsageReportGeneratorIWork(args)));

        if (args.autoSend){
            // Creates the usage bundle zip and sets as the resultFile in the DBCommand
            // Upload the file from the DBCommand to the Cloudera Servers if not airgapped
            steps.add(Step.of(ClusterUsageReportBundleUploaderIWork.of(),true));
        }

        return SeqWork.of(steps);
    }*/

}
