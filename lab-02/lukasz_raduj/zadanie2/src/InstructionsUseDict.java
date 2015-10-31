import java.util.Map;
import java.util.TreeMap;


public class InstructionsUseDict {
    private static final Map<String, Long> dict = new TreeMap<String, Long>();

    public static void registerUse(String instruction) {
        if (dict.containsKey(instruction)) {
            Long instructionCounter = dict.get(instruction);
            dict.put(instruction, instructionCounter + 1L);
        } else {
            dict.put(instruction, 1L);
        }
    }

    public static void createShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                printInstructionsUsage();
            }
        });
    }

    private static void printInstructionsUsage() {
        for (Map.Entry<String, Long> entry : dict.entrySet()) {
            String instruction = entry.getKey();
            Long occurrences = entry.getValue();
            if (occurrences >= 5) {
                System.out.printf("%s    %s%n", instruction.toUpperCase(), occurrences.toString());
            }
        }
    }
}