package com.compilerAPI.compilerAPI.API;

import com.compilerAPI.compilerAPI.API.FunctionRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.tools.*;
import java.io.*;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@RestController
public class CompilerAPI {

    @PostMapping("/execute")
    public ResponseEntity<?> executeFunction(@RequestBody FunctionRequest request) {
        try {
            String functionCode = request.getFunction();
            List<String> args = request.getArgs();

            String className = "DynamicClass";
            String sourceCode = generateSourceCode(className, functionCode);

            File tempFile = new File(className + ".java");
            try (FileWriter writer = new FileWriter(tempFile)) {
                writer.write(sourceCode);
            }

            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null);
            Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjects(tempFile);
            JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, null, null, compilationUnits);

            if (!task.call()) {
                StringBuilder errorMessages = new StringBuilder("Compile error:\n");
                for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                    errorMessages.append("Error in line: ").append(diagnostic.getLineNumber()).append(": ")
                            .append(diagnostic.getMessage(null)).append("\n");
                }
                return ResponseEntity.badRequest().body(errorMessages.toString());
            }

            URLClassLoader classLoader = URLClassLoader.newInstance(new URL[]{new File(".").toURI().toURL()});
            Class<?> dynamicClass = Class.forName(className, true, classLoader);

            Method[] methods = dynamicClass.getDeclaredMethods();
            if (methods.length == 0) {
                return ResponseEntity.badRequest().body("Function not find.");
            }
            Method method = methods[0];

            Object[] convertedArgs = convertArguments(method.getParameterTypes(), args);

            Object result = method.invoke(dynamicClass.getDeclaredConstructor().newInstance(), convertedArgs);

            tempFile.delete();

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Runtime error: " + e.getMessage());
        }
    }

    private String generateSourceCode(String className, String functionCode) {
        return """
                import java.util.*;
                public class %s {
                    %s
                }
                """.formatted(className, functionCode);
    }

    private Object[] convertArguments(Class<?>[] parameterTypes, List<String> args) throws IllegalArgumentException {
        if (parameterTypes.length != args.size()) {
            throw new IllegalArgumentException("The number of arguments does not match the function parameters.");
        }

        Object[] convertedArgs = new Object[args.size()];
        for (int i = 0; i < parameterTypes.length; i++) {
            String arg = args.get(i);
            Class<?> paramType = parameterTypes[i];

            if (paramType.isArray()) {
                convertedArgs[i] = parseArray(arg, paramType.getComponentType());
            } else if (List.class.isAssignableFrom(paramType)) {
                convertedArgs[i] = parseList(arg, Object.class);
            } else if (paramType == int.class) {
                convertedArgs[i] = Integer.parseInt(arg);
            } else if (paramType == double.class) {
                convertedArgs[i] = Double.parseDouble(arg);
            } else if (paramType == boolean.class) {
                convertedArgs[i] = Boolean.parseBoolean(arg);
            } else if (paramType == String.class) {
                convertedArgs[i] = arg;
            } else {
                throw new IllegalArgumentException("Unsupported type: " + paramType.getName());
            }
        }
        return convertedArgs;
    }

    private Object parseArray(String input, Class<?> componentType) {
        String[] elements = input.replace("[", "").replace("]", "").split(",");
        Object array = java.lang.reflect.Array.newInstance(componentType, elements.length);

        for (int i = 0; i < elements.length; i++) {
            String element = elements[i].trim();
            if (componentType == int.class) {
                java.lang.reflect.Array.set(array, i, Integer.parseInt(element));
            } else if (componentType == double.class) {
                java.lang.reflect.Array.set(array, i, Double.parseDouble(element));
            } else if (componentType == boolean.class) {
                java.lang.reflect.Array.set(array, i, Boolean.parseBoolean(element));
            } else if (componentType == String.class) {
                java.lang.reflect.Array.set(array, i, element);
            } else {
                throw new IllegalArgumentException("Unsupported array type: " + componentType.getName());
            }
        }
        return array;
    }

    private List<Object> parseList(String input, Class<?> componentType) {
        String[] elements = input.replace("[", "").replace("]", "").split(",");
        List<Object> list = new ArrayList<>();

        for (String element : elements) {
            list.add(element.trim());
        }
        return list;
    }

    private Object convertResult(Object result) {
        if (result == null) {
            return null;
        } else if (result.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(result);
            List<Object> list = new ArrayList<>();
            for (int i = 0; i < length; i++) {
                list.add(java.lang.reflect.Array.get(result, i));
            }
            return list;
        } else if (result instanceof Collection) {
            return result;
        } else {
            return result;
        }
    }
}
