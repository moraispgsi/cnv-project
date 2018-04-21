import BIT.highBIT.*;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;
import pt.ulisboa.tecnico.meic.cnv.loadbalancer.DynamoDB;
import pt.ulisboa.tecnico.meic.cnv.loadbalancer.Metric;


import java.io.File;
import java.util.*;


public class Instrumentation {

    // saves the result by thread
    public static HashMap<Long, Metrics> metricsHashMap = new HashMap<> ();

    public static void main (String argv[]) {
        if (argv.length < 1) {
            throw new RuntimeException ("Missing input directory.");
        }
        File root = new File (argv[0]);
        if (!root.isDirectory ()) {
            throw new RuntimeException ("Input file isn't a directory!");
        }
        List<String> excludeDirectories = new ArrayList<> ();
        excludeDirectories.add ("exceptions");
        excludeDirectories.add ("render");
        List<String> filesToInstrument = getFilePaths (root, excludeDirectories);


        for (String filepath : filesToInstrument) {
            ClassInfo classInfo = new ClassInfo (filepath);

            // loop through all the routines
            for (Enumeration e = classInfo.getRoutines ().elements (); e.hasMoreElements (); ) {
                Routine routine = (Routine) e.nextElement ();
                routine.addBefore ("Instrumentation", "methodCount", 1);
                for (Enumeration b = routine.getBasicBlocks ().elements (); b.hasMoreElements (); ) {
                    BasicBlock bb = (BasicBlock) b.nextElement ();
                    bb.addBefore ("Instrumentation", "basicBlockCount", bb.size ());
                }

                // number of creations
                InstructionArray instructions = routine.getInstructionArray ();
                for (Enumeration instrs = instructions.elements (); instrs.hasMoreElements (); ) {
                    Instruction instr = (Instruction) instrs.nextElement ();
                    int opcode = instr.getOpcode ();

                    if ((opcode == InstructionTable.NEW) || (opcode == InstructionTable.newarray)) {
                        instr.addBefore ("Instrumentation", "allocCount", new Integer (opcode));

                    }
                }
            }
            classInfo.addAfter ("Instrumentation", "classInstrumentation", classInfo.getClassName ());
            classInfo.write (filepath); // Replace original .class file with the one instrumented
        }
    }

    //private static HashMap<String, Integer> hm = new HashMap<> ();

    public static List<String> getFilePaths (File root, List<String> excludeDirectories) {
        List<String> filePaths = new ArrayList<> ();
        if (root == null) {
            return new ArrayList<> ();
        }
        File[] files = root.listFiles ();
        for (File file : files) {
            if (file.isFile ()) {
                if (file.getName ().endsWith (".class")) {
                    filePaths.add (file.getAbsolutePath ());
                    System.out.println ("File to instrument at: " + file.getAbsolutePath ());
                }
            } else if (!excludeDirectories.contains (file.getName ())) {
                filePaths.addAll (getFilePaths (file, new ArrayList<String> ()));
            }
        }
        return filePaths;
    }

    // try and catch necessary
    public static void methodCount (int increment) {
        long threadId = 0;
        Metrics metrics = null;
        try{
            threadId = Thread.currentThread ().getId ();
            metrics = metricsHashMap.get (threadId);
            metrics.methodInvocationCount++;
            metricsHashMap.put (threadId, metrics);
        }catch (NullPointerException e){
            metricsHashMap.put (threadId, new Metrics ());
            metrics = metricsHashMap.get (threadId);
            metrics.methodInvocationCount++;
            metricsHashMap.put (threadId, metrics);
        }
    }

    public static synchronized void basicBlockCount (int basicBlockSize) {
        long threadId = Thread.currentThread ().getId ();
        Metrics metrics = metricsHashMap.get (threadId);
        metrics.instructionsCount += basicBlockSize;
        metricsHashMap.put (threadId, metrics);
    }

    public static synchronized void allocCount (int type) {
        long threadId = Thread.currentThread ().getId ();
        Metrics metrics = metricsHashMap.get (threadId);
        switch (type) {
            case InstructionTable.NEW:
                metrics.objectAllocationCount++;
                break;
            case InstructionTable.newarray:
                metrics.arrayAllocationCount++;
                break;
        }
        metricsHashMap.put (threadId, metrics);
    }

    // Time is only for experimentation
    public static synchronized void classInstrumentation (String name) {
        // write to dynamo
        long threadId = Thread.currentThread ().getId ();
        Metrics metrics = metricsHashMap.get(threadId);
        DateTime d1 = metrics.init;
        DateTime d2 = new DateTime();
        Period period = new Period(d1, d2);
        PeriodFormatter formatter = new PeriodFormatterBuilder ()
                .appendHours().appendSuffix(" hour ", " hours ")
                .appendMinutes().appendSuffix(" minute ", " minutes ")
                .appendSeconds().appendSuffix(" second ", " seconds ")
                .appendMillis ().appendSuffix (" milliseconds", " milliseconds")
                .printZeroNever()
                .toFormatter();
        String elapsed = formatter.print(period);
        System.out.println ("Thread " + threadId + "recovered the following metrics: \n" + metrics);
        System.out.println ("Took: " + elapsed);
        // append metrics to the input request data
        Metric instanceMetrics = DynamoDB.getInstance ().getIncompleteMetricByThreadId (threadId);
        instanceMetrics.setCompleted (true);
        instanceMetrics.setInstructionsCount (metrics.instructionsCount);
        instanceMetrics.setMethodInvocationCount (metrics.methodInvocationCount);
        instanceMetrics.setArrayAllocationCount (metrics.arrayAllocationCount);
        instanceMetrics.setObjectAllocationCount (metrics.objectAllocationCount);
        // write on metrics on dynamo
        DynamoDB.getInstance ().writeValues (instanceMetrics);

        // remove metrics created by the thread
        metricsHashMap.remove (threadId);
    }

    public static class Metrics {

        public long instructionsCount = 0;
        public long methodInvocationCount = 0;
        public long objectAllocationCount = 0;
        public long arrayAllocationCount = 0;
        public DateTime init = new DateTime ();

        @Override public String toString () {
            return "Metrics: \n" +
                    "\tinstructionsCount:       " + instructionsCount + "\n" +
                    "\tmethodInvocationCount:   " + methodInvocationCount + "\n" +
                    "\tobjectCreationCount:     " + objectAllocationCount + "\n" +
                    "\tnewArrayCount:           " + arrayAllocationCount + "\n";
        }
    }

}
