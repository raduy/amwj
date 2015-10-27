import org.apache.bcel.Repository;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.*;

import java.io.IOException;

import static org.apache.bcel.Constants.INVOKESTATIC;
import static org.apache.bcel.generic.Type.*;


public class Transform {
    private static final String PRE_GET_FIELD = "Before getfield:";
    private ClassGen classGen;
    private ConstantPoolGen constantPoolGen;

    public static void main(String[] args) {
        try {
            if (args.length < 1) {
                printUsage();
                System.exit(1);
            }

            String fullClassName = args[0];
            if (!fullClassName.endsWith(".class")) {
                printUsage();
                System.exit(1);
            }

            String className = fullClassName.replace(".class", "");
            new Transform().transformAndSave(className);
        } catch (Exception ex) {
            System.out.printf("Cannot transform .class file! Reason: %s%n", ex);
        }
    }

    private static void printUsage() {
        System.out.println("Usage: Transform <.class file>");
    }

    private void transformAndSave(String className) throws IOException, ClassNotFoundException {
        JavaClass theClass = Repository.lookupClass(className);
        classGen = new ClassGen(theClass);
        constantPoolGen = classGen.getConstantPool();

        Method[] methods = theClass.getMethods();
        for (Method method : methods) {
            injectClassFieldDescription(method);
        }

        saveClassFile();
    }

    private void saveClassFile() throws IOException {
        String path = Repository.lookupClassFile(classGen.getClassName()).getPath();
        classGen.getJavaClass().dump(path);
    }

    private void injectClassFieldDescription(Method method) {
        MethodGen methodGenerator = new MethodGen(method, classGen.getClassName(), constantPoolGen);
        InstructionFactory factory = new InstructionFactory(classGen);
        InstructionList instructions = methodGenerator.getInstructionList();

        for (InstructionHandle instructionHandle : instructions.getInstructionHandles()) {
            Instruction instruction = instructionHandle.getInstruction();

            //not-get-field instructions are not interesting
            if (!(instruction instanceof GETFIELD)) {
                continue;
            }
            GETFIELD getFieldInstruction = (GETFIELD) instruction;

            Type fieldType = getFieldInstruction.getFieldType(constantPoolGen);

            if (!(fieldType instanceof BasicType)) {
                continue;
            }

            Type classType = getFieldInstruction.getReferenceType(constantPoolGen);
            String fieldName = getFieldInstruction.getFieldName(constantPoolGen);


            int systemOutField = constantPoolGen.addFieldref("java.lang.System", "out", "Ljava/io/PrintStream;");
            int printMethod = constantPoolGen.addMethodref("java.io.PrintStream", "print", "(Ljava/lang/String;)V");

            addInstructionsForPrintFieldDescription(instructions, instructionHandle, fieldType, classType, fieldName, systemOutField, printMethod);
            addInstructionsForPrintValue(instructions, instructionHandle, systemOutField, printMethod, fieldType, factory);

            if (!isNumericType(fieldType)) {
                continue;
            }

            addInstructionsForPrintHighValueWarning(instructions, instructionHandle, fieldType, systemOutField, printMethod);
        }

        methodGenerator.setMaxStack();
        methodGenerator.setMaxLocals();
        classGen.replaceMethod(method, methodGenerator.getMethod());
    }

    private void addInstructionsForPrintHighValueWarning(InstructionList instructions,
                                                         InstructionHandle instructionHandle,
                                                         Type fieldType,
                                                         int systemOutField,
                                                         int printMethod) {
        instructions.insert(instructionHandle, new DUP()); //copy reference
        instructions.insert(instructionHandle, instructionHandle.getInstruction());
        trimToInt(instructions, instructionHandle, fieldType);
        instructions.insert(instructionHandle, new PUSH(constantPoolGen, 30));
        instructions.insert(instructionHandle, new ISUB());


        InstructionHandle printWarning = instructions.insert(instructionHandle, new GETSTATIC(systemOutField));
        instructions.insert(instructionHandle, new PUSH(constantPoolGen, "    !the value is greater than 30!\n"));
        instructions.insert(instructionHandle, new INVOKEVIRTUAL(printMethod));

        InstructionHandle lastHandle = instructions.insert(instructionHandle, InstructionFactory.NOP);

        IFLE jumpHandle = new IFLE(lastHandle);
        instructions.insert(printWarning, jumpHandle);
    }

