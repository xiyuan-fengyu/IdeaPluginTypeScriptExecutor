package com.xiyuan.ts.model.tuple;

public class Tuple3 <T1, T2, T3> {

    public T1 t1;
    public T2 t2;
    public T3 t3;

    public Tuple3(T1 t1, T2 t2, T3 t3) {
        this.t1 = t1;
        this.t2 = t2;
        this.t3 = t3;
    }

    @Override
    public String toString() {
        return this.getClass().getName() + "@" + this.hashCode() +
            " {\n" +
            "t1=" + t1 + "\n" +
            "t2=" + t2 + "\n" +
            "t3=" + t3 + "\n" +
            "}";
    }

}