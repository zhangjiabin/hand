package com.max.controller;

import com.max.annotation.MyController;
import com.max.annotation.MyRequestMapping;

@MyController
@MyRequestMapping("/test")
public class TestController {

	@MyRequestMapping("/hello")
	public String Hello(String name) {
		return "hello: " + name;
	}
}
