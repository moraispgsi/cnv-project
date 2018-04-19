package pt.ulisboa.tecnico.meic.cnv.instrument;/* pt.ulisboa.tecnico.meic.cnv.instrument.Instrumentation.java
 * Sample program using pt.ulisboa.tecnico.meic.cnv.instrument.BIT -- counts the number of instructions executed.
 *
 * Copyright (c) 1997, The Regents of the University of Colorado. All
 * Rights Reserved.
 *
 * Permission to use and copy this software and its documentation for
 * NON-COMMERCIAL purposes and without fee is hereby granted provided
 * that this copyright notice appears in all copies. If you wish to use
 * or wish to have others use pt.ulisboa.tecnico.meic.cnv.instrument.BIT for commercial purposes please contact,
 * Stephen V. O'Neil, Director, Office of Technology Transfer at the
 * University of Colorado at Boulder (303) 492-5647.
 */

import pt.ulisboa.tecnico.meic.cnv.instrument.BIT.highBIT.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;


public class Instrumentation {

    // saves the result by thread
    public static HashMap<Long, Metrics> metricsHashMap;
    
    private static long instructionsCount = 0;
    private static long methodCount = 0;
    private static long objectCreationsCount = 0;
    private static long newarraycount = 0;


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
                routine.addBefore ("pt.ulisboa.tecnico.meic.cnv.instrument.Instrumentation", "methodCount", classInfo.getClassName ());
                for (Enumeration b = routine.getBasicBlocks ().elements (); b.hasMoreElements (); ) {
                    BasicBlock bb = (BasicBlock) b.nextElement ();
                    bb.addBefore ("pt.ulisboa.tecnico.meic.cnv.instrument.Instrumentation", "basicBlockCount", bb.size ());
                }

                // todo: should work, but creating multiple requests...
                // if the class name == Main and method is main create the
                if (routine.getClassName ().equals ("pt/ulisboa/tecnico/meic/cnv/mazerunner/maze/Main") &&
                        routine.getMethodName ().equals ("main")) {
                    //routine.addBefore ("pt.ulisboa.tecnico.meic.cnv.instrument.Instrumentation", "initStructure", 0);
                    //routine.addAfter ("pt.ulisboa.tecnico.meic.cnv.instrument.Instrumentation", "clearStructure", 0);
                }

                // number of creations
                InstructionArray instructions = routine.getInstructionArray ();
                for (Enumeration instrs = instructions.elements (); instrs.hasMoreElements (); ) {
                    Instruction instr = (Instruction) instrs.nextElement ();
                    int opcode = instr.getOpcode ();

                    if ((opcode == InstructionTable.NEW) || (opcode == InstructionTable.newarray)) {
                        instr.addBefore ("pt.ulisboa.tecnico.meic.cnv.instrument.Instrumentation", "allocCount", new Integer (opcode));

                    }
                }
            }
            classInfo.addAfter ("pt.ulisboa.tecnico.meic.cnv.instrument.Instrumentation", "classInstrumentation", classInfo.getClassName ());
            classInfo.addAfter ("pt.ulisboa.tecnico.meic.cnv.instrument.Instrumentation", "clearValues", classInfo.getClassName ());
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

    // test
    public static synchronized void initStructure (int i) {
        long threadId = Thread.currentThread ().getId ();
        metricsHashMap.put (threadId, new Metrics ());
        System.out.println ("ON INIT: im thread: " + threadId);
    }

    //todo at the end of the solving erase the entrance on the hashmap
    public static synchronized void clearStructure (int i) {
        long threadId = Thread.currentThread ().getId ();
        metricsHashMap.remove (threadId);
        System.out.println ("ON END: im thread: " + threadId);
    }

    // deprecated
    public static synchronized void clearValues (String cn) {
        instructionsCount = 0;
        methodCount = 0;
        objectCreationsCount = 0;
        newarraycount = 0;
    }

    public static synchronized void methodCount (String increment) {
        long threadId = Thread.currentThread ().getId ();
        Metrics metrics = metricsHashMap.get (threadId);
        metrics.methodInvocationCount++;
        metricsHashMap.put (threadId, metrics);
        //int val = hm.get (increment);
        //hm.put (increment, val +1);
        methodCount++;
    }

    public static synchronized void basicBlockCount (int basicBlockSize) {
        instructionsCount += basicBlockSize;
    }

    public static synchronized void allocCount (int type) {
        switch (type) {
            case InstructionTable.NEW:
                objectCreationsCount++;
                break;
            case InstructionTable.newarray:
                newarraycount++;
                break;
        }
    }

    // this call seems to only happen at the end of the call Maze.main
    public static synchronized void classInstrumentation (String name) {
        System.out.println (
                "On class: " + name + ", were executed " + instructionsCount + " instructions in " + methodCount + " methods.");
        //System.out.println (hm.toString ());
        System.out.println ("Allocations summary:");
        System.out.println ("new:            " + objectCreationsCount);
        System.out.println ("newarray:       " + newarraycount);
    }


    public static class Metrics {

        public long instructionsCount = 0;
        public long methodInvocationCount = 0;
        public long objectCreationCount = 0;
        public long newArrayCount = 0;

    }

    // deprecated
    // todo: validations
    /*public static Metrics getMetricsByThreadId (long threadId) {
        return metricsHashMap.get (threadId);
    }

    public static void initMetricsByThreadId (long threadId) {
        metricsHashMap.put (threadId, new Metrics ());
    }
    */

}
