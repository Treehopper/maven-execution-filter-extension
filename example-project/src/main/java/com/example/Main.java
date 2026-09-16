package com.example;

import java.util.*;

public class Main {

  public static void main(String[] args) {
    List<String> messages = new ArrayList<>();
    messages.add("Hello from the example project!");
    messages.forEach(System.out::println);
  }
}
