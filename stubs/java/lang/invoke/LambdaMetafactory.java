package java.lang.invoke;

/**
 * Stub لـ LambdaMetafactory — مطلوب فقط لترجمة اللامدا مع javac
 * (نفس فكرة core-lambda-stubs.jar الرسمية من Google).
 * لا يُضمَّن في الـ APK — d8 يولّد تعليمات invokedynamic الصحيحة.
 */
public final class LambdaMetafactory {
    private LambdaMetafactory() {}

    public static Object metafactory(
            MethodHandles.Lookup owner,
            String invokedName,
            MethodType invokedType,
            MethodType samMethodType,
            MethodHandle implMethod,
            MethodType instantiatedMethodType) {
        throw new UnsupportedOperationException("stub");
    }

    public static Object altMetafactory(
            MethodHandles.Lookup owner,
            String invokedName,
            MethodType invokedType,
            Object... rest) {
        throw new UnsupportedOperationException("stub");
    }
}
