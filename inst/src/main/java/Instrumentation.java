/* Instrumentation.java
 * Sample program using BIT -- counts the number of instructions executed.
 *
 * Copyright (c) 1997, The Regents of the University of Colorado. All
 * Rights Reserved.
 *
 * Permission to use and copy this software and its documentation for
 * NON-COMMERCIAL purposes and without fee is hereby granted provided
 * that this copyright notice appears in all copies. If you wish to use
 * or wish to have others use BIT for commercial purposes please contact,
 * Stephen V. O'Neil, Director, Office of Technology Transfer at the
 * University of Colorado at Boulder (303) 492-5647.
 */

import BIT.highBIT.*;

import java.io.*;
import java.util.*;


public class Instrumentation {
    public static class Metrics {

    }


    // saves the result by thread
    private static HashMap<Integer, Metrics> metricsHashMap;


    private static long instructionsCount = 0;
    private static long basicBlockCount = 0;
    private static long methodCount = 0;
    private static long classCount = 0; // to replace by 'classCountStatic'

    private static long objectCreationsCount = 0;
    private static long newarraycount = 0;
    private static long anewarraycount = 0;
    private static long multianewarraycount = 0;

    // static class content - don't think it can be relevant
    private static long fieldloadcount = 0;
    private static long fieldstorecount = 0;

    // might be relevant
    private static long loadcount = 0;
    private static long storecount = 0;


    private static long dyn_method_count = 0;
    private static long dyn_bb_count = 0;
    private static long dyn_instr_count = 0;


    private static long stackInstructions = 0;
    private static long arithmeticInstructions = 0;
    private static long logicalInstructions = 0;
    private static long comparisonInstructions = 0;
    private static long conditionalInstructions = 0;
    private static long objectInstructions = 0;

    // static
    private static long methodCountStatic = 0;
    private static long basicBlockCountStatic = 0;
    private static long instructionsCountStatic = 0;

    // manter no final
    private static long classCountStatic = 0;

    //private static HashMap<String, Integer> hm = new HashMap<> ();


    public static void main(String argv[]) {
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
            classCountStatic++;
            //System.out.println ("classInfo.getClassName ()" + classInfo.getClassName ());
            //hm.put (classInfo.getClassName (), 0);
            // loop through all the routines
            for (Enumeration e = classInfo.getRoutines().elements(); e.hasMoreElements(); ) {
                Routine routine = (Routine) e.nextElement();
                routine.addBefore("Instrumentation", "methodCount", classInfo.getClassName ());
                methodCountStatic++;
                for (Enumeration b = routine.getBasicBlocks().elements(); b.hasMoreElements(); ) {
                    BasicBlock bb = (BasicBlock) b.nextElement();
                    bb.addBefore("Instrumentation", "basicBlockCount", bb.size());
                    basicBlockCountStatic++;
                    instructionsCountStatic +=bb.size ();
                }

                // number of creations
                InstructionArray instructions = routine.getInstructionArray();
                for (Enumeration instrs = instructions.elements(); instrs.hasMoreElements(); ) {
                    Instruction instr = (Instruction) instrs.nextElement();
                    int opcode=instr.getOpcode();

                    if ((opcode==InstructionTable.NEW) ||
                            (opcode==InstructionTable.newarray) ||
                            (opcode==InstructionTable.anewarray) ||
                            (opcode==InstructionTable.multianewarray)) {
                        instr.addBefore("Instrumentation", "allocCount", new Integer(opcode));
                    }
                    else if ((opcode == InstructionTable.STACK_INSTRUCTION) ||
                            (opcode == InstructionTable.ARITHMETIC_INSTRUCTION) ||
                            (opcode == InstructionTable.LOGICAL_INSTRUCTION) ||
                            (opcode == InstructionTable.COMPARISON_INSTRUCTION) ||
                            (opcode == InstructionTable.CONDITIONAL_INSTRUCTION) ||
                            (opcode == InstructionTable.OBJECT_INSTRUCTION)) {
                        instr.addBefore ("Instrumentation", "countOperations", opcode);
                    }

                    else if (opcode == InstructionTable.getfield)
                        instr.addBefore("Instrumentation", "LSFieldCount", new Integer(0));
                    else if (opcode == InstructionTable.putfield)
                        instr.addBefore("Instrumentation", "LSFieldCount", new Integer(1));
                    else {
                        short instr_type = InstructionTable.InstructionTypeTable[opcode];
                        if (instr_type == InstructionTable.LOAD_INSTRUCTION) {
                            instr.addBefore("Instrumentation", "LSCount", new Integer(0));
                        }
                        else if (instr_type == InstructionTable.STORE_INSTRUCTION) {
                            instr.addBefore("Instrumentation", "LSCount", new Integer(1));
                        }
                    }
                }
            }
            classInfo.addAfter("Instrumentation", "classInstrumentation", classInfo.getClassName());
            classInfo.addAfter ("Instrumentation", "clearValues", classInfo.getClassName ());
            classInfo.write(filepath); // Replace original .class file with the one instrumented
        }
        System.out.println("Static information summary:");
        System.out.println("Number of class files:  " + classCountStatic);
        System.out.println("Number of methods:      " + methodCountStatic);
        System.out.println("Number of basic blocks: " + basicBlockCountStatic);
        System.out.println("Number of instructions: " + instructionsCountStatic);

        if (classCountStatic == 0 || methodCountStatic == 0) {
            return;
        }

        float instr_per_bb = (float) instructionsCountStatic / (float) basicBlockCountStatic;
        float instr_per_method = (float) instructionsCountStatic / (float) methodCountStatic;
        float instr_per_class = (float) instructionsCountStatic / (float) classCountStatic;
        float bb_per_method = (float) basicBlockCountStatic / (float) methodCountStatic;
        float bb_per_class = (float) basicBlockCountStatic / (float) classCountStatic;
        float method_per_class = (float) methodCountStatic / (float) classCountStatic;

