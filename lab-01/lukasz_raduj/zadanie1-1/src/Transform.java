import org.apache.bcel.Repository;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.*;

import java.io.IOException;

import static org.apache.bcel.Constants.INVOKESTATIC;
import static org.apache.bcel.generic.Type.*;


public class Transform {
    private static final String PRE_INVOKE_MESSAGE = "Method to be called: ";
    private static final String POST_INVOKE_MESSAGE = "Got result: ";

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

    public void transformAndSave(String inputClassName) throws IOException, ClassNotFoundException {
        JavaClass javaClass = Repository.lookupClass(inputClassName);
        classGen = new ClassGen(javaClass);
        constantPoolGen = classGen.getConstantPool();

        for (Method method : javaClass.getMethods()) {
            insertAroundInvocationNotifications(method);
        }

        saveClassFile();
    }

    private void saveClassFile() throws IOException {
        String path = Repository.lookupClassFile(classGen.getClassName()).getPath();
        classGen.getJavaClass().dump(path);
    }

    private void insertAroundInvocationNotifications(Method method) {
        MethodGen methodGen = new MethodGen(method, classGen.getClassName(), constantPoolGen);
        InstructionFactory instructionFactory = new InstructionFactory(classGen);

        InstructionList instructionList = methodGen.getInstructionList();

        for (InstructionHandle instructionHandle : instructionList.getInstructionHandles()) {
            Instruction instruction = instructionHandle.getInstruction();

            //not-invoke instructions are not interesting
            if (!(instruction instanceof InvokeInstruction)) {
                continue;
            }
            InvokeInstruction invokeInstruction = (InvokeInstruction) instruction;

            Type returnedType = invokeInstruction.getReturnType(constantPoolGen);
            //methods returning void are also not interesting
            if (returnedType == VOID) {
                continue;
            }

            InstructionList preInvocationInstructions = buildPreInvokeInstructions(instructionFactory, invokeInstruction);
            instructionList.insert(instructionHandle, preInvocationInstructions);

            InstructionList returnedValueDescription = buildPostInvokeInstructions(instructionFactory, returnedType);
            instructionList.append(instructionHandle, returnedValueDescription);
        }

        methodGen.setMaxStack();
        methodGen.setMaxLocals();
        classGen.replaceMethod(method, methodGen.getMethod());
    }

    private InstructionList buildPreInvokeInstructions(InstructionFactory instructionFactory,
                                                       InvokeInstruction invokeInstruction) {
        String methodDescription = buildPreInvokeMessage(invokeInstruction);
        return instructionFactory.createPrintln(methodDescription);
    }

    private String buildPreInvokeMessage(InvokeInstruction instruction) {
        String methodName = instruction.getMethodName(constantPoolGen);
        String signature = instruction.getSignature(constantPoolGen);

        return String.format("%s%s%s", PRE_INVOKE_MESSAGE, methodName, signature);
    }

    private InstructionList buildPostInvokeInstructions(InstructionFactory factory, Type returnType) {
        InstructionList instructionList = new InstructionList();

        int systemOutField = constantPoolGen.addFieldref("java.lang.System", "out", "Ljava/io/PrintStream;");
        int printMethod = constantPoolGen.addMethodref("java.io.PrintStream", "print", "(Ljava/lang/String;)V");

        instructionList.append(new GETSTATIC(systemOutField));
        instructionList.append(new PUSH(constantPoolGen, POST_INVOKE_MESSAGE));
        instructionList.append(new INVOKEVIRTUAL(printMethod));

        instructionList.append(InstructionFactory.createDup(returnType.getSize()));
        instructionList.append(new GETSTATIC(systemOutField));
        instructionList.append(swapTopItems(returnType)); //arguments must be higher on stack than calling function

        if (returnType instanceof BasicType) {
            Type[] returnedTypeInArray = new Type[]{expandReturnType(returnType)};
            InvokeInstruction valueOfCallInvoke =
                    factory.createInvoke("java.lang.String", "valueOf", STRING, returnedTypeInArray, INVOKESTATIC);
            instructionList.append(valueOfCallInvoke);
        } else {
            int toStringMethod = constantPoolGen.addMethodref("java.lang.Object", "toString", "()Ljava/lang/String;");
            instructionList.append(new INVOKEVIRTUAL(toStringMethod));
        }

        int println = constantPoolGen.addMethodref("java.io.PrintStream", "println", "(Ljava/lang/String;)V");
        instructionList.append(new INVOKEVIRTUAL(println));

        return instructionList;
    }

    private InstructionList swapTopItems(Type returnType) {
        InstructionList instructions = new InstructionList();
        if (isTwoWordType(returnType)) {
            instructions.append(InstructionFactory.DUP_X2);
            instructions.append(InstructionFactory.POP);
        } else {
            instructions.append(InstructionFactory.SWAP);
        }

        return instructions;
    }

    private boolean isTwoWordType(Type returnedType) {
        return returnedType == DOUBLE || returnedType == LONG;
    }

    private Type expandReturnType(Type returnType) {
        if (returnType instanceof BasicType) {
            if (returnType == BYTE || returnType == SHORT) {
                return INT;
            }
        }
        return returnType;
    }
}