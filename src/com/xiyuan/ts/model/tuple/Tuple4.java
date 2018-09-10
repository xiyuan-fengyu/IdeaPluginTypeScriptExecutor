package com.xiyuan.ts.model.tuple;

public class Tuple4 <T1, T2, T3, T4> {

    public T1 t1;
    public T2 t2;
    public T3 t3;
    public T4 t4;

    public Tuple4(T1 t1, T2 t2, T3 t3, T4 t4) {
        this.t1 = t1;
        this.t2 = t2;
        this.t3 = t3;
        this.t4 = t4;
    }

    @Override
    public String toString() {
        return this.getClass().getName() + "@" + this.hashCode() +
            " {\n" +
            "t1=" + t1 + "\n" +
            "t2=" + t2 + "\n" +
            "t3=" + t3 + "\n" +
            "t4=" + t4 + "\n" +
            "}";
    }

}