        System.out.println("Average number of instructions per basic block: " + instr_per_bb);
        System.out.println("Average number of instructions per method:      " + instr_per_method);
        System.out.println("Average number of instructions per class:       " + instr_per_class);
        System.out.println("Average number of basic blocks per method:      " + bb_per_method);
        System.out.println("Average number of basic blocks per class:       " + bb_per_class);
        System.out.println("Average number of methods per class:            " + method_per_class);
    }

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
            } else if (!excludeDirectories.contains (file.getName ())){
                filePaths.addAll (getFilePaths (file, new ArrayList<String> ()));
            }
        }
        return filePaths;
    }

    // deprecated
    public static synchronized void clearValues (String cn) {
        instructionsCount = 0;
        basicBlockCount = 0;
        methodCount = 0;
        classCount = 0; // to replace by 'classCountStatic'
        objectCreationsCount = 0;
        newarraycount = 0;
        anewarraycount = 0;
        multianewarraycount = 0;
        // static class content - don't think it can be relevant
        fieldloadcount = 0;
        fieldstorecount = 0;
        // might be relevant
        loadcount = 0;
        storecount = 0;
        dyn_method_count = 0;
        dyn_bb_count = 0;
        dyn_instr_count = 0;
        stackInstructions = 0;
        arithmeticInstructions = 0;
        logicalInstructions = 0;
        comparisonInstructions = 0;
        conditionalInstructions = 0;
        objectInstructions = 0;
        // static
        methodCountStatic = 0;
        basicBlockCountStatic = 0;
        instructionsCountStatic = 0;
        // manter no final
        classCountStatic = 0;
    }

    public static synchronized void countOperations (int type) {
        switch(type) {
            case InstructionTable.STACK_INSTRUCTION:
                stackInstructions++;
                break;
            case InstructionTable.ARITHMETIC_INSTRUCTION:
                arithmeticInstructions++;
                break;
            case InstructionTable.LOGICAL_INSTRUCTION:
                logicalInstructions++;
                break;
            case InstructionTable.COMPARISON_INSTRUCTION:
                comparisonInstructions++;
                break;
            case InstructionTable.CONDITIONAL_INSTRUCTION:
                conditionalInstructions++;
                break;
            case InstructionTable.OBJECT_INSTRUCTION:
                objectInstructions++;
                break;
        }
    }

    public static synchronized void methodCount (String increment) {
        //int val = hm.get (increment);
        //hm.put (increment, val +1);
        methodCount++;
        //System.out.println ("CLASS NAME: " + increment);    // to check what classes are called


        /*
        System.out.println("Dynamic information summary:");
        System.out.println("Number of methods:      " + dyn_method_count);
        System.out.println("Number of basic blocks: " + dyn_bb_count);
        System.out.println("Number of instructions: " + dyn_instr_count);

        if (dyn_method_count == 0) {
            return;
        }

        float instr_per_bb = (float) dyn_instr_count / (float) dyn_bb_count;
        float instr_per_method = (float) dyn_instr_count / (float) dyn_method_count;
        float bb_per_method = (float) dyn_bb_count / (float) dyn_method_count;

        System.out.println("Average number of instructions per basic block: " + instr_per_bb);
        System.out.println("Average number of instructions per method:      " + instr_per_method);
        System.out.println("Average number of basic blocks per method:      " + bb_per_method);
        */
    }

    public static synchronized void basicBlockCount (int basicBlockSize) {
        basicBlockCount++;
        instructionsCount += basicBlockSize;
    }

    public static synchronized void allocCount(int type)
    {
        switch(type) {
            case InstructionTable.NEW:
                objectCreationsCount++;
                break;
            case InstructionTable.newarray:
                newarraycount++;
                break;
            case InstructionTable.anewarray:
                anewarraycount++;
                break;
            case InstructionTable.multianewarray:
                multianewarraycount++;
                break;
        }
    }

    public static synchronized void LSFieldCount(int type)
    {
        if (type == 0)
            fieldloadcount++;
        else
            fieldstorecount++;
    }

    public static synchronized void LSCount(int type)
    {
        if (type == 0)
            loadcount++;
        else
            storecount++;
    }

    // this call seems to only happen at the end of the call Maze.main
    public static synchronized void classInstrumentation (String name) {
        classCount++;
        System.out.println ("On class: " + name + ", were executed " + instructionsCount + " instructions in "
                + basicBlockCount + " basic blocks on " + methodCount + " methods." );
        //System.out.println (hm.toString ());
        System.out.println("Allocations summary:");
        System.out.println("new:            " + objectCreationsCount);
        System.out.println("newarray:       " + newarraycount);
        System.out.println("anewarray:      " + anewarraycount);
        System.out.println("multianewarray: " + multianewarraycount);

        System.out.println("Load Store Summary:");
        System.out.println("Field load:    " + fieldloadcount);
        System.out.println("Field store:   " + fieldstorecount);
        System.out.println("Regular load:  " + loadcount);
        System.out.println("Regular store: " + storecount);

        System.out.println("MORE:");
        System.out.println("stackInstructions:    " + stackInstructions);
        System.out.println("arithmeticInstructions:   " + arithmeticInstructions);
        System.out.println("logicalInstructions:  " + logicalInstructions);
        System.out.println("comparisonInstructions: " + comparisonInstructions);
        System.out.println("conditionalInstructions: " + conditionalInstructions);
        System.out.println("objectInstructions: " + objectInstructions);
    }

}
