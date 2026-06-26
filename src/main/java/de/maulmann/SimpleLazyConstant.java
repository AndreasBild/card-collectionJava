package de.maulmann;

import java.util.function.Supplier;

public class SimpleLazyConstant<T> {
    private final Supplier<T> supplier;
    private volatile T value;

    private SimpleLazyConstant(Supplier<T> supplier) {
        this.supplier = supplier;
    }

    public static <T> SimpleLazyConstant<T> of(Supplier<T> supplier) {
        return new SimpleLazyConstant<>(supplier);
    }

    public T get() {
        T result = value;
        if (result == null) {
            synchronized (this) {
                result = value;
                if (result == null) {
                    value = result = supplier.get();
                }
            }
        }
        return result;
    }
}
