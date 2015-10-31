import org.apache.bcel.Repository;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.*;

import java.io.IOException;


public class Transform {
    private JavaClass clazz;
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

    public void transformAndSave(String className) throws IOException, ClassNotFoundException {
        clazz = Repository.lookupClass(className);
        classGen = new ClassGen(clazz);
        constantPoolGen = classGen.getConstantPool();

        Method[] methods = clazz.getMethods();
        for (Method method : methods) {
            addStatsAspect(method);
        }

        String classFilePath = Repository.lookupClassFile(classGen.getClassName()).getPath();
        classGen.getJavaClass().dump(classFilePath);
    }

    private void addStatsAspect(Method method) {
        String methodName = method.getName();
        if (isConstructor(methodName) || isStaticInitializer(methodName)) {
            return;
        }

        MethodGen methodGenerator = new MethodGen(method, classGen.getClassName(), constantPoolGen);
        InstructionFactory factory = new InstructionFactory(classGen);
        InstructionList instructionList = methodGenerator.getInstructionList();

        if (isMainMethod(method)) {
            registerShutDownHook(instructionList);
        }

        for (InstructionHandle instructionHandle : instructionList.getInstructionHandles()) {
            if (isInstructionFromExternalMethod(instructionHandle.getInstruction())) {
                continue;
            }

            if (methodName.startsWith("m")) {
                continue;
            }

            insertMethodUsageUpdate(instructionList, factory, instructionHandle);
        }

        methodGenerator.setMaxStack();
        methodGenerator.setMaxLocals();
        classGen.replaceMethod(method, methodGenerator.getMethod());
    }

    private boolean isMainMethod(Method method) {
        Type[] methodArgumentTypes = method.getArgumentTypes();
        return method.isPublic() && method.isStatic() && method.getReturnType().equals(Type.VOID)
                && "main".equals(method.getName())
                && methodArgumentTypes[0].equals(Type.getType(String[].class));
    }

    private boolean isStaticInitializer(String methodName) {
        return methodName.equals("<clinit>");
    }

    private boolean isConstructor(String methodName) {
        return methodName.equals("<init>");
    }

    private void registerShutDownHook(InstructionList instructionList) {
        int shutDownHookMethod = constantPoolGen.addMethodref("InstructionsUsageStatistics", "createShutdownHook", "()V");
        instructionList.insert(new INVOKESTATIC(shutDownHookMethod));
    }

    private boolean isInstructionFromExternalMethod(Instruction instruction) {
        if (!(instruction instanceof InvokeInstruction)) {
            return false;
        }

        InvokeInstruction invokeInstruction = (InvokeInstruction) instruction;
        return !isMethodDeclaredInThisClass(invokeInstruction);
    }

    private boolean isMethodDeclaredInThisClass(InvokeInstruction invokeInstruction) {
        for (Method method : clazz.getMethods()) {
            if (isInstructionFromMethod(invokeInstruction, method)) {
                return true;
            }
        }
        return false;
    }

    private boolean isInstructionFromMethod(InvokeInstruction invokeInstruction, Method method) {
        return invokeInstruction.getReferenceType(constantPoolGen).toString().equals(classGen.getClassName())
                && invokeInstruction.getMethodName(constantPoolGen).equals(method.getName())
                && invokeInstruction.getSignature(constantPoolGen).equals(method.getSignature());
    }

    private void insertMethodUsageUpdate(InstructionList instructionList,
                                         InstructionFactory factory,
                                         InstructionHandle instructionHandle) {

        instructionList.insert(instructionHandle, factory.createConstant(instructionHandle.getInstruction().getName()));

        int incrementUsageCounterMethod = constantPoolGen.addMethodref(
                InstructionsUsageStatistics.class.getCanonicalName(), "registerUse", "(Ljava/lang/String;)V");
        instructionList.insert(instructionHandle, new INVOKESTATIC(incrementUsageCounterMethod));
    }
}