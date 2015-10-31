
public class Test extends TestBase {
    private final int someInt = 90;
    private long someLong = 90L;
    private static String someString;
    private SomeClass someClass = new SomeClass();

    static {
        someString = "Init in static block";
    }

    {
        someLong++;
    }

    public void mMethod() {
        System.out.println(someLong);
        System.out.println("Name of this method starts with m");
    }

    public void printMe(int howManyTimes) {
        if (howManyTimes == 0) {
            return;
        }
        printMe(howManyTimes - 1);
    }

    public void callSomeExternalMethod() {
        System.out.println("external");
        someClass.duplicate("stirng");
        someClass.duplicate("stirng");
    }

    public static void main(String[] args) {
        Test test = new Test();

        test.printMe(13);

        String s = someString + " in main method";

        System.out.println("    " + s);

        System.out.println(test.someInt);

        test.mMethod();
        test.callSomeExternalMethod();

        SomeClass someClass = new SomeClass();
        someClass.duplicate("yolo");
        someClass.duplicate("yolo2");
        someClass.duplicate("yolo3");

        System.out.println(test.getBaseString());
    }
}