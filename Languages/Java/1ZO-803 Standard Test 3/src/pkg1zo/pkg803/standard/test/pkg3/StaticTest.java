/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */


/**
 *
 * @author t2p
 */
public class StaticAccessibilityTest {

    int i1;
    static int i2;

    public static void callStatic() {

    }

    public void accessibiltyTest() {
        this.i1 = 0;
        i2 = 90; //this is allowed
    }

    public static void staticMethod() {
            System.out.println("I am staticMethod()");

    }

    public void methodAccessibilityTest() {
        staticMethod();
    }

    public static void main(String[] args) {
         StaticAccessibilityTest sa = new StaticAccessibilityTest();
       
    }

}
