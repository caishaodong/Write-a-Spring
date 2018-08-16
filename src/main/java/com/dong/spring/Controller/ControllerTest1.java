package com.dong.spring.Controller;

import com.dong.spring.annotation.CSDController;
import com.dong.spring.annotation.CSDRequestMapping;

import java.math.BigDecimal;

@CSDController
@CSDRequestMapping("spring/test")
public class ControllerTest1 {

    @CSDRequestMapping("/addNum")
    public String addNum(Integer a, String b) {
        try {
            return "addNum success! a=" + a + ",b=" + b + ",a+b=" + (a + Integer.parseInt(b));
        } catch (NumberFormatException e) {
            e.printStackTrace();
            return "500 System Error! " + e.getMessage();
        }
    }

    @CSDRequestMapping("/subtractNum")
    public String subtractNum(Integer a, Integer b) {
        try {
            return "subtractNum success! a=" + a + ",b=" + b + ",a+b=" + (a - b);
        } catch (Exception e) {
            e.printStackTrace();
            return "500 System Error! " + e.getMessage();
        }
    }


    @CSDRequestMapping("/multiplyNum")
    public String multiplyNum(String a, String b) {
        try {
            return "multiplyNum success! a=" + a + ",b=" + b + ",a*b=" + (Integer.parseInt(a) * Integer.parseInt(b));
        } catch (NumberFormatException e) {
            e.printStackTrace();
            return "500 System Error! " + e.getMessage();
        }
    }


    @CSDRequestMapping("/divideNum")
    public String divideNum(BigDecimal a, BigDecimal b) {
        try {
            return "divideNum success! a =" + a.toString() + ",b=" + b.toString() + ",a/b=" + a.divide(b).setScale(2).toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "500 System Error! " + e.getMessage();
        }
    }


}
