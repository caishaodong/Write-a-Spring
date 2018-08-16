package com.dong.spring;

import com.dong.spring.annotation.CSDAutowired;
import com.dong.spring.annotation.CSDController;
import com.dong.spring.annotation.CSDRequestMapping;
import com.dong.spring.annotation.CSDService;
import javassist.*;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.LocalVariableAttribute;
import javassist.bytecode.MethodInfo;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.net.URL;
import java.util.*;

public class CSDDispatcherServlet extends HttpServlet {
    Properties properties = new Properties();
    List<String> classNameList = new ArrayList<String>();
    Map<String, Object> ioc = new HashMap<String, Object>();
    Map<String, Method> handlerMapping = new HashMap<String, Method>();
    Map<String, Object> controllerMap = new HashMap<String, Object>();


    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doDispatcher(req, resp);
    }


    @Override
    public void init(ServletConfig config) throws ServletException {
        //1、读取配置文件
        doLoadConfig(config.getInitParameter("contextConfigLocation"));

        //2、扫描包下面所有的类
        doScanner(properties.getProperty("scanPackage"));

        //3、实例化所有的类
        doInstance();

        //4、给类中的属性赋值（依赖注入）
        doDI();

        //5、初始化HandlerMapping（将url和method对应）
        initHandlerMapping();

    }

    private void doLoadConfig(String contextConfigLocation) {
        //把web.xml中的contextConfigLocation对应value值的文件加载到流里面
        InputStream resourceAsStream = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
        try {
            this.properties.load(resourceAsStream);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (null != resourceAsStream) {
                try {
                    resourceAsStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void doScanner(String packageName) {
        if (null == packageName || "".equals(packageName)) {
            return;
        }
        URL url = this.getClass().getClassLoader().getResource("/" + packageName.replaceAll("\\.", "/"));
        File dir = new File(url.getFile());
        for (File file : dir.listFiles()) {
            if (file.isDirectory()) {
                //递归读取包
                doScanner(packageName + "." + file.getName());
            } else {
                String className = packageName + "." + file.getName().replace(".class", "");
                classNameList.add(className);
            }
        }
    }

    private void doInstance() {
        if (classNameList.isEmpty()) {
            return;
        }
        for (String className : classNameList) {
            try {
                //反射出所有的类，把有注解的类实例化
                Class<?> clz = Class.forName(className);
                if (clz.isAnnotationPresent(CSDController.class)) {
                    //Controller注解直接把类首字母小写作为Key
                    ioc.put(toLowerFirstWord(clz.getSimpleName()), clz.newInstance());
                } else if (clz.isAnnotationPresent(CSDService.class)) {
                    //Service注解需要判断注解上有没有自定义key
                    CSDService annotation = clz.getAnnotation(CSDService.class);
                    String beanName = "".equals(annotation.value()) ? toLowerFirstWord(clz.getSimpleName()) : annotation.value();
                    ioc.put(beanName, clz.newInstance());
                } else {
                    continue;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void doDI() {
        if (ioc.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : fields) {
                if (!field.isAnnotationPresent(CSDAutowired.class)) {
                    continue;
                }
                CSDAutowired annotation = field.getAnnotation(CSDAutowired.class);
                String beanName = "".equals(annotation.value()) ? field.getType().getName() : annotation.value();

                try {
                    field.set(entry.getValue(), ioc.get(beanName));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void initHandlerMapping() {
        if (ioc.isEmpty()) {
            return;
        }
        try {
            for (Map.Entry<String, Object> entry : ioc.entrySet()) {
                Class<?> clz = entry.getValue().getClass();
                if (!clz.isAnnotationPresent(CSDController.class)) {
                    continue;
                }
                //获取类上的url
                String baseUrl = "/";
                if (clz.isAnnotationPresent(CSDRequestMapping.class)) {
                    CSDRequestMapping annotation = clz.getAnnotation(CSDRequestMapping.class);
                    baseUrl += annotation.value();
                }
                //获取方法上的url
                Method[] methods = clz.getMethods();
                for (Method method : methods) {
                    if (!method.isAnnotationPresent(CSDRequestMapping.class)) {
                        continue;
                    }
                    CSDRequestMapping annotation = method.getAnnotation(CSDRequestMapping.class);
                    String url = (baseUrl + "/" + annotation.value()).replaceAll("/+", "/");

                    if (handlerMapping.containsKey(url)) {
                        throw new RuntimeException("The url path" + url + " is repeated");
                    }
                    handlerMapping.put(url, method);
                    controllerMap.put(url, clz.newInstance());
                }


            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void doDispatcher(HttpServletRequest req, HttpServletResponse resp) {

        try {
            if (this.handlerMapping.isEmpty()) {
                resp.getWriter().write("HandlerMapping is Empty!");
                return;
            }
            String url = req.getRequestURI();
            url = ("/" + url).replaceAll("/+", "/");
            if (!this.handlerMapping.containsKey(url)) {
                resp.getWriter().write("404 Not Found");
                return;
            }

            Method method = handlerMapping.get(url);

            //获取请求参数的列表
            Map<String, String[]> parameterMap = req.getParameterMap();

            //获取请求方法参数的类型
            Class<?>[] parameterTypes = method.getParameterTypes();

            //获取方法的参数列表
            Class<?> declaringClass = method.getDeclaringClass();
            String methodName = method.getName();
            String[] methodParamsNames = getMethodParamsNames(declaringClass, methodName);

            //保存参数值的数组
            Object[] paramValues = new Object[methodParamsNames.length];

            for (int i = 0; i < methodParamsNames.length; i++) {
                for (Map.Entry<String, String[]> param : parameterMap.entrySet()) {
                    if (methodParamsNames[i].equals(param.getKey())) {
                        String[] value = param.getValue();
                        String paramSimpleName = parameterTypes[i].getSimpleName();

                        Object obj = null;
                        if ("Integer".equals(paramSimpleName)) {
                            obj = Integer.parseInt(value[0]);
                        } else if ("String".equals(paramSimpleName)) {
                            obj = value[0];
                        } else if ("BigDecimal".equals(paramSimpleName)) {
                            obj = new BigDecimal(value[0]);
                        }
                        paramValues[i] = obj;
                        break;
                    }
                }
                if (null == paramValues[i]) {
                    resp.getWriter().write("400 Parameter [" + methodParamsNames[i] + "] must be not null! ");
                    return;
                }
            }

            //利用反射机制来调用
            Object invoke = method.invoke(this.controllerMap.get(url), paramValues);//第一个参数是method所对应的实例 在ioc容器中
            String result = (String) invoke;
            resp.getWriter().write(result);
        } catch (Exception e) {
            try {
                resp.getWriter().write("500 System Error! " + e);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            e.printStackTrace();
        }

    }

    private String toLowerFirstWord(String name) {
        if (null == name) {
            return "";
        } else {
            char[] chars = name.toCharArray();
            chars[0] += 32;
            return String.valueOf(chars);
        }

    }


    /**
     * javassist获取方法上所有的参数名
     *
     * @param clazz            方法所属的类
     * @param targetMethodName 方法名
     * @return
     */
    public static String[] getMethodParamsNames(Class<?> clazz, String targetMethodName) {

        try {
            //
            ClassPool pool = ClassPool.getDefault();
            pool.insertClassPath(new ClassClassPath(clazz));
            CtClass cc = pool.get(clazz.getName());
            CtMethod cm = cc.getDeclaredMethod(targetMethodName);

            MethodInfo methodInfo = cm.getMethodInfo();
            CodeAttribute codeAttribute = methodInfo.getCodeAttribute();
            LocalVariableAttribute attr = (LocalVariableAttribute) codeAttribute.getAttribute(LocalVariableAttribute.tag);
            String[] variableNames = new String[cm.getParameterTypes().length];
            int staticIndex = Modifier.isStatic(cm.getModifiers()) ? 0 : 1;
            //FIXME 第一个参数会是this???????????
            for (int i = 0; i < variableNames.length; i++) {
                variableNames[i] = attr.variableName(i + staticIndex);
                if (variableNames[i].equals("this")) {
                    variableNames[i] = attr.variableName(i + (++staticIndex));
                }
            }
            return variableNames;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

}
