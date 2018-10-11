package com.max.servlet;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.max.annotation.MyController;
import com.max.annotation.MyRequestMapping;

@SuppressWarnings("serial")
public class MyDispatcherServlet extends HttpServlet {

	private Properties properties = new Properties();
	private List<String> classNames = new ArrayList<>();
	private Map<String, Object> ioc = new HashMap<>();
	private Map<String, Method> handMapping = new HashMap<>();
	private Map<String, Object> controllerMapping = new HashMap<>();

	@Override
	public void init(ServletConfig config) throws ServletException {

		// 加载配置文件
		try {
			load(config);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// 初始化相关类
		doScanner(properties.getProperty("scanPackage"));

		// 实例化
		doInstance();

		// 初始化handMapping
		initHandMapping();

	}

	private void load(ServletConfig config) throws Exception {
		String location = config.getInitParameter("contextConfigLocation");
		InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(location);
		properties.load(inputStream);
	}

	private void doScanner(String scanPackage) {
		URL url = this.getClass().getClassLoader().getResource("/" + scanPackage.replace(".", "/"));
		File dir = new File(url.getFile());
		for (File file : dir.listFiles()) {
			if (file.isDirectory()) {
				// 递归读取包
				doScanner(scanPackage + "." + file.getName());
			} else {
				String className = scanPackage + "." + file.getName().replace(".class", "");
				classNames.add(className);
			}
		}
	}

	private void doInstance() {
		if (classNames == null || classNames.size() == 0)
			return;
		for (String className : classNames) {
			try {
				Class<?> clazz = Class.forName(className);
				if (clazz.isAnnotationPresent(MyController.class)) {
					ioc.put(toLowerFirstWord(clazz.getSimpleName()), clazz.newInstance());
				}
			} catch (ClassNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InstantiationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	private String toLowerFirstWord(String name) {
		char[] charArray = name.toCharArray();
		charArray[0] += 32;
		return String.valueOf(charArray);
	}

	private void initHandMapping() {
		if (classNames != null && classNames.size() != 0) {
			for (String className : classNames) {
				try {
					Class<?> clazz = Class.forName(className);
					String controllUrl = "";
					if (clazz.isAnnotationPresent(MyController.class)) {
						if (clazz.isAnnotationPresent(MyRequestMapping.class)) {
							MyRequestMapping myRequestMapping = clazz.getAnnotation(MyRequestMapping.class);
							controllUrl = myRequestMapping.value();
						}
						Method[] methods = clazz.getMethods();
						if (methods != null && methods.length != 0) {
							for (Method method : methods) {
								if (method.isAnnotationPresent(MyRequestMapping.class)) {
									String methodUrl = method.getAnnotation(MyRequestMapping.class).value();
									String url = (controllUrl + "/" + methodUrl).replaceAll("/+", "/");
									handMapping.put(url, method);
									controllerMapping.put(url, ioc.get(toLowerFirstWord(clazz.getSimpleName())));
								}
							}
						}
					}
				} catch (ClassNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

			}
		}

	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		// TODO Auto-generated method stub
		doPost(req, resp);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		// TODO Auto-generated method stub
		try {
			doDispatch(req, resp);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			resp.getWriter().write(e.getMessage());
		}
	}

	private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception {
		if (handMapping != null && !handMapping.isEmpty()) {
			String url = req.getRequestURI();
			String contextPath = req.getContextPath();
			url = url.replace(contextPath, "").replaceAll("/+", "/");
			if (!this.handMapping.containsKey(url)) {
				resp.getWriter().write("404 NOT FOUND!");
				return;
			} else {
				Method method = handMapping.get(url);
				Object instance = controllerMapping.get(url);
				// req.getp
				String retValue = method.invoke(instance, getMethodParams(method, req, resp)).toString();
				resp.getWriter().write(retValue);
			}
		}
	}

	private Object[] getMethodParams(Method method, HttpServletRequest req, HttpServletResponse resp) {
		// 获取方法的参数列表
		Class<?>[] parameterTypes = method.getParameterTypes();
		// 获取请求的参数
		Map<String, String[]> parameterMap = req.getParameterMap();
		// 保存参数值
		Object[] paramValues = new Object[parameterTypes.length];
		// 方法的参数列表
		for (int i = 0; i < parameterTypes.length; i++) {
			// 根据参数名称，做某些处理
			String requestParam = parameterTypes[i].getSimpleName();

			if (requestParam.equals("HttpServletRequest")) {
				// 参数类型已明确，这边强转类型
				paramValues[i] = req;
				continue;
			}
			if (requestParam.equals("HttpServletResponse")) {
				paramValues[i] = resp;
				continue;
			}
			if (requestParam.equals("String")) {
				for (Entry<String, String[]> param : parameterMap.entrySet()) {
					String value = Arrays.toString(param.getValue()).replaceAll("\\[|\\]", "").replaceAll(",\\s", ",");
					paramValues[i] = value;
				}
			}
		}
		return paramValues;
	}

}
