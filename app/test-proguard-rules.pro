# Error Prone's compile-time IncompatibleModifiers annotation references this JDK
# compiler enum. Android does not provide javax.lang.model; the annotation is not
# used at runtime. This exception applies only to the instrumentation APK.
-dontwarn javax.lang.model.element.Modifier
