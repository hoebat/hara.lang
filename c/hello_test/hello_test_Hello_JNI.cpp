#include "hello_test_Hello_JNI.h"
#include <iostream>

using std::cout;

JNIEXPORT void JNICALL Java_hello_1test_Hello_1JNI_sayHello
  (JNIEnv* env, jobject thisObject) {
    std::cout << "Hello from C++ !!" << std::endl;
}