    private void trimToInt(InstructionList instructions, InstructionHandle instructionHandle, Type fieldType) {
        if (fieldType == LONG) {
            instructions.insert(instructionHandle, new L2I());
        } else if (fieldType == FLOAT) {
            instructions.insert(instructionHandle, new F2I());
        } else if (fieldType == DOUBLE) {
            instructions.insert(instructionHandle, new D2I());
        }
    }

    private boolean isNumericType(Type fieldType) {
        return fieldType == Type.INT || fieldType == Type.LONG ||
                fieldType == Type.FLOAT || fieldType == DOUBLE ||
                fieldType == Type.BYTE || fieldType == SHORT;
    }

    private void addInstructionsForPrintFieldDescription(InstructionList instructions,
                                                         InstructionHandle instructionHandle,
                                                         Type fieldType,
                                                         Type classType,
                                                         String fieldName,
                                                         int systemOutField,
                                                         int printMethod) {
        instructions.insert(instructionHandle, new GETSTATIC(systemOutField));
        String fieldDescription = buildFieldDescription(fieldType, classType, fieldName);
        instructions.insert(instructionHandle, new PUSH(constantPoolGen, fieldDescription));
        instructions.insert(instructionHandle, new INVOKEVIRTUAL(printMethod));
    }


    private InstructionList swapTopItems(Type returnType, InstructionList instructions, InstructionHandle instructionHandle) {
        if (isTwoWordType(returnType)) {
            instructions.insert(instructionHandle, InstructionFactory.DUP_X2);
            instructions.insert(instructionHandle, InstructionFactory.POP);
        } else {
            instructions.insert(instructionHandle, InstructionFactory.SWAP);
        }

        return instructions;
    }

    private boolean isTwoWordType(Type returnedType) {
        return returnedType == DOUBLE || returnedType == LONG;
    }

    private void addInstructionsForPrintValue(InstructionList instructions,
                                              InstructionHandle instructionHandle,
                                              int systemOutField,
                                              int printMethod,
                                              Type fieldType,
                                              InstructionFactory factory) {
//        print spaces
        instructions.insert(instructionHandle, new GETSTATIC(systemOutField));
        instructions.insert(instructionHandle, new PUSH(constantPoolGen, "    "));
        instructions.insert(instructionHandle, new INVOKEVIRTUAL(printMethod));

//        print value
        instructions.insert(instructionHandle, new DUP()); //copy reference
        instructions.insert(instructionHandle, instructionHandle.getInstruction());
        instructions.insert(instructionHandle, new GETSTATIC(systemOutField));

        swapTopItems(fieldType, instructions, instructionHandle);

        Type[] argTypes = {getReturnedTypeInArray(fieldType)};
        InvokeInstruction valueOfCall1 = factory.createInvoke("java.lang.String", "valueOf", STRING, argTypes, INVOKESTATIC);
        instructions.insert(instructionHandle, valueOfCall1);

        int printlnMethod = constantPoolGen.addMethodref("java.io.PrintStream", "println", "(Ljava/lang/String;)V");

        instructions.insert(instructionHandle, new INVOKEVIRTUAL(printlnMethod));
    }

    private String buildFieldDescription(Type fieldType, Type classType, String fieldName) {
        return String.format(
                "%s\n" +
                        "    %s\n" +
                        "    %s\n" +
                        "    %s\n", PRE_GET_FIELD, classType.toString(), fieldType.toString(), fieldName);
    }

    private Type getReturnedTypeInArray(Type returnType) {
        if (returnType instanceof BasicType) {
            if (returnType == BYTE || returnType == SHORT) {
                return INT;
            }
        }
        return returnType;
    }
}