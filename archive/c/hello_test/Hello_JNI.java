package hello_test;
 
public class Hello_JNI {
 
    static {
        System.loadLibrary("native");
    }
    
    public static void main(String[] args) {
        new Hello_JNI().sayHello();
    }
 
    // Declare a native method sayHello() that receives no arguments and returns void
    private native void sayHello();
}