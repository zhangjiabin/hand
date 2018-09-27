package rpc.max;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.ServerSocket;
import java.net.Socket;

public class RpcFramework {

	public static void export(final Object service, int port) throws Exception {
		if (null == service) {
			throw new IllegalArgumentException("service is null");
		}

		if (port < 0 || port > 65535) {
			throw new IllegalArgumentException("Invalid port:" + port);
		}
		System.out.println("Export service:" + service.getClass().getName() + " on port :" + port);

		ServerSocket serverSocket = new ServerSocket(port);
		for (;;) {
			// 匿名内部类只能引用外部的final变量
			final Socket socket = serverSocket.accept();

			new Thread(new Runnable() {

				public void run() {
					ObjectInputStream ois = null;
					try {
						ois = new ObjectInputStream(socket.getInputStream());

						try {
							// 拿到入参
							String methodName = ois.readUTF();
							Class<?>[] parameterTypes = (Class<?>[]) ois.readObject();
							Object[] arguments = (Object[]) ois.readObject();

							ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());

							try {
								// 反射调用
								Method method = service.getClass().getMethod(methodName, parameterTypes);
								Object result = method.invoke(service, arguments);
								oos.writeObject(result);
							} catch (Exception e) {
								oos.writeObject(e);
							} finally {
								oos.close();
							}

						} catch (ClassNotFoundException e) {
							e.printStackTrace();
						}

					} catch (IOException e) {
						e.printStackTrace();
					} finally {
						if (null != ois) {
							try {
								ois.close();
							} catch (IOException e) {
								e.printStackTrace();
							}
						}

						try {
							socket.close();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}
			}).start();
		}

	}

	/**
	 * 引用服务
	 * 
	 * @author <a href="mailto:shengyun@taobao.com">shengyun</a>
	 * @since 2016年9月1日
	 * @param interfaceName
	 * @param host
	 * @param port
	 * @return
	 * @throws Exception
	 */
	public static <T> T refer(final Class<T> interfaceClass, final String host, final int port) throws Exception {
		if (null == interfaceClass) {
			throw new Exception("interfaceClass can not be null");
		}
		if (!interfaceClass.isInterface()) {
			throw new Exception(interfaceClass.getName() + " is not interface");
		}
		if (null == host || host.length() == 0) {
			throw new Exception("host is null");
		}
		if (port < 0 || port > 65535) {
			throw new Exception("invalid port");
		}

		System.out.println("Get remote service " + interfaceClass.getName() + "from: " + host + " " + port);

		// 根据传入的接口生成动态代理类
		Object instance = Proxy.newProxyInstance(interfaceClass.getClassLoader(), new Class<?>[] { interfaceClass },
				new InvocationHandler() {

					public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
						Socket socket = new Socket(host, port);
						try {
							ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
							// 两边参数的读写必须匹配
							oos.writeUTF(method.getName());
							oos.writeObject(method.getParameterTypes());
							oos.writeObject(args);

							// 一般的动态代理代码中，我们会在invoke里调用真实对象。
							// 通过socket传递参数去执行，拿到结果
							// 这就是一个简单的rpc实现
							ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
							try {
								Object result = ois.readObject();
								if (result instanceof Throwable) {
									throw (Throwable) result;
								}

								return result;

							} finally {
								ois.close();
								oos.close();
							}
						} finally {
							socket.close();
						}
					}
				});

		return (T) instance;

	}
}
